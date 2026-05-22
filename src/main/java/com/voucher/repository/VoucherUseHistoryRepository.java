package com.voucher.repository;

import com.voucher.domain.VoucherUseHistory;
import com.voucher.domain.enums.UseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherUseHistoryRepository extends JpaRepository<VoucherUseHistory, Long> {
    List<VoucherUseHistory> findAllByVoucherId(Long voucherId);
    List<VoucherUseHistory> findAllByVoucher_Owner_WalletAddressIgnoreCaseAndStatusOrderByIdDesc(
            String walletAddress, UseStatus status);
}
