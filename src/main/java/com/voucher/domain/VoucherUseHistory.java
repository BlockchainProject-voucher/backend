package com.voucher.domain;

import com.voucher.domain.enums.UseStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher_use_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class VoucherUseHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Member merchant;

    @Column(nullable = false)
    private Long amount;

    @Column(name = "old_value", nullable = false)
    private Long oldValue;

    @Column(name = "new_value", nullable = false)
    private Long newValue;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "metadata_hash", length = 66)
    private String metadataHash;

    @Column(name = "use_nonce")
    private Long useNonce;

    @Column(nullable = false)
    private Long deadline;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private UseStatus status;

    @CreationTimestamp
    @Column(name = "used_at", updatable = false)
    private LocalDateTime usedAt;

    public void setMetadataInfo(String metadataJson, String metadataHash) {
        this.metadataJson = metadataJson;
        this.metadataHash = metadataHash;
    }

    public void confirm(String txHash, Long blockNumber) {
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.status = UseStatus.CONFIRMED;
    }
}
