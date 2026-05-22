package com.voucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.util.Map;

@Getter
@Builder
public class UseVoucherPrepareResponse {

    private Long historyId;
    private Long voucherId;       // executeUse 호출 시 경로 변수로 필요
    private Long amount;          // 결제 금액 (화면 표시용)
    private String merchantNickname; // 가맹점 닉네임 (화면 표시용)
    private String programName;   // 바우처 프로그램명 (화면 표시용)
    private String metadataHash;
    private BigInteger nonce;
    private long deadline;
    private Map<String, Object> eip712; // MetaMask eth_signTypedData_v4 용 구조화 데이터
}
