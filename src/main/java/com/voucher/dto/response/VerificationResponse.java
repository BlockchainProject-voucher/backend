package com.voucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 토큰 무결성 검증 결과.
 *
 * 4가지 상태:
 * - VERIFIED         : DB와 온체인이 양쪽 다 존재하고 (잔액·hash·nonce) 모두 일치
 * - MISMATCH         : 양쪽 다 존재하지만 하나 이상 불일치 → 위변조 의심
 * - MISSING_DB       : 온체인엔 있는데 DB에 없음 → 백엔드 처리 누락 의심
 * - MISSING_ONCHAIN  : DB엔 있는데 온체인에 없음 → 컨트랙트 미반영 의심
 */
@Getter
@Builder
public class VerificationResponse {

    private Long tokenId;
    private String status;

    private Long dbValue;
    private Long onChainValue;
    private Boolean valueMatch;

    private List<String> dbHashes;
    private List<String> onChainHashes;
    private Boolean hashMatch;

    private Long dbNonce;
    private Long onChainNonce;
    private Boolean nonceMatch;

    private String ownerWallet;
    private String programName;
    private LocalDateTime checkedAt;
    private String message;
}
