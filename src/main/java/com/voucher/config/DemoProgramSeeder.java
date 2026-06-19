package com.voucher.config;

import com.voucher.domain.Member;
import com.voucher.domain.VoucherProgram;
import com.voucher.domain.enums.ProgramStatus;
import com.voucher.domain.enums.Role;
import com.voucher.repository.MemberRepository;
import com.voucher.repository.VoucherProgramRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 데모용 더미 VoucherProgram 3개를 부팅 시점에 시드한다.
 * 모두 사용자 자격 미달 조건이라 신청 자체가 백엔드 단에서 거절되므로
 * 컨트랙트 createVoucherProgram 호출 없이 DB에만 넣어도 정합성이 깨지지 않는다.
 * name 기준 idempotent — 이미 있으면 건너뛴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoProgramSeeder implements CommandLineRunner {

    private final VoucherProgramRepository programRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Member admin = memberRepository.findFirstByRole(Role.ADMIN).orElse(null);
        if (admin == null) {
            log.info("[DemoProgramSeeder] ADMIN 멤버가 없어 더미 시드를 건너뜁니다. 어드민 회원가입 후 재기동하세요.");
            return;
        }

        LocalDateTime validFrom = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime validUntil = LocalDateTime.of(2026, 12, 31, 23, 59);

        List<VoucherProgram> seeds = List.of(
                VoucherProgram.builder()
                        .createdBy(admin)
                        .name("시니어 문화 바우처")
                        .maxValue(50000L)
                        .totalSupply(500)
                        .category("영화관")
                        .validFrom(validFrom)
                        .validUntil(validUntil)
                        .minAge(65)
                        .maxAge(null)
                        .allowedRegions(null)
                        .status(ProgramStatus.ACTIVE)
                        .build(),
                VoucherProgram.builder()
                        .createdBy(admin)
                        .name("청소년 알뜰 바우처")
                        .maxValue(20000L)
                        .totalSupply(1000)
                        .category("편의점")
                        .validFrom(validFrom)
                        .validUntil(validUntil)
                        .minAge(14)
                        .maxAge(18)
                        .allowedRegions(null)
                        .status(ProgramStatus.ACTIVE)
                        .build(),
                VoucherProgram.builder()
                        .createdBy(admin)
                        .name("부산 미식 탐방 바우처")
                        .maxValue(30000L)
                        .totalSupply(300)
                        .category("카페")
                        .validFrom(validFrom)
                        .validUntil(validUntil)
                        .minAge(20)
                        .maxAge(40)
                        .allowedRegions("부산")
                        .status(ProgramStatus.ACTIVE)
                        .build()
        );

        int created = 0;
        for (VoucherProgram seed : seeds) {
            if (programRepository.existsByName(seed.getName())) {
                continue;
            }
            programRepository.save(seed);
            created++;
        }
        log.info("[DemoProgramSeeder] 더미 프로그램 신규 추가: {}개 (이미 존재하던 건 스킵)", created);
    }
}
