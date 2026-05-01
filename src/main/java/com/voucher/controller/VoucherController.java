package com.voucher.controller;

import com.voucher.dto.request.CreateVoucherRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.VoucherResponse;
import com.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Voucher", description = "바우처 발급(민팅) 및 조회")
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "바우처 발급 (민팅)",
            description = """
                    DB 저장 → 블록체인 mint() 호출 (1초 폴링, 최대 40초) → 온체인 정보 업데이트 순으로 처리됩니다.
                    현재 BlockchainService는 더미 데이터를 반환합니다. 블록체인팀 컨트랙트 연동 후 실제 트랜잭션이 발생합니다.
                    """)
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<VoucherResponse> issueVoucher(@Valid @RequestBody CreateVoucherRequest request) {
        return voucherService.issueVoucher(request);
    }

    @Operation(summary = "내 바우처 목록 조회")
    @GetMapping("/my/{walletAddress}")
    public ApiResponse<List<VoucherResponse>> getMyVouchers(
            @Parameter(description = "0x 포함 42자 이더리움 주소", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @PathVariable String walletAddress) {
        return voucherService.getMyVouchers(walletAddress);
    }

    @Operation(summary = "바우처 상세 조회", description = "walletAddress가 소유자와 일치하지 않으면 403을 반환합니다.")
    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getVoucher(
            @Parameter(description = "바우처 DB ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "소유자 지갑 주소 (소유자 검증용)", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @RequestParam String walletAddress) {
        return voucherService.getVoucher(id, walletAddress);
    }
}
