package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.domain.ChainSyncState;
import com.voucher.repository.ChainSyncStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 컨트랙트 이벤트 4종을 폴링 방식으로 감지해 로깅한다.
 * - 마지막 처리 블록은 ChainSyncState에 저장 (스캐너명: "EVENT_SCANNER")
 * - 10초마다 (lastProcessedBlock + 1) ~ 현재 블록 구간을 스캔
 * - 예외 발생 시 lastProcessedBlock 갱신 X → 다음 주기에 재시도
 *
 * 검증/리액션 로직은 별도 서비스에서 처리 (이 클래스는 감지+로깅만).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventScannerService {

    private static final String SCANNER_NAME = "EVENT_SCANNER";

    // 이벤트 시그니처 keccak256 (topic[0])
    // 시그니처 문자열은 Solidity와 정확히 동일해야 함 (타입 이름, 공백 없음).
    private static final String SIG_VOUCHER_PROGRAM_CREATED =
            eventTopic("VoucherProgramCreated(uint16,address,string,uint256,uint256,uint16,string)");
    private static final String SIG_VOUCHER_MINTED =
            eventTopic("VoucherMinted(uint256,uint16,address,uint256,uint256,string)");
    private static final String SIG_MERCHANT_APPROVED =
            eventTopic("MerchantApproved(address,bool)");
    private static final String SIG_VOUCHER_USED =
            eventTopic("VoucherUsed(uint256,address,address,uint256,uint256,uint256,uint256,bytes32,bytes32)");

    private final BlockchainService blockchainService;
    private final ChainSyncStateRepository chainSyncStateRepository;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void scanNewEvents() {
        ChainSyncState state = chainSyncStateRepository.findByScannerName(SCANNER_NAME)
                .orElseGet(this::createInitialState);

        BigInteger fromBlock = BigInteger.valueOf(state.getLastProcessedBlock() + 1);
        BigInteger currentBlock;

        try {
            currentBlock = blockchainService.getCurrentBlockNumber();
        } catch (Exception e) {
            log.error("[SCANNER] 현재 블록 조회 실패 — 다음 주기 재시도: {}", e.getMessage());
            return;
        }

        if (fromBlock.compareTo(currentBlock) > 0) {
            // 새 블록 없음 — 조용히 패스
            return;
        }

        log.info("[SCANNER] 스캔 시작 — range: {} ~ {}", fromBlock, currentBlock);

        try {
            scanProgramCreated(fromBlock, currentBlock);
            scanVoucherMinted(fromBlock, currentBlock);
            scanMerchantApproved(fromBlock, currentBlock);
            scanVoucherUsed(fromBlock, currentBlock);

            state.update(currentBlock.longValue());
            chainSyncStateRepository.save(state);
            log.info("[SCANNER] 스캔 완료 — lastProcessedBlock: {}", currentBlock);
        } catch (Exception e) {
            log.error("[SCANNER] 스캔 중 예외 발생 — lastProcessedBlock 미갱신, 다음 주기 재시도: {}", e.getMessage(), e);
        }
    }

    private ChainSyncState createInitialState() {
        ChainSyncState initial = ChainSyncState.builder()
                .scannerName(SCANNER_NAME)
                .lastProcessedBlock(0L)
                .updatedAt(LocalDateTime.now())
                .build();
        return chainSyncStateRepository.save(initial);
    }

    private void scanProgramCreated(BigInteger from, BigInteger to) {
        List<Log> logs = blockchainService.getEventLogs(from, to, SIG_VOUCHER_PROGRAM_CREATED);
        for (Log l : logs) {
            // topics: [sig, programId(uint16), issuer(address)]
            Long programId = topicToLong(l, 1);
            String issuer = topicToAddress(l, 2);
            log.info("[SCANNER] VoucherProgramCreated block={} programId={} issuer={} tx={}",
                    l.getBlockNumber(), programId, issuer, l.getTransactionHash());
        }
    }

    private void scanVoucherMinted(BigInteger from, BigInteger to) {
        List<Log> logs = blockchainService.getEventLogs(from, to, SIG_VOUCHER_MINTED);
        for (Log l : logs) {
            // topics: [sig, tokenId(uint256), programId(uint16), recipient(address)]
            Long tokenId = topicToLong(l, 1);
            Long programId = topicToLong(l, 2);
            String recipient = topicToAddress(l, 3);
            log.info("[SCANNER] VoucherMinted block={} tokenId={} programId={} recipient={} tx={}",
                    l.getBlockNumber(), tokenId, programId, recipient, l.getTransactionHash());
        }
    }

    private void scanMerchantApproved(BigInteger from, BigInteger to) {
        List<Log> logs = blockchainService.getEventLogs(from, to, SIG_MERCHANT_APPROVED);
        for (Log l : logs) {
            // topics: [sig, merchant(address)]; data: bool approved
            String merchant = topicToAddress(l, 1);
            boolean approved = dataToBool(l);
            log.info("[SCANNER] MerchantApproved block={} merchant={} approved={} tx={}",
                    l.getBlockNumber(), merchant, approved, l.getTransactionHash());
        }
    }

    private void scanVoucherUsed(BigInteger from, BigInteger to) {
        List<Log> logs = blockchainService.getEventLogs(from, to, SIG_VOUCHER_USED);
        for (Log l : logs) {
            // topics: [sig, tokenId(uint256), user(address), merchant(address)]
            Long tokenId = topicToLong(l, 1);
            String user = topicToAddress(l, 2);
            String merchant = topicToAddress(l, 3);
            log.info("[SCANNER] VoucherUsed block={} tokenId={} user={} merchant={} tx={}",
                    l.getBlockNumber(), tokenId, user, merchant, l.getTransactionHash());
        }
    }

    // --- 토픽/데이터 파싱 헬퍼 ---

    private static String eventTopic(String signature) {
        byte[] hash = Hash.sha3(signature.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Numeric.toHexString(hash);
    }

    private static Long topicToLong(Log l, int index) {
        if (l.getTopics() == null || l.getTopics().size() <= index) return null;
        return Numeric.toBigInt(l.getTopics().get(index)).longValueExact();
    }

    /** topic의 마지막 20바이트(40 hex chars)가 address. */
    private static String topicToAddress(Log l, int index) {
        if (l.getTopics() == null || l.getTopics().size() <= index) return null;
        String topic = l.getTopics().get(index);
        // 0x + 64 hex; 마지막 40자가 address
        return "0x" + topic.substring(topic.length() - 40);
    }

    /** non-indexed bool은 data 첫 32바이트에 0/1로 들어있음. */
    private static boolean dataToBool(Log l) {
        String data = l.getData();
        if (data == null || data.length() < 66) return false;
        // 0x + 64 hex; 마지막 char가 0 또는 1
        return data.charAt(data.length() - 1) == '1';
    }
}
