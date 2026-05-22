package com.voucher.service;

import com.voucher.domain.Member;
import com.voucher.domain.Voucher;
import com.voucher.domain.VoucherProgram;
import com.voucher.domain.VoucherUseHistory;
import com.voucher.domain.enums.VoucherStatus;
import com.voucher.dto.response.VoucherUseHistoryResponse;
import com.voucher.dto.response.VoucherResponse;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.MemberRepository;
import com.voucher.repository.VoucherProgramRepository;
import com.voucher.repository.VoucherRepository;
import com.voucher.repository.VoucherUseHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * issueVoucher 의 각 DB 쓰기를 독립 트랜잭션으로 커밋하는 헬퍼.
 * VoucherService 와 같은 클래스에 두면 self-invocation 으로 프록시가 적용되지 않으므로 별도 빈으로 분리.
 */
@Service
@RequiredArgsConstructor
class VoucherPersistenceService {

    private final VoucherRepository voucherRepository;
    private final MemberRepository memberRepository;
    private final VoucherProgramRepository voucherProgramRepository;
    private final VoucherUseHistoryRepository voucherUseHistoryRepository;

    /** ① PENDING 바우처 INSERT 후 tokenUri 설정 → 즉시 커밋 (voucherId 확보) */
    @Transactional
    public Voucher createPendingVoucher(Long ownerId, Long programId, String baseUrl) {
        Member owner = memberRepository.findById(ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        VoucherProgram program = voucherProgramRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_PROGRAM_NOT_FOUND));

        Voucher voucher = Voucher.builder()
                .owner(owner)
                .voucherProgram(program)
                .currentValue(program.getMaxValue())
                .initialValue(program.getMaxValue())
                .status(VoucherStatus.PENDING)
                .build();
        voucherRepository.save(voucher);
        voucher.updateTokenUri(baseUrl + "/api/metadata/" + voucher.getId());
        return voucher; // 트랜잭션 커밋 후 반환
    }

    /**
     * ③ txHash 만 별도 트랜잭션으로 커밋.
     * 이 메서드가 반환된 시점에 txHash 가 DB 에 영구 저장되므로
     * 이후 Receipt 폴링이 타임아웃되더라도 txHash 로 온체인 상태 확인 가능.
     */
    @Transactional
    public void persistTxHash(Long voucherId, String txHash) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.setPendingTx(txHash);
    }

    /** ⑤⑥ tokenId + blockNumber + status=ACTIVE 업데이트 → 즉시 커밋 */
    @Transactional
    public VoucherResponse confirmMinting(Long voucherId, Long tokenId, String txHash, Long blockNumber) {
        Voucher voucher = voucherRepository.findById(voucherId)
                .orElseThrow(() -> new BusinessException(ErrorCode.VOUCHER_NOT_FOUND));
        voucher.confirmMinting(tokenId, txHash, blockNumber);
        return VoucherResponse.from(voucher);
    }

    /** 바우처 사용 확정: 이력 CONFIRMED 처리 + 바우처 currentValue 차감 → 즉시 커밋 */
    @Transactional
    public VoucherUseHistoryResponse confirmUse(Long historyId, String txHash, Long blockNumber) {
        VoucherUseHistory history = voucherUseHistoryRepository.findById(historyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USE_HISTORY_NOT_FOUND));
        history.confirm(txHash, blockNumber);

        Voucher voucher = history.getVoucher();
        voucher.use(history.getAmount());

        return VoucherUseHistoryResponse.from(history);
    }
}
