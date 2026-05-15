package com.voucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class CreateVoucherProgramRequest {

    @NotBlank(message = "요청자 지갑 주소는 필수입니다.")
    private String walletAddress;

    @NotBlank(message = "프로그램 이름은 필수입니다.")
    private String name;

    private String description;

    @NotNull(message = "최대 금액은 필수입니다.")
    @Positive(message = "최대 금액은 양수여야 합니다.")
    private Long maxValue;

    @NotNull(message = "총 발행량은 필수입니다.")
    @Positive(message = "총 발행량은 양수여야 합니다.")
    private Integer totalSupply;

    @NotBlank(message = "카테고리는 필수입니다.")
    private String category;

    @NotNull(message = "유효 시작일은 필수입니다.")
    private LocalDateTime validFrom;

    @NotNull(message = "유효 종료일은 필수입니다.")
    private LocalDateTime validUntil;
}
