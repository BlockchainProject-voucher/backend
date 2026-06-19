package com.voucher.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 온체인 vs DB 정합성 검증 결과를 기록한다.
 * status: VERIFIED / MISMATCH / MISSING_DB / MISSING_ONCHAIN
 * details: JSON 덤프 (검증 시점의 상세 비교 결과)
 */
@Entity
@Table(name = "verification_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VerificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_id", nullable = false)
    private Long tokenId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "checked_at", updatable = false)
    private LocalDateTime checkedAt;
}
