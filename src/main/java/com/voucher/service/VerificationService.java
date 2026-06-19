package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.domain.Voucher;
import com.voucher.domain.VoucherUseHistory;
import com.voucher.dto.response.VerificationResponse;
import com.voucher.repository.VoucherRepository;
import com.voucher.repository.VoucherUseHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Hash;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 무결성 검증 서비스.
 *
 * 한 토큰 ID에 대해:
 *  1. DB에서 (잔액, hash 리스트, nonce, 소유자, 프로그램명) 가져오기
 *  2. 온체인에서 같은 항목 (잔액, hash 리스트, nonce) 가져오기
 *  3. 비교 → 4가지 상태 중 하나로 판정
 *
 * "안 쓰이는 facility 없이 다 활용"의 핵심:
 *  - voucherValue[tokenId]  : 컨트랙트 public 변수 → BlockchainService.getVoucherValue
 *  - useNonce[tokenId]      : 컨트랙트 public 변수 → BlockchainService.getUseNonce
 *  - VoucherUsed 이벤트      : 이벤트 로그 → BlockchainService.getEventLogs
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    /** VoucherUsed 이벤트의 시그니처 topic (keccak256). */
    private static final String SIG_VOUCHER_USED = Numeric.toHexString(Hash.sha3(
            "VoucherUsed(uint256,address,address,uint256,uint256,uint256,uint256,bytes32,bytes32)"
                    .getBytes(StandardCharsets.UTF_8)
    ));

    private final VoucherRepository voucherRepository;
    private final VoucherUseHistoryRepository historyRepository;
    private final BlockchainService blockchainService;

    @Transactional(readOnly = true)
    public VerificationResponse verify(Long tokenId) {
        DbSnapshot db = fetchDbSnapshot(tokenId);
        OnChainSnapshot onChain = fetchOnChainSnapshot(tokenId);
        return judge(tokenId, db, onChain);
    }

    // ─────────────────────────────────────────────────────────
    // 1. DB 스냅샷
    // ─────────────────────────────────────────────────────────
    private DbSnapshot fetchDbSnapshot(Long tokenId) {
        Optional<Voucher> voucherOpt = voucherRepository.findByOnChainTokenId(tokenId);
        if (voucherOpt.isEmpty()) {
            return DbSnapshot.absent();
        }
        Voucher v = voucherOpt.get();
        List<VoucherUseHistory> histories = historyRepository.findAllByVoucherId(v.getId());
        List<String> hashes = histories.stream()
                .map(VoucherUseHistory::getMetadataHash)
                .filter(Objects::nonNull)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        // DB의 nonce는 결제 횟수 (성공한 useVoucher 호출 수와 동치)
        Long nonce = (long) histories.size();
        return new DbSnapshot(
                v.getCurrentValue(),
                hashes,
                nonce,
                v.getOwner().getWalletAddress(),
                v.getVoucherProgram().getName(),
                true
        );
    }

    // ─────────────────────────────────────────────────────────
    // 2. 온체인 스냅샷
    // ─────────────────────────────────────────────────────────
    private OnChainSnapshot fetchOnChainSnapshot(Long tokenId) {
        try {
            BigInteger value = blockchainService.getVoucherValue(tokenId);
            BigInteger nonce = blockchainService.getUseNonce(tokenId);

            BigInteger latestBlock = blockchainService.getCurrentBlockNumber();
            List<Log> allUsedLogs = blockchainService.getEventLogs(
                    BigInteger.ZERO, latestBlock, SIG_VOUCHER_USED
            );

            // 이 tokenId의 VoucherUsed 이벤트만 필터 → recordCommitmentHash 추출
            List<String> hashes = allUsedLogs.stream()
                    .filter(l -> matchesTokenId(l, tokenId))
                    .map(this::extractRecordCommitmentHash)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());

            // 토큰이 존재하지 않으면 voucherValue도 0이 나오므로,
            // "한 번도 발급되지 않은 토큰"과 "발급됐는데 다 쓴 토큰"을 구분해야 함.
            // → nonce > 0 또는 value > 0 또는 hash가 있으면 온체인에 존재
            boolean exists = value.signum() > 0 || nonce.signum() > 0 || !hashes.isEmpty();

            return new OnChainSnapshot(value.longValue(), hashes, nonce.longValue(), exists);
        } catch (Exception e) {
            log.warn("[Verify] 온체인 조회 실패 (tokenId={}): {}", tokenId, e.getMessage());
            return OnChainSnapshot.absent();
        }
    }

    // ─────────────────────────────────────────────────────────
    // 3. 판정
    // ─────────────────────────────────────────────────────────
    private VerificationResponse judge(Long tokenId, DbSnapshot db, OnChainSnapshot onChain) {
        String status;
        String message;
        Boolean valueMatch = null;
        Boolean hashMatch = null;
        Boolean nonceMatch = null;

        if (!db.exists && !onChain.exists) {
            status = "MISSING_ONCHAIN";
            message = "DB와 온체인 모두 해당 토큰 데이터가 없습니다. (존재하지 않는 토큰)";
        } else if (!db.exists) {
            status = "MISSING_DB";
            message = "온체인에는 존재하지만 DB에 기록이 없습니다. 백엔드 동기화 누락이 의심됩니다.";
        } else if (!onChain.exists) {
            status = "MISSING_ONCHAIN";
            message = "DB에는 있지만 온체인에 존재하지 않습니다. 컨트랙트 호출 누락이 의심됩니다.";
        } else {
            valueMatch = Objects.equals(db.value, onChain.value);
            hashMatch = db.hashes.equals(onChain.hashes);
            nonceMatch = Objects.equals(db.nonce, onChain.nonce);

            if (valueMatch && hashMatch && nonceMatch) {
                status = "VERIFIED";
                message = "온체인과 DB가 모두 일치합니다. 무결성 검증 통과.";
            } else {
                status = "MISMATCH";
                StringBuilder sb = new StringBuilder("위변조 의심 — 불일치 항목: ");
                if (!valueMatch) sb.append("[잔액] ");
                if (!hashMatch) sb.append("[Hash] ");
                if (!nonceMatch) sb.append("[Nonce] ");
                message = sb.toString().trim();
            }
        }

        return VerificationResponse.builder()
                .tokenId(tokenId)
                .status(status)
                .dbValue(db.value)
                .onChainValue(onChain.exists ? onChain.value : null)
                .valueMatch(valueMatch)
                .dbHashes(db.exists ? db.hashes : null)
                .onChainHashes(onChain.exists ? onChain.hashes : null)
                .hashMatch(hashMatch)
                .dbNonce(db.nonce)
                .onChainNonce(onChain.exists ? onChain.nonce : null)
                .nonceMatch(nonceMatch)
                .ownerWallet(db.ownerWallet)
                .programName(db.programName)
                .checkedAt(LocalDateTime.now())
                .message(message)
                .build();
    }

    // ─────────────────────────────────────────────────────────
    // 이벤트 로그 파싱 헬퍼
    // ─────────────────────────────────────────────────────────

    /** topics[1]에 indexed tokenId가 들어있음. */
    private boolean matchesTokenId(Log l, Long tokenId) {
        if (l.getTopics() == null || l.getTopics().size() < 2) return false;
        try {
            return Numeric.toBigInt(l.getTopics().get(1)).longValueExact() == tokenId;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * VoucherUsed 이벤트 data 레이아웃 (각 필드 32바이트):
     *   [0] amount
     *   [1] oldValue
     *   [2] newValue
     *   [3] nonce
     *   [4] recordCommitmentHash  ← 우리가 원하는 것
     *   [5] usageHash
     *
     * 즉 data = "0x" + (4번째 필드 위치) + 32바이트 추출.
     */
    private String extractRecordCommitmentHash(Log l) {
        String data = l.getData();
        if (data == null || data.length() < 2 + 6 * 64) return null;
        int offset = 2 + (4 * 64); // "0x" + 4 fields skipped
        return "0x" + data.substring(offset, offset + 64);
    }

    // ─────────────────────────────────────────────────────────
    // 내부 스냅샷
    // ─────────────────────────────────────────────────────────
    private static class DbSnapshot {
        final Long value;
        final List<String> hashes;
        final Long nonce;
        final String ownerWallet;
        final String programName;
        final boolean exists;

        DbSnapshot(Long value, List<String> hashes, Long nonce,
                   String ownerWallet, String programName, boolean exists) {
            this.value = value;
            this.hashes = hashes;
            this.nonce = nonce;
            this.ownerWallet = ownerWallet;
            this.programName = programName;
            this.exists = exists;
        }

        static DbSnapshot absent() {
            return new DbSnapshot(null, Collections.emptyList(), null, null, null, false);
        }
    }

    private static class OnChainSnapshot {
        final Long value;
        final List<String> hashes;
        final Long nonce;
        final boolean exists;

        OnChainSnapshot(Long value, List<String> hashes, Long nonce, boolean exists) {
            this.value = value;
            this.hashes = hashes;
            this.nonce = nonce;
            this.exists = exists;
        }

        static OnChainSnapshot absent() {
            return new OnChainSnapshot(null, Collections.emptyList(), null, false);
        }
    }
}
