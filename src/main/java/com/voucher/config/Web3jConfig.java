package com.voucher.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

@Configuration
@RequiredArgsConstructor
public class Web3jConfig {

    private final BlockchainProperties blockchainProperties;

    @Bean
    public Web3j web3j() {
        return Web3j.build(new HttpService(blockchainProperties.getRpcUrl()));
    }
}
