package com.voucher.blockchain;

import com.voucher.config.BlockchainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainService {

    private static final int POLL_INTERVAL_MS = 1000;
    private static final int MAX_POLL_ATTEMPTS = 40;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(300_000);

    private final Web3j web3j;
    private final BlockchainProperties blockchainProperties;

    /**
     * ERC-721 바우처 민팅.
     * 트랜잭션 전송 후 1초 간격으로 receipt를 폴링하며 최대 40초 대기.
     *
     * TODO (블록체인팀 확인 필요):
     *   1. 함수명: 현재 "mint" 가정 → safeMint, mintTo 등으로 변경될 수 있음
     *   2. 파라미터: (address ownerAddress, uint256 amount) 가정
     *   3. Transfer 이벤트 tokenId가 indexed 인지 확인 (OpenZeppelin 기준 topics[3])
     */
    public MintResult mintVoucher(String ownerAddress, Long amount) {
        log.info("[Blockchain] mintVoucher() — to: {}, amount: {}", ownerAddress, amount);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            // TODO: 함수명 "mint" 및 파라미터 (address, uint256) 블록체인팀과 확인
            Function function = new Function(
                    "mint",
                    List.of(new Address(ownerAddress), new Uint256(BigInteger.valueOf(amount))),
                    Collections.emptyList()
            );
            String encodedFunction = FunctionEncoder.encode(function);

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, GAS_LIMIT,
                    blockchainProperties.getContractAddress(),
                    encodedFunction
            );

            byte[] signedMessage = TransactionEncoder.signMessage(rawTx, credentials);
            EthSendTransaction sendResult = web3j.ethSendRawTransaction(
                    Numeric.toHexString(signedMessage)
            ).send();

            if (sendResult.hasError()) {
                throw new RuntimeException("트랜잭션 전송 실패: " + sendResult.getError().getMessage());
            }

            String txHash = sendResult.getTransactionHash();
            log.info("[Blockchain] tx sent — txHash: {}", txHash);

            // Receipt 폴링 (1초 간격, 최대 40초 대기)
            TransactionReceipt receipt = pollForReceipt(txHash);

            // Transfer 이벤트 topics[3]에서 tokenId 추출
            Long tokenId = extractTokenId(receipt);

            log.info("[Blockchain] mint confirmed — tokenId: {}, block: {}",
                    tokenId, receipt.getBlockNumber());
            return new MintResult(tokenId, txHash, receipt.getBlockNumber().longValue());

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("블록체인 민팅 중 오류: " + e.getMessage(), e);
        }
    }

    private TransactionReceipt pollForReceipt(String txHash) throws Exception {
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            Optional<TransactionReceipt> receipt = web3j
                    .ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
            if (receipt.isPresent()) {
                return receipt.get();
            }
            log.debug("[Blockchain] polling receipt {}/{} — txHash: {}", attempt, MAX_POLL_ATTEMPTS, txHash);
            Thread.sleep(POLL_INTERVAL_MS);
        }
        // 타임아웃 → @Transactional 롤백 트리거
        throw new RuntimeException("Receipt 타임아웃 (40초 초과) — txHash: " + txHash);
    }

    /**
     * Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
     *   topics[0] = 이벤트 시그니처 해시
     *   topics[1] = from address
     *   topics[2] = to address
     *   topics[3] = tokenId (hex, big-endian)
     *
     * TODO: tokenId가 indexed가 아닌 경우 log.getData()에서 ABI 디코딩으로 변경 필요
     */
    private Long extractTokenId(TransactionReceipt receipt) {
        return receipt.getLogs().stream()
                .filter(log -> log.getTopics().size() >= 4)
                .findFirst()
                .map(log -> Numeric.toBigInt(log.getTopics().get(3)).longValue())
                .orElseThrow(() -> new RuntimeException("Transfer 이벤트에서 tokenId를 찾을 수 없습니다."));
    }

    /**
     * TODO: tokenURI 저장 방식 확정 필요 (현재: 로컬 서버 URL / 대안: IPFS)
     */
    public String generateTokenUri(Long programId, String baseUrl) {
        return baseUrl + "/api/metadata/" + programId;
    }
}
