package com.voucher.controller;

import com.voucher.dto.request.MerchantPrepareRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.UseVoucherPrepareResponse;
import com.voucher.dto.response.VoucherUseHistoryResponse;
import com.voucher.service.VoucherService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Merchant", description = "가맹점 전용 API")
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final VoucherService voucherService;

    @Operation(summary = "바우처 사용 준비 (QR 스캔)",
            description = "가맹점이 사용자 QR을 스캔한 후 금액을 입력해 호출합니다. "
                    + "반환된 eip712 데이터를 사용자가 MetaMask로 서명 후 POST /api/vouchers/{id}/use를 호출하면 결제가 완료됩니다.")
    @PostMapping("/vouchers/use/prepare")
    public ApiResponse<UseVoucherPrepareResponse> prepareUse(
            @Valid @RequestBody MerchantPrepareRequest request,
            Authentication authentication) {
        String merchantWallet = (String) authentication.getPrincipal();
        return voucherService.merchantPrepareUse(request, merchantWallet);
    }

    @Operation(summary = "가맹점 결제 내역 조회")
    @GetMapping("/history")
    public ApiResponse<List<VoucherUseHistoryResponse>> getHistory(Authentication authentication) {
        String merchantWallet = (String) authentication.getPrincipal();
        return voucherService.getMerchantHistory(merchantWallet);
    }

    @Operation(summary = "결제 요청 상태 조회", description = "가맹점이 결제 완료 여부를 폴링할 때 사용합니다.")
    @GetMapping("/history/{historyId}/status")
    public ApiResponse<Map<String, String>> getPaymentStatus(
            @PathVariable Long historyId,
            Authentication authentication) {
        String merchantWallet = (String) authentication.getPrincipal();
        return voucherService.getPaymentStatus(historyId, merchantWallet);
    }
}
