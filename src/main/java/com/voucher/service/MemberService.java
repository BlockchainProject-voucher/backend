package com.voucher.service;

import com.voucher.domain.Member;
import com.voucher.domain.enums.Role;
import com.voucher.dto.request.CreateMerchantRequest;
import com.voucher.dto.request.CreateUserRequest;
import com.voucher.dto.response.ApiResponse;
import com.voucher.dto.response.MemberResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;

    @Transactional
    public ApiResponse<MemberResponse> registerUser(CreateUserRequest request) {
        validateDuplicateWallet(request.getWalletAddress());
        Member member = Member.builder()
                .walletAddress(request.getWalletAddress())
                .nickname(request.getNickname())
                .role(Role.USER)
                .build();
        return ApiResponse.success(MemberResponse.from(memberRepository.save(member)));
    }

    @Transactional
    public ApiResponse<MemberResponse> registerMerchant(CreateMerchantRequest request) {
        validateDuplicateWallet(request.getWalletAddress());
        Member member = Member.builder()
                .walletAddress(request.getWalletAddress())
                .nickname(request.getNickname())
                .role(Role.MERCHANT)
                .category(request.getCategory())
                .build();
        return ApiResponse.success(MemberResponse.from(memberRepository.save(member)));
    }

    public ApiResponse<Boolean> checkWalletExists(String walletAddress) {
        return ApiResponse.success(memberRepository.existsByWalletAddress(walletAddress));
    }

    public ApiResponse<MemberResponse> getMemberByWallet(String walletAddress) {
        return ApiResponse.success(MemberResponse.from(findByWalletOrThrow(walletAddress)));
    }

    public Member findByWalletOrThrow(String walletAddress) {
        return memberRepository.findByWalletAddress(walletAddress)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateDuplicateWallet(String walletAddress) {
        if (memberRepository.existsByWalletAddress(walletAddress)) {
            throw new BusinessException(ErrorCode.WALLET_ALREADY_EXISTS);
        }
    }
}
