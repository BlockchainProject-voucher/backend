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
    private String merchantNickname;
    private String programName;
    private Long amount;
    private Long oldValue;
    private Long newValue;
    private String metadataHash;
    private String txHash;
    private Long blockNumber;
    private UseStatus status;
    private Long deadline;
    private LocalDateTime usedAt;

    public static VoucherUseHistoryResponse from(VoucherUseHistory h) {
        return VoucherUseHistoryResponse.builder()
                .id(h.getId())
                .voucherId(h.getVoucher().getId())
                .onChainTokenId(h.getVoucher().getOnChainTokenId())
                .merchantWallet(h.getMerchant().getWalletAddress())
                .merchantNickname(h.getMerchant().getNickname())
                .programName(h.getVoucher().getVoucherProgram().getName())
                .amount(h.getAmount())
                .oldValue(h.getOldValue())
                .newValue(h.getNewValue())
                .metadataHash(h.getMetadataHash())
                .txHash(h.getTxHash())
                .blockNumber(h.getBlockNumber())
                .status(h.getStatus())
                .deadline(h.getDeadline())
                .usedAt(h.getUsedAt())
                .build();
    }
}
