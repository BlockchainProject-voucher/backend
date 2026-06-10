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
    private Long maxValue;
    private Integer totalSupply;
    private String category;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
    private ProgramStatus status;
    private Integer minAge;
    private Integer maxAge;
    private String allowedRegions;
    private String usageGuide;
    private String issuanceGuide;
    private String refundPolicy;
    private LocalDateTime createdAt;

    public static VoucherProgramResponse from(VoucherProgram program) {
        return VoucherProgramResponse.builder()
                .id(program.getId())
                .createdById(program.getCreatedBy().getId())
                .createdByWallet(program.getCreatedBy().getWalletAddress())
                .name(program.getName())
                .description(program.getDescription())
                .maxValue(program.getMaxValue())
                .totalSupply(program.getTotalSupply())
                .category(program.getCategory())
                .validFrom(program.getValidFrom())
                .validUntil(program.getValidUntil())
                .status(program.getStatus())
                .minAge(program.getMinAge())
                .maxAge(program.getMaxAge())
                .allowedRegions(program.getAllowedRegions())
                .usageGuide(program.getUsageGuide())
                .issuanceGuide(program.getIssuanceGuide())
                .refundPolicy(program.getRefundPolicy())
                .createdAt(program.getCreatedAt())
                .build();
    }
}
