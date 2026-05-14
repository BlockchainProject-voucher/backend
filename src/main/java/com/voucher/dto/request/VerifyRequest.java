package com.voucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VerifyRequest {

    @NotBlank(message = "walletAddress는 필수입니다.")
    private String walletAddress;

    @NotBlank(message = "signature는 필수입니다.")
    private String signature;
}
