package com.voucher.controller;

import com.voucher.dto.request.VerifyRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Auth", description = "MetaMask 서명 기반 로그인")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
        summary = "Nonce 발급",
        description = "로그인 전 지갑 주소로 일회용 nonce를 발급받습니다. MetaMask 서명 메시지에 포함시켜야 합니다."
    )
    @GetMapping("/nonce/{walletAddress}")
    public ApiResponse<String> getNonce(@PathVariable String walletAddress) {
        return ApiResponse.success(authService.issueNonce(walletAddress));
    }

    @Operation(
        summary = "서명 검증 및 JWT 발급",
        description = "MetaMask로 서명한 signature를 검증하고 JWT를 반환합니다. 이후 모든 요청에 'Authorization: Bearer <JWT>' 헤더를 포함하세요."
    )
    @PostMapping("/verify")
    public ApiResponse<String> verify(@Valid @RequestBody VerifyRequest request) {
        return ApiResponse.success(authService.verify(request.getWalletAddress(), request.getSignature()));
    }
}
