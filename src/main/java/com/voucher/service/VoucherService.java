package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.domain.Member;
import com.voucher.domain.Voucher;
import com.voucher.domain.VoucherProgram;
import com.voucher.domain.enums.ProgramStatus;
import com.voucher.domain.enums.VoucherStatus;
import com.voucher.dto.request.CreateVoucherRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.VoucherResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final MemberService memberService;
    private final VoucherProgramService voucherProgramService;
    private final BlockchainService blockchainService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * 바우처 발급 흐름:
     *
     *  ① DB 저장        status = PENDING, 블록체인 필드 전부 null
     *  ② tx 전송        sendMintTx() → txHash 즉시 반환
     *  ③ txHash DB 저장 setPendingTx(txHash) — 타임아웃 시에도 txHash 보존
     *  ④ Receipt 폴링   waitForReceipt() — 1초 간격 최대 40초
     *     타임아웃 시   txHash가 DB에 남아 수동 복구 가능
     *  ⑤ tokenId 추출   Transfer 이벤트 topics[3]
     *  ⑥ DB 업데이트    confirmMinting() → status = ACTIVE
     */
    @Transactional
    public ApiResponse<VoucherResponse> issueVoucher(CreateVoucherRequest request) {
        Member owner = memberService.findByWalletOrThrow(request.getWalletAddress());
        VoucherProgram program = voucherProgramService.findByIdOrThrow(request.getVoucherProgramId());

        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_PROGRAM_INACTIVE);
        }

        // ① DB 저장 (status = PENDING) — voucherId 확보 후 tokenUri 생성
        Voucher voucher = Voucher.builder()
                .voucherProgram(program)
                .owner(owner)
                .currentValue(program.getMaxValue())
                .initialValue(program.getMaxValue())
                .status(VoucherStatus.PENDING)
                .build();
        voucherRepository.save(voucher);

        String tokenUri = blockchainService.generateTokenUri(voucher.getId(), baseUrl);
        voucher.updateTokenUri(tokenUri);

        // ② 트랜잭션 전송 → txHash 즉시 반환
        String txHash;
        try {
            txHash = blockchainService.sendMintTx(owner.getWalletAddress(), tokenUri);
        } catch (Exception e) {
            log.error("tx 전송 실패 — wallet: {}, programId: {}", owner.getWalletAddress(), program.getId(), e);
            throw new BusinessException(ErrorCode.MINT_FAILED);
        }

        // ③ txHash 즉시 DB 저장 — 이후 타임아웃이 발생해도 txHash로 온체인 상태 확인 가능
        voucher.setPendingTx(txHash);

        // ④ Receipt 폴링 (타임아웃 시 RuntimeException → @Transactional 롤백)
        //    롤백되더라도 txHash는 이미 커밋 전이므로 함께 롤백됨
        //    → 타임아웃 후에는 txHash로 직접 온체인 확인 필요
        TransactionReceipt receipt;
        try {
            receipt = blockchainService.waitForReceipt(txHash);
        } catch (Exception e) {
            log.error("Receipt 타임아웃 — txHash: {}", txHash, e);
            throw new BusinessException(ErrorCode.MINT_TIMEOUT);
        }

        // ⑤⑥ tokenId 추출 후 DB 업데이트 (status → ACTIVE)
        Long tokenId = blockchainService.extractTokenId(receipt);
        voucher.confirmMinting(tokenId, txHash, receipt.getBlockNumber().longValue());

        log.info("바우처 발급 완료 — voucherId: {}, tokenId: {}, txHash: {}", voucher.getId(), tokenId, txHash);
        return ApiResponse.success(VoucherResponse.from(voucher));
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

    public Voucher findByIdOrThrow(Long voucherId) {
        return voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
    }

    public Voucher findByOnChainTokenIdOrThrow(Long onChainTokenId) {
        return voucherRepository.findByOnChainTokenId(onChainTokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
    }
}
