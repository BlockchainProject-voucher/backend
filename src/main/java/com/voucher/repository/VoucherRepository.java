package com.voucher.repository;

import com.voucher.domain.Member;
import com.voucher.domain.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VoucherRepository extends JpaRepository<Voucher, Long> {
    List<Voucher> findAllByOwner(Member owner);
    Optional<Voucher> findByOnChainTokenId(Long onChainTokenId);
}
