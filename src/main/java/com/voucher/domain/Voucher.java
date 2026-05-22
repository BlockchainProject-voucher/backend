package com.voucher.domain;

import com.voucher.domain.enums.VoucherStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "voucher")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Voucher {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "on_chain_token_id", unique = true)
    private Long onChainTokenId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "voucher_program_id", nullable = false)
    private VoucherProgram voucherProgram;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private Member owner;

    @Column(name = "current_value")
    private Long currentValue;

    @Column(name = "initial_value")
    private Long initialValue;

    @Column(name = "token_uri", length = 500)
    private String tokenUri;

    @Column(name = "tx_hash", length = 66)
    private String txHash;

    @Column(name = "block_number")
    private Long blockNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VoucherStatus status;

    @Column(name = "minted_at")
    private LocalDateTime mintedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void updateTokenUri(String tokenUri) {
        this.tokenUri = tokenUri;
    }

    public void use(Long amount) {
        this.currentValue -= amount;
        if (this.currentValue <= 0) {
            this.currentValue = 0L;
            this.status = VoucherStatus.USED_UP;
        }
    }

    // 트랜잭션 전송 직후 호출 — txHash를 즉시 저장해 타임아웃 시 복구 가능하게 함
    public void setPendingTx(String txHash) {
        this.txHash = txHash;
    }

    // Receipt 수신 후 호출 — 온체인 정보 전체 업데이트 및 ACTIVE 전환
    public void confirmMinting(Long onChainTokenId, String txHash, Long blockNumber) {
        this.onChainTokenId = onChainTokenId;
        this.txHash = txHash;
        this.blockNumber = blockNumber;
        this.mintedAt = LocalDateTime.now();
        this.status = VoucherStatus.ACTIVE;
    }
}
