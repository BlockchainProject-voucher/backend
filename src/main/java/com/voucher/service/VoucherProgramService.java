package com.voucher.service;

import com.voucher.blockchain.BlockchainService;
import com.voucher.domain.Member;
import com.voucher.domain.VoucherProgram;
import com.voucher.domain.enums.ProgramStatus;
import com.voucher.domain.enums.Role;
import com.voucher.dto.request.CreateVoucherProgramRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.VoucherProgramResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.VoucherProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherProgramService {

    private final VoucherProgramRepository voucherProgramRepository;
    private final MemberService memberService;
    private final BlockchainService blockchainService;

    @Transactional
    public ApiResponse<VoucherProgramResponse> createProgram(CreateVoucherProgramRequest request) {
        Member requester = memberService.findByWalletOrThrow(request.getWalletAddress());
        if (requester.getRole() != Role.ADMIN) {
            throw new BusinessException(ErrorCode.NOT_ADMIN);
        }
        if (voucherProgramRepository.existsByName(request.getName())) {
            throw new BusinessException(ErrorCode.VOUCHER_PROGRAM_NAME_DUPLICATE);
        }
        VoucherProgram program = VoucherProgram.builder()
                .createdBy(requester)
                .name(request.getName())
                .description(request.getDescription())
                .maxValue(request.getMaxValue())
                .totalSupply(request.getTotalSupply())
                .category(request.getCategory())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .status(ProgramStatus.ACTIVE)
                .build();
        VoucherProgram saved = voucherProgramRepository.save(program);

        // DB id를 온체인 programId로 사용 (uint16 범위: 1 ~ 65535)
        blockchainService.createVoucherProgram(
                saved.getId().intValue(),
                saved.getName(),
                BigInteger.valueOf(saved.getMaxValue()),
                saved.getValidUntil().toEpochSecond(ZoneOffset.UTC),
                saved.getTotalSupply(),
                saved.getCategory()
        );

        return ApiResponse.success(VoucherProgramResponse.from(saved));
    }

    public ApiResponse<List<VoucherProgramResponse>> getActivePrograms() {
        List<VoucherProgramResponse> list = voucherProgramRepository.findAllByStatus(ProgramStatus.ACTIVE)
                .stream()
                .map(VoucherProgramResponse::from)
                .collect(Collectors.toList());
        return ApiResponse.success(list);
    }

    public ApiResponse<VoucherProgramResponse> getProgram(Long id) {
        return ApiResponse.success(VoucherProgramResponse.from(findByIdOrThrow(id)));
    }

    public VoucherProgram findByIdOrThrow(Long id) {
        return voucherProgramRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_PROGRAM_NOT_FOUND));
    }
}
