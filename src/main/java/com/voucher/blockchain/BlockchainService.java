package com.voucher.blockchain;

import com.voucher.config.BlockchainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
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
     * 민팅 트랜잭션을 전송하고 txHash를 즉시 반환합니다.
     * Receipt 대기 없이 반환하므로 호출 즉시 txHash를 DB에 저장할 수 있습니다.
     *
     * TODO (블록체인 확인 필요): 가스 전략: 현재 ethGasPrice() 사용 → EIP-1559 방식으로 변경 가능
     */
    public String sendMintTx(String ownerAddress, String tokenUri) {
        log.info("[Blockchain] sendMintTx() — to: {}, tokenUri: {}", ownerAddress, tokenUri);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            Function function = new Function(
                    "mintVoucher",
                    List.of(new Address(ownerAddress), new Utf8String(tokenUri)),
                    Collections.emptyList()
            );

            RawTransaction rawTx = RawTransaction.createTransaction(
                    nonce, gasPrice, GAS_LIMIT,
                    blockchainProperties.getContractAddress(),
                    FunctionEncoder.encode(function)
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
            return txHash;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("트랜잭션 전송 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * txHash로 Receipt를 폴링합니다.
     * 1초 간격으로 최대 40초 대기하며, 타임아웃 시 RuntimeException을 던집니다.
     * 타임아웃이 발생해도 txHash는 이미 DB에 저장된 상태이므로 수동 복구가 가능합니다.
     */
    public TransactionReceipt waitForReceipt(String txHash) {
        log.info("[Blockchain] waiting for receipt — txHash: {}", txHash);
        try {
            for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
                Optional<TransactionReceipt> receipt = web3j
                        .ethGetTransactionReceipt(txHash).send().getTransactionReceipt();
                if (receipt.isPresent()) {
                    log.info("[Blockchain] receipt arrived — attempt: {}/{}", attempt, MAX_POLL_ATTEMPTS);
                    return receipt.get();
                }
                log.debug("[Blockchain] polling {}/{}", attempt, MAX_POLL_ATTEMPTS);
                Thread.sleep(POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("폴링 중 인터럽트 발생 — txHash: " + txHash, e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Receipt 조회 중 오류: " + e.getMessage(), e);
        }
        throw new RuntimeException("Receipt 타임아웃 (40초 초과) — txHash: " + txHash);
    }

    /**
     * Receipt 로그에서 ERC-721 Transfer 이벤트의 tokenId를 추출합니다.
     *
     * Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
     *   topics[0] = 이벤트 시그니처 해시
     *   topics[1] = from address
     *   topics[2] = to address
     *   topics[3] = tokenId (hex)
     *
     * TODO: tokenId가 indexed가 아닌 컨트랙트의 경우 log.getData()에서 ABI 디코딩 필요
     */
    public Long extractTokenId(TransactionReceipt receipt) {
        return receipt.getLogs().stream()
                .filter(log -> log.getTopics().size() >= 4)
                .findFirst()
                .map(log -> Numeric.toBigInt(log.getTopics().get(3)).longValue())
                .orElseThrow(() -> new RuntimeException("Transfer 이벤트에서 tokenId를 찾을 수 없습니다."));
    }

    /**
     * TODO: tokenURI 저장 방식 블록체인팀과 확인 (현재: 로컬 서버 URL / 대안: IPFS)
     */
    public String generateTokenUri(Long programId, String baseUrl) {
        return baseUrl + "/api/metadata/" + programId;
    }
}
