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

    // VoucherProgram
    VOUCHER_PROGRAM_NOT_FOUND(HttpStatus.NOT_FOUND, "바우처 프로그램을 찾을 수 없습니다."),
    VOUCHER_PROGRAM_NAME_DUPLICATE(HttpStatus.CONFLICT, "이미 존재하는 프로그램 이름입니다."),
    VOUCHER_PROGRAM_INACTIVE(HttpStatus.BAD_REQUEST, "비활성화된 바우처 프로그램입니다."),

    // Voucher
    VOUCHER_NOT_FOUND(HttpStatus.NOT_FOUND, "바우처를 찾을 수 없습니다."),
    VOUCHER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "해당 바우처에 접근 권한이 없습니다."),
    MINT_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "바우처 민팅에 실패했습니다."),
    MINT_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "블록체인 트랜잭션 응답 대기 시간이 초과되었습니다. (40초)");

    private final HttpStatus httpStatus;
    private final String message;
}
