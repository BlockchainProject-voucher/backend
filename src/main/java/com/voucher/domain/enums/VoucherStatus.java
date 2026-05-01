package com.voucher.domain.enums;

public enum VoucherStatus {
    PENDING,    // 민팅 트랜잭션 전송 후 receipt 대기 중
    ACTIVE,     // 민팅 완료, 사용 가능
    USED_UP,    // 잔액 소진
    BURNED      // 소각
}
