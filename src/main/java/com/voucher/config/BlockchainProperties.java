package com.voucher.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "blockchain")
public class BlockchainProperties {
    private String rpcUrl;
    private String privateKey;
    private String contractAddress;
}
