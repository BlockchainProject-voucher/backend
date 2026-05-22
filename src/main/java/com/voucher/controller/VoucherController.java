package com.voucher.controller;

import com.voucher.dto.request.CreateVoucherRequest;
import com.voucher.dto.request.UseVoucherPrepareRequest;
import com.voucher.dto.request.UseVoucherRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.UseVoucherPrepareResponse;
import com.voucher.dto.response.VoucherQrResponse;
import com.voucher.dto.response.VoucherResponse;
import com.voucher.dto.response.VoucherUseHistoryResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import java.util.List;

@Tag(name = "Voucher", description = "바우처 발급(민팅) 및 조회")
@RestController
@RequestMapping("/api/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private final VoucherService voucherService;

    @Operation(summary = "바우처 발급 (민팅)",
            description = "DB 저장 → 블록체인 mintVoucher() 호출 (1초 폴링, 최대 40초) → 온체인 정보 업데이트 순으로 처리됩니다.")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<VoucherResponse> issueVoucher(
            @Valid @RequestBody CreateVoucherRequest request,
            Authentication authentication) {
        validateWalletOwnership(request.getWalletAddress(), authentication);
        return voucherService.issueVoucher(request);
    }

    @Operation(summary = "내 바우처 목록 조회")
    @GetMapping("/my/{walletAddress}")
    public ApiResponse<List<VoucherResponse>> getMyVouchers(
            @Parameter(description = "0x 포함 42자 이더리움 주소", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @PathVariable String walletAddress,
            Authentication authentication) {
        validateWalletOwnership(walletAddress, authentication);
        return voucherService.getMyVouchers(walletAddress);
    }

    @Operation(summary = "바우처 상세 조회", description = "walletAddress가 소유자와 일치하지 않으면 403을 반환합니다.")
    @GetMapping("/{id}")
    public ApiResponse<VoucherResponse> getVoucher(
            @Parameter(description = "바우처 DB ID", example = "1")
            @PathVariable Long id,
            @Parameter(description = "소유자 지갑 주소 (소유자 검증용)", example = "0xAbCd1234567890abcdef1234567890ABCDEF1234")
            @RequestParam String walletAddress,
            Authentication authentication) {
        validateWalletOwnership(walletAddress, authentication);
        return voucherService.getVoucher(id, walletAddress);
    }

    @Operation(summary = "바우처 QR 데이터 조회",
            description = "프론트엔드가 QR 이미지를 렌더링할 때 사용할 데이터를 반환합니다. voucherId와 ownerWallet이 QR에 담깁니다.")
    @GetMapping("/{id}/qr")
    public ApiResponse<VoucherQrResponse> getQrData(
            @PathVariable Long id,
            Authentication authentication) {
        String ownerWallet = (String) authentication.getPrincipal();
        return voucherService.getQrData(id, ownerWallet);
    }

    @Operation(summary = "대기 중인 결제 요청 목록",
            description = "가맹점이 QR 스캔 후 생성한 PENDING 상태의 결제 요청 목록입니다. 사용자가 서명할 EIP-712 데이터를 포함합니다.")
    @GetMapping("/pending-use")
    public ApiResponse<List<UseVoucherPrepareResponse>> getPendingUseRequests(Authentication authentication) {
        String ownerWallet = (String) authentication.getPrincipal();
        return voucherService.getPendingUseRequests(ownerWallet);
    }

    @Operation(summary = "바우처 사용 준비",
            description = "metadataHash와 EIP-712 서명 데이터를 반환합니다. 프론트엔드는 eip712 필드를 MetaMask eth_signTypedData_v4로 서명 후 /use 엔드포인트를 호출하세요.")
    @PostMapping("/{id}/use/prepare")
    public ApiResponse<UseVoucherPrepareResponse> prepareUse(
            @PathVariable Long id,
            @Valid @RequestBody UseVoucherPrepareRequest request,
            Authentication authentication) {
        String ownerWallet = (String) authentication.getPrincipal();
        return voucherService.prepareUse(id, request, ownerWallet);
    }

    @Operation(summary = "바우처 사용 실행",
            description = "MetaMask에서 받은 서명값으로 온체인 useVoucherByMerchant를 호출합니다.")
    @PostMapping("/{id}/use")
    public ApiResponse<VoucherUseHistoryResponse> executeUse(
            @PathVariable Long id,
            @Valid @RequestBody UseVoucherRequest request,
            Authentication authentication) {
        String ownerWallet = (String) authentication.getPrincipal();
        return voucherService.executeUse(id, request, ownerWallet);
    }

    private void validateWalletOwnership(String requestWallet, Authentication authentication) {
        String jwtWallet = (String) authentication.getPrincipal();
        if (!jwtWallet.equalsIgnoreCase(requestWallet)) {
            throw new BusinessException(ErrorCode.WALLET_MISMATCH);
        }
    }
}
