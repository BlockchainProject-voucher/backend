package com.voucher.domain.enums;

public enum UseStatus {
    PENDING,    // 서명 대기 중
    CONFIRMED,  // 온체인 확정
    FAILED      // 처리 실패 / 만료
}
