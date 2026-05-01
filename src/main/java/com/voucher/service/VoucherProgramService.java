package com.voucher.service;

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

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VoucherProgramService {

    private final VoucherProgramRepository voucherProgramRepository;
    private final MemberService memberService;

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
                .contractAddress(request.getContractAddress())
                .maxValue(request.getMaxValue())
                .validFrom(request.getValidFrom())
                .validUntil(request.getValidUntil())
                .status(ProgramStatus.ACTIVE)
                .build();
        return ApiResponse.success(VoucherProgramResponse.from(voucherProgramRepository.save(program)));
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
