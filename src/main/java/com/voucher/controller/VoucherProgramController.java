package com.voucher.controller;

import com.voucher.dto.request.CreateVoucherProgramRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.VoucherProgramResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.service.VoucherProgramService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Voucher Program", description = "바우처 프로그램 관리 (ADMIN 전용 생성)")
@RestController
@RequestMapping("/api/voucher-programs")
@RequiredArgsConstructor
public class VoucherProgramController {

    private final VoucherProgramService voucherProgramService;

    @Operation(summary = "바우처 프로그램 생성 [ADMIN]",
            description = "요청자의 walletAddress로 ADMIN 여부를 검증합니다. " +
                    "ADMIN 계정은 DB에서 직접 role = 'ADMIN'으로 설정")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<VoucherProgramResponse> createProgram(
            @Valid @RequestBody CreateVoucherProgramRequest request,
            Authentication authentication) {
        String jwtWallet = (String) authentication.getPrincipal();
        if (!jwtWallet.equalsIgnoreCase(request.getWalletAddress())) {
            throw new BusinessException(ErrorCode.WALLET_MISMATCH);
        }
        return voucherProgramService.createProgram(request);
    }

    @Operation(summary = "활성 프로그램 목록 조회", description = "status = ACTIVE인 프로그램 전체 목록을 반환합니다.")
    @GetMapping
    public ApiResponse<List<VoucherProgramResponse>> getActivePrograms() {
        return voucherProgramService.getActivePrograms();
    }

    @Operation(summary = "프로그램 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<VoucherProgramResponse> getProgram(
            @Parameter(description = "바우처 프로그램 ID", example = "1")
            @PathVariable Long id) {
        return voucherProgramService.getProgram(id);
    }
}
