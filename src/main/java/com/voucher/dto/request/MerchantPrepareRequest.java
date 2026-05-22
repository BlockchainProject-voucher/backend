package com.voucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class MerchantPrepareRequest {

    @NotNull(message = "바우처 ID는 필수입니다.")
    private Long voucherId;

    @NotBlank(message = "바우처 소유자 지갑 주소는 필수입니다.")
    private String ownerWallet;

    @NotNull(message = "결제 금액은 필수입니다.")
    @Positive(message = "결제 금액은 양수여야 합니다.")
    private Long amount;
}
