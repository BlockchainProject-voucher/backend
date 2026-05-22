package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.domain.Member;
import com.voucher.domain.Voucher;
import com.voucher.domain.VoucherProgram;
import com.voucher.domain.VoucherUseHistory;
import com.voucher.domain.enums.ProgramStatus;
import com.voucher.domain.enums.Role;
import com.voucher.domain.enums.UseStatus;
import com.voucher.domain.enums.VoucherStatus;
import com.voucher.dto.request.CreateVoucherRequest;
import com.voucher.dto.request.MerchantPrepareRequest;
import com.voucher.dto.request.UseVoucherPrepareRequest;
import com.voucher.dto.request.UseVoucherRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.UseVoucherPrepareResponse;
import com.voucher.dto.response.VoucherQrResponse;
import com.voucher.dto.response.VoucherResponse;
import com.voucher.dto.response.VoucherUseHistoryResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.VoucherRepository;
import com.voucher.repository.VoucherUseHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherUseHistoryRepository voucherUseHistoryRepository;
    private final MemberService memberService;
    private final VoucherProgramService voucherProgramService;
    private final BlockchainService blockchainService;
    private final VoucherPersistenceService voucherPersistenceService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 바우처 발급 흐름 — 각 DB 쓰기를 독립 트랜잭션으로 커밋해 txHash 유실 방지:
     *
     *  ① DB 저장        VoucherPersistenceService → 즉시 커밋 (voucherId 확보)
     *  ② tx 전송        sendMintTx() → txHash 즉시 반환
     *  ③ txHash DB 저장 VoucherPersistenceService → 즉시 커밋
     *                   → 이후 Receipt 폴링 타임아웃 시에도 txHash 보존
     *  ④ Receipt 폴링   waitForReceipt() — 1초 간격 최대 40초
     *  ⑤ tokenId 추출   Transfer 이벤트 topics[3]
     *  ⑥ DB 업데이트    VoucherPersistenceService → 즉시 커밋 (status = ACTIVE)
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 외부 트랜잭션 없이 실행 — 각 단계가 독립 커밋
    public ApiResponse<VoucherResponse> issueVoucher(CreateVoucherRequest request) {
        Member owner = memberService.findByWalletOrThrow(request.getWalletAddress());
        VoucherProgram program = voucherProgramService.findByIdOrThrow(request.getVoucherProgramId());

        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_PROGRAM_INACTIVE);
        }

        // ① PENDING 바우처 저장 + tokenUri 설정 → 즉시 커밋
        Voucher voucher = voucherPersistenceService.createPendingVoucher(
                owner.getId(), program.getId(), baseUrl);

        // ② 트랜잭션 전송 → txHash 즉시 반환
        String txHash;
        try {
            txHash = blockchainService.sendMintTx(program.getId().intValue(), owner.getWalletAddress(), voucher.getTokenUri());
        } catch (Exception e) {
            log.error("tx 전송 실패 — wallet: {}, programId: {}", owner.getWalletAddress(), program.getId(), e);
            throw new BusinessException(ErrorCode.MINT_FAILED);
        }

        // ③ txHash 독립 트랜잭션으로 커밋 — 이후 Receipt 폴링 타임아웃 시에도 txHash 보존됨
        voucherPersistenceService.persistTxHash(voucher.getId(), txHash);

        // ④ Receipt 폴링 (타임아웃 시 예외 발생 — txHash는 이미 커밋되어 있음)
        TransactionReceipt receipt;
        try {
            receipt = blockchainService.waitForReceipt(txHash);
        } catch (Exception e) {
            log.error("Receipt 타임아웃 — txHash: {}", txHash, e);
            throw new BusinessException(ErrorCode.MINT_TIMEOUT);
        }

        // ⑤⑥ tokenId 추출 후 DB 업데이트 (status → ACTIVE) → 즉시 커밋
        Long tokenId = blockchainService.extractTokenId(receipt);
        VoucherResponse response = voucherPersistenceService.confirmMinting(
                voucher.getId(), tokenId, txHash, receipt.getBlockNumber().longValue());

        log.info("바우처 발급 완료 — voucherId: {}, tokenId: {}, txHash: {}", voucher.getId(), tokenId, txHash);
        return ApiResponse.success(response);
    }

    public ApiResponse<List<VoucherResponse>> getMyVouchers(String walletAddress) {
        Member owner = memberService.findByWalletOrThrow(walletAddress);
        List<VoucherResponse> list = voucherRepository.findAllByOwner(owner)
                .stream()
                .map(VoucherResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    public ApiResponse<VoucherResponse> getVoucher(Long id, String walletAddress) {
        Voucher voucher = voucherRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
        if (!voucher.getOwner().getWalletAddress().equalsIgnoreCase(walletAddress)) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }
        return ApiResponse.success(VoucherResponse.from(voucher));
    }

    /**
     * 바우처 QR 데이터 반환:
     * 프론트엔드는 이 데이터를 QR 이미지로 렌더링합니다.
     * 가맹점이 QR을 스캔하면 voucherId + ownerWallet을 얻어 merchantPrepareUse를 호출합니다.
     */
    public ApiResponse<VoucherQrResponse> getQrData(Long voucherId, String ownerWallet) {
        Voucher voucher = findByIdOrThrow(voucherId);
        if (!voucher.getOwner().getWalletAddress().equalsIgnoreCase(ownerWallet)) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_ACTIVE);
        }
        return ApiResponse.success(VoucherQrResponse.from(voucher));
    }

    /**
     * 사용자가 대기 중인 결제 요청 목록 조회:
     * 가맹점이 QR 스캔 후 생성한 PENDING 이력을 사용자가 확인하고 서명합니다.
     */
    public ApiResponse<List<UseVoucherPrepareResponse>> getPendingUseRequests(String ownerWallet) {
        List<VoucherUseHistory> pending = voucherUseHistoryRepository
                .findAllByVoucher_Owner_WalletAddressIgnoreCaseAndStatusOrderByIdDesc(
                        ownerWallet, UseStatus.PENDING);

        long chainId = blockchainService.getChainId();

        List<UseVoucherPrepareResponse> responses = pending.stream()
                .map(history -> {
                    Map<String, Object> eip712 = buildEip712Data(
                            chainId,
                            history.getVoucher().getOnChainTokenId(),
                            history.getVoucher().getOwner().getWalletAddress(),
                            history.getMerchant().getWalletAddress(),
                            history.getAmount(),
                            history.getMetadataHash(),
                            BigInteger.valueOf(history.getUseNonce()),
                            history.getDeadline()
                    );
                    return UseVoucherPrepareResponse.builder()
                            .historyId(history.getId())
                            .metadataHash(history.getMetadataHash())
                            .nonce(BigInteger.valueOf(history.getUseNonce()))
                            .deadline(history.getDeadline())
                            .eip712(eip712)
                            .build();
                })
                .collect(Collectors.toList());

        return ApiResponse.success(responses);
    }

    /**
     * 바우처 사용 준비 (사용자 주도):
     * DB에 PENDING 이력 저장 → metadataHash 생성 → EIP-712 데이터 반환
     * 프론트엔드는 반환된 eip712 데이터를 MetaMask eth_signTypedData_v4로 서명 후 executeUse 호출
     */
    @Transactional
    public ApiResponse<UseVoucherPrepareResponse> prepareUse(Long voucherId,
                                                              UseVoucherPrepareRequest request,
                                                              String ownerWallet) {
        Voucher voucher = findByIdOrThrow(voucherId);

        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_ACTIVE);
        }
        if (!voucher.getOwner().getWalletAddress().equalsIgnoreCase(ownerWallet)) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }

        Member merchant = memberService.findByWalletOrThrow(request.getMerchantWallet());
        if (merchant.getRole() != Role.MERCHANT) {
            throw new BusinessException(ErrorCode.NOT_MERCHANT);
        }

        return ApiResponse.success(buildPrepareResponse(voucher, merchant, request.getAmount(), ownerWallet));
    }

    /**
     * 바우처 사용 준비 (가맹점 주도 — QR 스캔):
     * 가맹점이 사용자 QR을 스캔한 후 금액을 입력해 호출합니다.
     * 반환된 eip712 데이터를 사용자가 서명 후 executeUse를 호출합니다.
     */
    @Transactional
    public ApiResponse<UseVoucherPrepareResponse> merchantPrepareUse(MerchantPrepareRequest request,
                                                                      String merchantWallet) {
        Member merchant = memberService.findByWalletOrThrow(merchantWallet);
        if (merchant.getRole() != Role.MERCHANT) {
            throw new BusinessException(ErrorCode.NOT_MERCHANT);
        }

        Voucher voucher = findByIdOrThrow(request.getVoucherId());
        if (!voucher.getOwner().getWalletAddress().equalsIgnoreCase(request.getOwnerWallet())) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }
        if (voucher.getStatus() != VoucherStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_NOT_ACTIVE);
        }

        return ApiResponse.success(buildPrepareResponse(voucher, merchant, request.getAmount(), request.getOwnerWallet()));
    }

    private UseVoucherPrepareResponse buildPrepareResponse(Voucher voucher, Member merchant,
                                                            Long amount, String ownerWallet) {
        long oldValue = voucher.getCurrentValue();
        long newValue = oldValue - amount;
        if (newValue < 0) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_VOUCHER_VALUE);
        }

        // nonce를 먼저 조회해 history에 함께 저장 (pending-use 재조회 시 EIP-712 재생성에 필요)
        BigInteger onChainNonce = blockchainService.getUseNonce(voucher.getOnChainTokenId());
        long chainId = blockchainService.getChainId();
        long deadline = Instant.now().plusSeconds(600).getEpochSecond(); // 10분

        VoucherUseHistory history = VoucherUseHistory.builder()
                .voucher(voucher)
                .merchant(merchant)
                .amount(amount)
                .oldValue(oldValue)
                .newValue(newValue)
                .useNonce(onChainNonce.longValue())
                .deadline(deadline)
                .status(UseStatus.PENDING)
                .build();
        history = voucherUseHistoryRepository.save(history);

        String canonicalJson = buildCanonicalJson(history.getId(), voucher.getOnChainTokenId(),
                ownerWallet, merchant.getWalletAddress(), amount, oldValue, newValue, deadline);
        String metadataHash = blockchainService.computeMetadataHash(canonicalJson);
        history.setMetadataInfo(canonicalJson, metadataHash);

        Map<String, Object> eip712 = buildEip712Data(chainId,
                voucher.getOnChainTokenId(), ownerWallet, merchant.getWalletAddress(),
                amount, metadataHash, onChainNonce, deadline);

        log.info("바우처 사용 준비 완료 — historyId: {}, metadataHash: {}", history.getId(), metadataHash);

        return UseVoucherPrepareResponse.builder()
                .historyId(history.getId())
                .metadataHash(metadataHash)
                .nonce(onChainNonce)
                .deadline(deadline)
                .eip712(eip712)
                .build();
    }

    /**
     * 바우처 사용 실행:
     * MetaMask 서명값을 받아 useVoucherByMerchant 온체인 호출 → DB 확정
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ApiResponse<VoucherUseHistoryResponse> executeUse(Long voucherId,
                                                              UseVoucherRequest request,
                                                              String ownerWallet) {
        VoucherUseHistory history = voucherUseHistoryRepository.findById(request.getHistoryId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USE_HISTORY_NOT_FOUND));

        if (history.getStatus() != UseStatus.PENDING) {
            throw new BusinessException(ErrorCode.USE_ALREADY_PROCESSED);
        }
        if (!history.getVoucher().getId().equals(voucherId)) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }
        if (!history.getVoucher().getOwner().getWalletAddress().equalsIgnoreCase(ownerWallet)) {
            throw new BusinessException(ErrorCode.VOUCHER_ACCESS_DENIED);
        }

        String txHash;
        try {
            txHash = blockchainService.sendUseVoucherTx(
                    history.getVoucher().getOnChainTokenId(),
                    history.getAmount(),
                    history.getMetadataHash(),
                    history.getDeadline(),
                    request.getOwnerSignature()
            );
        } catch (Exception e) {
            log.error("useVoucherByMerchant 전송 실패 — historyId: {}", history.getId(), e);
            throw new BusinessException(ErrorCode.USE_FAILED);
        }

        TransactionReceipt receipt;
        try {
            receipt = blockchainService.waitForReceipt(txHash);
        } catch (Exception e) {
            log.error("Receipt 타임아웃 — txHash: {}", txHash, e);
            throw new BusinessException(ErrorCode.MINT_TIMEOUT);
        }

        VoucherUseHistoryResponse response = voucherPersistenceService.confirmUse(
                history.getId(), txHash, receipt.getBlockNumber().longValue());

        log.info("바우처 사용 완료 — historyId: {}, txHash: {}", history.getId(), txHash);
        return ApiResponse.success(response);
    }

    private String buildCanonicalJson(Long historyId, Long tokenId, String ownerWallet,
                                      String merchantWallet, Long amount, long oldValue,
                                      long newValue, long deadline) {
        return String.format(
                "{\"amount\":%d,\"deadline\":%d,\"historyId\":%d,\"merchantWallet\":\"%s\","
                + "\"newValue\":%d,\"oldValue\":%d,\"ownerWallet\":\"%s\",\"tokenId\":%d}",
                amount, deadline, historyId, merchantWallet.toLowerCase(),
                newValue, oldValue, ownerWallet.toLowerCase(), tokenId
        );
    }

    private Map<String, Object> buildEip712Data(long chainId, Long tokenId, String user,
                                                 String merchant, Long amount, String metadataHash,
                                                 BigInteger nonce, long deadline) {
        Map<String, Object> domain = new LinkedHashMap<>();
        domain.put("name", "Voucher");
        domain.put("version", "1");
        domain.put("chainId", chainId);
        // verifyingContract는 컨트랙트 주소여야 함 — EIP712 도메인 분리자가 address(this)를 사용
        domain.put("verifyingContract", blockchainService.getContractAddress());

        // 필드명이 컨트랙트 TYPEHASH와 정확히 일치해야 서명 검증 통과
        // USE_VOUCHER_TYPEHASH: "UseVoucher(uint256 tokenId,address user,address merchant,uint256 amount,bytes32 recordCommitmentHash,uint256 nonce,uint256 deadline)"
        List<Map<String, String>> types = List.of(
                Map.of("name", "tokenId", "type", "uint256"),
                Map.of("name", "user", "type", "address"),
                Map.of("name", "merchant", "type", "address"),
                Map.of("name", "amount", "type", "uint256"),
                Map.of("name", "recordCommitmentHash", "type", "bytes32"),
                Map.of("name", "nonce", "type", "uint256"),
                Map.of("name", "deadline", "type", "uint256")
        );

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("tokenId", tokenId.toString());
        message.put("user", user);
        message.put("merchant", merchant);
        message.put("amount", amount.toString());
        message.put("recordCommitmentHash", metadataHash);
        message.put("nonce", nonce.toString());
        message.put("deadline", Long.toString(deadline));

        Map<String, Object> eip712 = new LinkedHashMap<>();
        eip712.put("domain", domain);
        eip712.put("types", Map.of("UseVoucher", types));
        eip712.put("primaryType", "UseVoucher");
        eip712.put("message", message);
        return eip712;
    }

    public Voucher findByIdOrThrow(Long voucherId) {
        return voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    public Voucher findByOnChainTokenIdOrThrow(Long onChainTokenId) {
        return voucherRepository.findByOnChainTokenId(onChainTokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
    }
}
