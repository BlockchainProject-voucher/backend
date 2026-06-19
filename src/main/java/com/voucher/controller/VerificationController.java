package com.voucher.controller;

import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.VerificationResponse;
import com.voucher.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 무결성 검증 API.
 * GET /api/verify/{tokenId} — 한 토큰에 대해 DB와 온체인을 즉시 비교한 결과를 반환.
 */
@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @GetMapping("/{tokenId}")
    public ApiResponse<VerificationResponse> verify(@PathVariable Long tokenId) {
        return ApiResponse.success(verificationService.verify(tokenId));
    }
}
