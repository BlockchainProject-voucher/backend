package com.voucher.controller;

import com.voucher.dto.request.CreateMerchantRequest;
import com.voucher.dto.request.CreateUserRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.MemberResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.service.MemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Member", description = "회원 관리 (지갑 주소 기반)")
@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @Operation(summary = "일반 회원 가입", description = "role = USER로 등록됩니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/user")
    public ApiResponse<MemberResponse> registerUser(@Valid @RequestBody CreateUserRequest request) {
        return memberService.registerUser(request);
    }

    @Operation(summary = "가맹점 회원 가입", description = "role = MERCHANT로 등록됩니다. category 필수.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/merchant")
    public ApiResponse<MemberResponse> registerMerchant(@Valid @RequestBody CreateMerchantRequest request) {
        return memberService.registerMerchant(request);
    }

    @Operation(summary = "가맹점 온체인 승인/취소 [ADMIN]",
            description = "approved=true: 승인, approved=false: 취소. 온체인 approveMerchant() 호출.")
    @PostMapping("/merchant/{walletAddress}/approve")
    public ApiResponse<MemberResponse> approveMerchant(
            @Parameter(description = "가맹점 지갑 주소") @PathVariable String walletAddress,
            @Parameter(description = "승인 여부") @RequestParam boolean approved,
            Authentication authentication) {
        String requesterWallet = (String) authentication.getPrincipal();
        return memberService.approveMerchant(requesterWallet, walletAddress, approved);
    }

    @Operation(summary = "지갑 주소 존재 여부 확인",
            description = "data: true → 로그인, false → 회원가입 화면으로 분기")
    @GetMapping("/check/{walletAddress}")
    public ApiResponse<Boolean> checkWallet(
            @Parameter(description = "0x 포함 42자 이더리움 주소", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @PathVariable String walletAddress) {
        return memberService.checkWalletExists(walletAddress);
    }

    @Operation(summary = "회원 정보 조회")
    @GetMapping("/{walletAddress}")
    public ApiResponse<MemberResponse> getMember(
            @Parameter(description = "0x 포함 42자 이더리움 주소", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @PathVariable String walletAddress) {
        return memberService.getMemberByWallet(walletAddress);
    }
}
