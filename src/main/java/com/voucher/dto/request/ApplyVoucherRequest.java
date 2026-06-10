package com.voucher.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApplyVoucherRequest {

    @NotNull(message = "바우처 프로그램 ID는 필수입니다.")
    private Long voucherProgramId;

    @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "올바른 이더리움 지갑 주소 형식이 아닙니다.")
    private String walletAddress;
}
