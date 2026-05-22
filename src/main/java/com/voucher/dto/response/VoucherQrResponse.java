package com.voucher.dto.response;

import com.voucher.domain.Voucher;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoucherQrResponse {

    private Long voucherId;
    private String ownerWallet;
    private Long onChainTokenId;
    private Long currentValue;
    private String programName;

    public static VoucherQrResponse from(Voucher voucher) {
        return VoucherQrResponse.builder()
                .voucherId(voucher.getId())
                .ownerWallet(voucher.getOwner().getWalletAddress())
                .onChainTokenId(voucher.getOnChainTokenId())
                .currentValue(voucher.getCurrentValue())
                .programName(voucher.getVoucherProgram().getName())
                .build();
    }
}
