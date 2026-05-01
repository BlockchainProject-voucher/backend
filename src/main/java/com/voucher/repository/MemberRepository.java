package com.voucher.repository;

import com.voucher.domain.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByWalletAddress(String walletAddress);
    Optional<Member> findByWalletAddress(String walletAddress);
}
