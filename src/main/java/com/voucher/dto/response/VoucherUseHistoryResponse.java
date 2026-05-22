package com.voucher.dto.response;

import com.voucher.domain.VoucherUseHistory;
import com.voucher.domain.enums.UseStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class VoucherUseHistoryResponse {

    private Long id;
    private Long voucherId;
    private Long onChainTokenId;
    private String merchantWallet;
    private Long amount;
    private Long oldValue;
    private Long newValue;
    private String metadataHash;
    private String txHash;
    private Long blockNumber;
    private UseStatus status;
    private LocalDateTime usedAt;

    public static VoucherUseHistoryResponse from(VoucherUseHistory h) {
        return VoucherUseHistoryResponse.builder()
                .id(h.getId())
                .voucherId(h.getVoucher().getId())
                .onChainTokenId(h.getVoucher().getOnChainTokenId())
                .merchantWallet(h.getMerchant().getWalletAddress())
                .amount(h.getAmount())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .metadataHash(h.getMetadataHash())
                .txHash(h.getTxHash())
                .blockNumber(h.getBlockNumber())
                .status(h.getStatus())
                .usedAt(h.getUsedAt())
                .build();
    }
}
