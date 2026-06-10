package com.voucher.domain;

import com.voucher.domain.enums.ProgramStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_program")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoucherProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private Member createdBy;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "max_value")
    private Long maxValue;

    @Column(name = "total_supply")
    private Integer totalSupply;

    @Column(length = 200)
    private String category;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    // 자격요건 — null이면 제한 없음
    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    // 콤마 구분 지역명 — null 또는 "" 이면 전국 허용
    @Column(name = "allowed_regions", length = 500)
    private String allowedRegions;

    @Column(name = "usage_guide", columnDefinition = "TEXT")
    private String usageGuide;

    @Column(name = "issuance_guide", columnDefinition = "TEXT")
    private String issuanceGuide;

    @Column(name = "refund_policy", columnDefinition = "TEXT")
    private String refundPolicy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProgramStatus status;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void update(String name, String description, Long maxValue, Integer totalSupply,
                       String category, LocalDateTime validFrom, LocalDateTime validUntil,
                       Integer minAge, Integer maxAge, String allowedRegions,
                       String usageGuide, String issuanceGuide, String refundPolicy) {
        this.name = name;
        this.description = description;
        this.maxValue = maxValue;
        this.totalSupply = totalSupply;
        this.category = category;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.minAge = minAge;
        this.maxAge = maxAge;
        this.allowedRegions = allowedRegions;
        this.usageGuide = usageGuide;
        this.issuanceGuide = issuanceGuide;
        this.refundPolicy = refundPolicy;
    }

    public void end() {
        this.status = ProgramStatus.INACTIVE;
    }
}
