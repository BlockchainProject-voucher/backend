package com.voucher.blockchain;

import com.voucher.config.BlockchainProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint16;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BlockchainService {

    private static final int POLL_INTERVAL_MS = 1000;
    private static final int MAX_POLL_ATTEMPTS = 40;
    private static final BigInteger GAS_LIMIT = BigInteger.valueOf(500_000);

    // Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
    private static final String TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final Web3j web3j;
    private final BlockchainProperties blockchainProperties;

    /**
     * 바우처 프로그램을 온체인에 등록합니다. (동기 — 완료 후 반환)
     * mintVoucher 호출 전에 반드시 온체인에 프로그램이 존재해야 합니다.
     */
    public void createVoucherProgram(int programId, String name, BigInteger amount,
                                     long expiryDate, int totalSupply, String category) {
        log.info("[Blockchain] createVoucherProgram() — programId: {}, name: {}", programId, name);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            Function function = new Function(
                    "createVoucherProgram",
                    List.of(
                            new Uint16(BigInteger.valueOf(programId)),
                            new Utf8String(name),
                            new Uint256(amount),
                            new Uint256(BigInteger.valueOf(expiryDate)),
                            new Uint16(BigInteger.valueOf(totalSupply)),
                            new Utf8String(category)
                    ),
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
                throw new RuntimeException("createVoucherProgram 전송 실패: " + sendResult.getError().getMessage());
            }

            TransactionReceipt receipt = waitForReceipt(sendResult.getTransactionHash());
            if (!"0x1".equals(receipt.getStatus())) {
                throw new RuntimeException("createVoucherProgram revert — txHash: " + receipt.getTransactionHash());
            }

            log.info("[Blockchain] createVoucherProgram 완료 — programId: {}", programId);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("createVoucherProgram 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 민팅 트랜잭션을 전송하고 txHash를 즉시 반환합니다.
     * Receipt 대기 없이 반환하므로 호출 즉시 txHash를 DB에 저장할 수 있습니다.
     *
     * 컨트랙트: mintVoucher(uint16 programId, address recipient, string uri)
     */
    public String sendMintTx(int programId, String recipientAddress, String tokenUri) {
        log.info("[Blockchain] sendMintTx() — programId: {}, to: {}, tokenUri: {}", programId, recipientAddress, tokenUri);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            Function function = new Function(
                    "mintVoucher",
                    List.of(
                            new Uint16(BigInteger.valueOf(programId)),
                            new Address(recipientAddress),
                            new Utf8String(tokenUri)
                    ),
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
     *   topics[0] = TRANSFER_EVENT_TOPIC (이벤트 시그니처 keccak256)
     *   topics[1] = from address (민팅 시 0x0)
     *   topics[2] = to address
     *   topics[3] = tokenId (hex)
     */
    public Long extractTokenId(TransactionReceipt receipt) {
        return receipt.getLogs().stream()
                .filter(log -> log.getTopics().size() == 4
                        && TRANSFER_EVENT_TOPIC.equals(log.getTopics().get(0)))
                .findFirst()
                .map(log -> Numeric.toBigInt(log.getTopics().get(3)).longValue())
                .orElseThrow(() -> new RuntimeException("Transfer 이벤트에서 tokenId를 찾을 수 없습니다."));
    }

    /**
     * 가맹점 주소를 온체인에 승인/취소합니다.
     * 컨트랙트: approveMerchant(address merchant, bool approved)
     */
    public void approveMerchant(String merchantAddress, boolean approved) {
        log.info("[Blockchain] approveMerchant() — merchant: {}, approved: {}", merchantAddress, approved);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            Function function = new Function(
                    "approveMerchant",
                    List.of(new Address(merchantAddress), new org.web3j.abi.datatypes.Bool(approved)),
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
                throw new RuntimeException("approveMerchant 전송 실패: " + sendResult.getError().getMessage());
            }

            TransactionReceipt receipt = waitForReceipt(sendResult.getTransactionHash());
            if (!"0x1".equals(receipt.getStatus())) {
                throw new RuntimeException("approveMerchant revert — txHash: " + receipt.getTransactionHash());
            }

            log.info("[Blockchain] approveMerchant 완료 — merchant: {}, approved: {}", merchantAddress, approved);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("approveMerchant 중 오류: " + e.getMessage(), e);
        }
    }

    /** 컨트랙트에서 useNonce[tokenId] 값을 읽어옵니다 (EIP-712 서명 시 필요). */
    public BigInteger getUseNonce(Long onChainTokenId) {
        try {
            Function function = new Function(
                    "useNonce",
                    List.of(new Uint256(BigInteger.valueOf(onChainTokenId))),
                    List.of(new TypeReference<Uint256>() {})
            );
            org.web3j.protocol.core.methods.request.Transaction tx =
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            null, blockchainProperties.getContractAddress(), FunctionEncoder.encode(function));
            String result = web3j.ethCall(tx, DefaultBlockParameterName.LATEST).send().getValue();
            List<Type> decoded = FunctionReturnDecoder.decode(result, function.getOutputParameters());
            return ((Uint256) decoded.get(0)).getValue();
        } catch (Exception e) {
            throw new RuntimeException("useNonce 조회 실패: " + e.getMessage(), e);
        }
    }

    /** 연결된 체인의 chainId를 반환합니다 (EIP-712 도메인에 필요). */
    public long getChainId() {
        try {
            return web3j.ethChainId().send().getChainId().longValue();
        } catch (Exception e) {
            throw new RuntimeException("chainId 조회 실패: " + e.getMessage(), e);
        }
    }

    /** 백엔드 서명 지갑 주소를 반환합니다 (EIP-712 merchant 필드에 사용). */
    public String getBackendWalletAddress() {
        return Credentials.create(blockchainProperties.getPrivateKey()).getAddress();
    }

    /** 배포된 컨트랙트 주소를 반환합니다 (EIP-712 verifyingContract 필드에 사용). */
    public String getContractAddress() {
        return blockchainProperties.getContractAddress();
    }

    /** canonicalJson의 keccak256 해시를 반환합니다 (metadataHash 생성). */
    public String computeMetadataHash(String canonicalJson) {
        byte[] hashBytes = Hash.sha3(canonicalJson.getBytes(StandardCharsets.UTF_8));
        return Numeric.toHexString(hashBytes);
    }

    /**
     * useVoucherByMerchant 트랜잭션을 전송하고 txHash를 즉시 반환합니다.
     * 컨트랙트: useVoucherByMerchant(uint256 tokenId, uint256 amount, bytes32 metadataHash, uint256 deadline, bytes ownerSignature)
     */
    public String sendUseVoucherTx(Long onChainTokenId, Long amount, String metadataHash,
                                   long deadline, String ownerSignature) {
        log.info("[Blockchain] sendUseVoucherTx() — tokenId: {}, amount: {}", onChainTokenId, amount);
        try {
            Credentials credentials = Credentials.create(blockchainProperties.getPrivateKey());

            BigInteger nonce = web3j.ethGetTransactionCount(
                    credentials.getAddress(), DefaultBlockParameterName.LATEST
            ).send().getTransactionCount();

            BigInteger gasPrice = web3j.ethGasPrice().send().getGasPrice();

            byte[] metadataHashBytes = Numeric.hexStringToByteArray(metadataHash);
            byte[] metadataHash32 = new byte[32];
            System.arraycopy(metadataHashBytes, metadataHashBytes.length - 32, metadataHash32, 0, 32);

            Function function = new Function(
                    "useVoucherByMerchant",
                    List.of(
                            new Uint256(BigInteger.valueOf(onChainTokenId)),
                            new Uint256(BigInteger.valueOf(amount)),
                            new Bytes32(metadataHash32),
                            new Uint256(BigInteger.valueOf(deadline)),
                            new DynamicBytes(Numeric.hexStringToByteArray(ownerSignature))
                    ),
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
                throw new RuntimeException("useVoucherByMerchant 전송 실패: " + sendResult.getError().getMessage());
            }

            String txHash = sendResult.getTransactionHash();
            log.info("[Blockchain] useVoucher tx sent — txHash: {}", txHash);
            return txHash;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("useVoucherByMerchant 전송 중 오류: " + e.getMessage(), e);
        }
    }

    public String generateTokenUri(Long voucherId, String baseUrl) {
        return baseUrl + "/api/metadata/" + voucherId;
    }
}
