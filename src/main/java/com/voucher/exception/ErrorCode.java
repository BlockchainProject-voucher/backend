package com.voucher.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Member
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 지갑 주소입니다."),
    NOT_ADMIN(HttpStatus.FORBIDDEN, "관리자 권한이 필요합니다."),
    NOT_MERCHANT(HttpStatus.BAD_REQUEST, "가맹점 회원이 아닙니다."),
    NOT_USER(HttpStatus.BAD_REQUEST, "일반 사용자 회원이 아닙니다."),

    // 바우처 자격요건
    ELIGIBILITY_AGE(HttpStatus.BAD_REQUEST, "나이 요건을 충족하지 않습니다."),
    ELIGIBILITY_REGION(HttpStatus.BAD_REQUEST, "지역 요건을 충족하지 않습니다."),
    ALREADY_ISSUED(HttpStatus.CONFLICT, "이미 발급받은 바우처 프로그램입니다."),

    // VoucherProgram
    VOUCHER_PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "바우처 프로그램을 찾을 수 없습니다."),
    VOUCHER_PROGRAM_NAME_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 프로그램 이름입니다."),
    VOUCHER_PROGRAM_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 바우처 프로그램입니다."),

    // Auth
    INVALID_SIGNATURE(HttpStatus.UNAUTHORIZED, "서명 검증에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // Voucher
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "바우처를 찾을 수 없습니다."),
    VOUCHER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 바우처에 접근 권한이 없습니다."),
    VOUCHER_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "사용 가능한 상태의 바우처가 아닙니다."),
    INSUFFICIENT_VOUCHER_VALUE(HttpStatus.BAD_REQUEST, "바우처 잔액이 부족합니다."),
    WALLET_MISMATCH(HttpStatus.FORBIDDEN, "요청 지갑 주소가 인증된 지갑과 일치하지 않습니다."),
    MINT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "바우처 민팅에 실패했습니다."),
    MINT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "블록체인 트랜잭션 응답 대기 시간이 초과되었습니다. (40초)"),
    USE_HISTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "바우처 사용 이력을 찾을 수 없습니다."),
    USE_ALREADY_PROCESSED(HttpStatus.BAD_REQUEST, "이미 처리된 결제 요청입니다."),
    USE_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "바우처 사용 처리에 실패했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
