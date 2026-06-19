package com.voucher.repository;

import com.voucher.domain.VerificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VerificationLogRepository extends JpaRepository<VerificationLog, Long> {
    List<VerificationLog> findTop10ByTokenIdOrderByCheckedAtDesc(Long tokenId);
}
