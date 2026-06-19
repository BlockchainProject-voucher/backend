package com.voucher;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoucherApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoucherApplication.class, args);
    }
}
