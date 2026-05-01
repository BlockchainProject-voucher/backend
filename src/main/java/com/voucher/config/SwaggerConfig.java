package com.voucher.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ERC-721 Voucher System API")
                        .description("""
                                MetaMask 지갑 주소 기반 ERC-721 바우처 발급 시스템 API입니다.

                                **인증 방식**: JWT 없음 — 요청 body 또는 path/query parameter의 `walletAddress`로 신원 확인

                                **블록체인**: Ganache 로컬 (http://localhost:7545) 연동, 민팅 시 트랜잭션 발생
                                """)
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("Voucher Dev Team")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Dev Server")
                ))
                .tags(List.of(
                        new Tag().name("Member").description("회원 관리 (지갑 주소 기반)"),
                        new Tag().name("Voucher Program").description("바우처 프로그램 관리 (ADMIN 전용 생성)"),
                        new Tag().name("Voucher").description("바우처 발급(민팅) 및 조회"),
                        new Tag().name("Metadata").description("ERC-721 토큰 메타데이터 (온체인 tokenURI 응답)")
                ));
    }
}
