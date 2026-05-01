package com.voucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CreateMerchantRequest {

    @NotBlank(message = "지갑 주소는 필수입니다.")
    @Pattern(regexp = "^0x[0-9a-fA-F]{40}$", message = "올바른 이더리움 지갑 주소 형식이 아닙니다.")
    private String walletAddress;

    @NotBlank(message = "닉네임은 필수입니다.")
    @Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
    private String nickname;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;
}
