package com.voucher.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigInteger;
import java.util.Map;

@Getter
@Builder
public class UseVoucherPrepareResponse {

    private Long historyId;
    private String metadataHash;
    private BigInteger nonce;
    private long deadline;
    private Map<String, Object> eip712; // MetaMask eth_signTypedData_v4 용 구조화 데이터
}
