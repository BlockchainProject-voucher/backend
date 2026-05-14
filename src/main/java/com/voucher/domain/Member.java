package com.voucher.domain;

import com.voucher.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, unique = true, length = 42)
    private String walletAddress;

    @Column(nullable = false, length = 50)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Role role;

    // MERCHANT일 때만 값 있음, USER/ADMIN은 null
    @Column(nullable = true)
    private String category;

    @Column(length = 36)
    private String nonce;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void updateNonce(String nonce) {
        this.nonce = nonce;
    }
}
