package com.voucher.dto.response;

import com.voucher.domain.Voucher;
import com.voucher.domain.enums.VoucherStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherResponse {

    private Long id;
    private Long onChainTokenId;
    private Long voucherProgramId;
    private String programName;
    private Long ownerId;
    private String ownerWallet;
    private String ownerNickname;
    private Long currentValue;
    private Long initialValue;
    private String programCategory;
    private LocalDateTime programValidUntil;
    private String issuedBy;
    private String tokenUri;
    private String txHash;
    private Long blockNumber;
    private VoucherStatus status;
    private LocalDateTime mintedAt;
    private LocalDateTime createdAt;

    public static VoucherResponse from(Voucher voucher) {
        return VoucherResponse.builder()
                .id(voucher.getId())
                .onChainTokenId(voucher.getOnChainTokenId())
                .voucherProgramId(voucher.getVoucherProgram().getId())
                .programName(voucher.getVoucherProgram().getName())
                .ownerId(voucher.getOwner().getId())
                .ownerWallet(voucher.getOwner().getWalletAddress())
                .ownerNickname(voucher.getOwner().getNickname())
                .currentValue(voucher.getCurrentValue())
                .initialValue(voucher.getInitialValue())
                .programCategory(voucher.getVoucherProgram().getCategory())
                .programValidUntil(voucher.getVoucherProgram().getValidUntil())
                .issuedBy(voucher.getVoucherProgram().getCreatedBy().getWalletAddress())
                .tokenUri(voucher.getTokenUri())
                .txHash(voucher.getTxHash())
                .blockNumber(voucher.getBlockNumber())
                .status(voucher.getStatus())
                .mintedAt(voucher.getMintedAt())
                .createdAt(voucher.getCreatedAt())
                .build();
    }
}
