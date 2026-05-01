package com.voucher.repository;

import com.voucher.domain.VoucherProgram;
import com.voucher.domain.enums.ProgramStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherProgramRepository extends JpaRepository<VoucherProgram, Long> {
    List<VoucherProgram> findAllByStatus(ProgramStatus status);
    boolean existsByName(String name);
}
