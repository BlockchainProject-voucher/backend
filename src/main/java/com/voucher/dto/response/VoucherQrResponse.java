package com.voucher.dto.response;

import com.voucher.domain.Voucher;
import com.voucher.domain.enums.VoucherStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherQrResponse {

    private Long voucherId;
    private String ownerWallet;
    private String ownerNickname;
    private Long onChainTokenId;
    private Long currentValue;
    private String programName;
    private String category;
    private LocalDateTime expiryDate;
    private boolean isValid;

    public static VoucherQrResponse from(Voucher voucher) {
        boolean valid = voucher.getStatus() == VoucherStatus.ACTIVE
                && voucher.getCurrentValue() != null
                && voucher.getCurrentValue() > 0
                && (voucher.getVoucherProgram().getValidUntil() == null
                    || voucher.getVoucherProgram().getValidUntil().isAfter(LocalDateTime.now()));

        return VoucherQrResponse.builder()
                .voucherId(voucher.getId())
                .ownerWallet(voucher.getOwner().getWalletAddress())
                .ownerNickname(voucher.getOwner().getNickname())
                .onChainTokenId(voucher.getOnChainTokenId())
                .currentValue(voucher.getCurrentValue())
                .programName(voucher.getVoucherProgram().getName())
                .category(voucher.getVoucherProgram().getCategory())
                .expiryDate(voucher.getVoucherProgram().getValidUntil())
                .isValid(valid)
                .build();
    }
}
