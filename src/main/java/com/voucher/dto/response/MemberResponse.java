package com.voucher.dto.response;

import com.voucher.domain.Member;
import com.voucher.domain.enums.Role;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberResponse {

    private Long id;
    private String walletAddress;
    private String nickname;
    private Role role;
    private String category;
    private LocalDateTime createdAt;

    public static MemberResponse from(Member member) {
        return MemberResponse.builder()
                .id(member.getId())
                .walletAddress(member.getWalletAddress())
                .nickname(member.getNickname())
                .role(member.getRole())
                .category(member.getCategory())
                .createdAt(member.getCreatedAt())
                .build();
    }
}
