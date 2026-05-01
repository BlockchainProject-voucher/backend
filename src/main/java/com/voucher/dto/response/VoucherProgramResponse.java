package com.voucher.dto.response;

import com.voucher.domain.VoucherProgram;
import com.voucher.domain.enums.ProgramStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherProgramResponse {

    private Long id;
    private Long createdById;
    private String createdByWallet;
    private String name;
    private String description;
    private String contractAddress;
    private Long maxValue;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private ProgramStatus status;
    private LocalDateTime createdAt;

    public static VoucherProgramResponse from(VoucherProgram program) {
        return VoucherProgramResponse.builder()
                .id(program.getId())
                .createdById(program.getCreatedBy().getId())
                .createdByWallet(program.getCreatedBy().getWalletAddress())
                .name(program.getName())
                .description(program.getDescription())
                .contractAddress(program.getContractAddress())
                .maxValue(program.getMaxValue())
                .validFrom(program.getValidFrom())
                .validUntil(program.getValidUntil())
                .status(program.getStatus())
                .createdAt(program.getCreatedAt())
                .build();
    }
}
