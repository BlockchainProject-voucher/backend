package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.blockchain.MintResult;
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
     *   ① DB에 voucher 저장 (온체인 정보 null, status = ACTIVE)
     *   ② blockchainService.mintVoucher() 호출 — 폴링 대기
     *      타임아웃 시 RuntimeException → @Transactional 롤백
     *   ③ receipt 수신 → txHash / blockNumber / tokenId 추출
     *   ④ voucher.confirmMinting()으로 온체인 정보 업데이트
     *   ⑤ VoucherResponse 반환
     */
    @Transactional
    public ApiResponse<VoucherResponse> issueVoucher(CreateVoucherRequest request) {
        Member owner = memberService.findByWalletOrThrow(request.getWalletAddress());
        VoucherProgram program = voucherProgramService.findByIdOrThrow(request.getVoucherProgramId());

        if (program.getStatus() != ProgramStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.VOUCHER_PROGRAM_INACTIVE);
        }

        String tokenUri = blockchainService.generateTokenUri(program.getId(), baseUrl);

        // ① DB 먼저 저장 (on_chain_token_id / tx_hash / block_number = null)
        Voucher voucher = Voucher.builder()
                .onChainTokenId(null)
                .voucherProgram(program)
                .owner(owner)
                .currentValue(program.getMaxValue())
                .initialValue(program.getMaxValue())
                .tokenUri(tokenUri)
                .txHash(null)
                .blockNumber(null)
                .status(VoucherStatus.ACTIVE)
                .mintedAt(null)
                .build();
        voucherRepository.save(voucher);

        // ② 블록체인 민팅 (1초 폴링, 최대 40초 대기)
        MintResult mintResult;
        try {
            mintResult = blockchainService.mintVoucher(owner.getWalletAddress(), program.getMaxValue());
        } catch (RuntimeException e) {
            log.error("Mint failed — wallet: {}, programId: {}, error: {}",
                    owner.getWalletAddress(), program.getId(), e.getMessage(), e);
            // RuntimeException 으로 @Transactional 롤백 트리거
            throw new BusinessException(ErrorCode.MINT_FAILED);
        }

        // ④ 온체인 정보 업데이트 (Hibernate dirty checking → UPDATE 발생)
        voucher.confirmMinting(mintResult.getTokenId(), mintResult.getTxHash(), mintResult.getBlockNumber());

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

    public Voucher findByOnChainTokenIdOrThrow(Long onChainTokenId) {
        return voucherRepository.findByOnChainTokenId(onChainTokenId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
    }
}
