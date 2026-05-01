package com.voucher.blockchain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MintResult {
    private Long tokenId;
    private String txHash;
    private Long blockNumber;
}
