package com.voucher.controller;

import com.voucher.domain.Voucher;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.MetadataResponse;
import com.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Metadata", description = "ERC-721 토큰 메타데이터 (온체인 tokenURI 응답)")
@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class MetadataController {

    private final VoucherService voucherService;

    @Operation(summary = "ERC-721 메타데이터 조회",
            description = "컨트랙트의 tokenURI(tokenId) 호출 URL이 이 엔드포인트를 가리킵니다. OpenSea 표준 포맷을 따릅니다.")
    @GetMapping("/{voucherId}")
    public ApiResponse<MetadataResponse> getMetadata(
            @Parameter(description = "DB 바우처 ID", example = "42")
            @PathVariable Long voucherId) {
        Voucher voucher = voucherService.findByIdOrThrow(voucherId);
        MetadataResponse metadata = MetadataResponse.builder()
                .name(voucher.getVoucherProgram().getName() + " #" + voucherId)
                .description(voucher.getVoucherProgram().getDescription())
                .image("")
                .attributes(List.of(
                        MetadataResponse.Attribute.builder()
                                .traitType("initial_value")
                                .value(voucher.getInitialValue())
                                .build(),
                        MetadataResponse.Attribute.builder()
                                .traitType("current_value")
                                .value(voucher.getCurrentValue())
                                .build(),
                        MetadataResponse.Attribute.builder()
                                .traitType("status")
                                .value(voucher.getStatus().name())
                                .build(),
                        MetadataResponse.Attribute.builder()
                                .traitType("valid_until")
                                .value(voucher.getVoucherProgram().getValidUntil().toString())
                                .build()
                ))
                .build();
        return ApiResponse.success(metadata);
    }
}
