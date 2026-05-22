package com.voucher.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UseVoucherRequest {

    @NotNull(message = "사용 이력 ID는 필수입니다.")
    private Long historyId;

    @NotBlank(message = "서명값은 필수입니다.")
    private String ownerSignature;
}
