package com.voucher.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 이벤트 스캐너의 마지막 처리 블록 번호를 저장한다.
 * scannerName으로 여러 스캐너(EVENT_SCANNER, VERIFICATION 등)를 구분할 수 있음.
 */
@Entity
@Table(name = "chain_sync_state")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ChainSyncState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scanner_name", nullable = false, unique = true, length = 50)
    private String scannerName;

    @Column(name = "last_processed_block", nullable = false)
    private Long lastProcessedBlock;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 스캔 한 사이클 끝나면 호출 — 마지막 처리 블록 + 갱신 시각 업데이트 */
    public void update(Long newBlock) {
        this.lastProcessedBlock = newBlock;
        this.updatedAt = LocalDateTime.now();
    }
}
