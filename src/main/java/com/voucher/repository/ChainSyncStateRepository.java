package com.voucher.repository;

import com.voucher.domain.ChainSyncState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChainSyncStateRepository extends JpaRepository<ChainSyncState, Long> {
    Optional<ChainSyncState> findByScannerName(String scannerName);
}
