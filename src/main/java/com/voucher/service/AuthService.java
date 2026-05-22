package com.voucher.service;

import com.voucher.auth.JwtUtil;
import com.voucher.domain.Member;
import com.voucher.exception.BusinessException;
import com.voucher.exception.ErrorCode;
import com.voucher.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final MemberRepository memberRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public String issueNonce(String walletAddress) {
        Member member = memberRepository.findByWalletAddress(walletAddress.toLowerCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));
        String nonce = UUID.randomUUID().toString();
        member.updateNonce(nonce);
        return nonce;
    }

    @Transactional
    public String verify(String walletAddress, String signature) {
        Member member = memberRepository.findByWalletAddress(walletAddress.toLowerCase())
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        String message = buildSignMessage(member.getNonce());
        String recovered = recoverAddress(message, signature);

        if (!recovered.equalsIgnoreCase(walletAddress)) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }

        // nonce 폐기 — 같은 signature 재사용 방지
        member.updateNonce(UUID.randomUUID().toString());

        return jwtUtil.generateToken(walletAddress, member.getRole().name());
    }

    // 프론트의 personal_sign 메시지와 반드시 동일해야 함
    public static String buildSignMessage(String nonce) {
        return "Voucher 서비스 로그인\nNonce: " + nonce;
    }

    private String recoverAddress(String message, String signature) {
        try {
            byte[] sigBytes = Numeric.hexStringToByteArray(signature);

            byte v = sigBytes[64];
            if (v < 27) v += 27;

            Sign.SignatureData sigData = new Sign.SignatureData(
                    v,
                    Arrays.copyOfRange(sigBytes, 0, 32),
                    Arrays.copyOfRange(sigBytes, 32, 64)
            );

            BigInteger publicKey = Sign.signedMessageToKey(
                    message.getBytes(StandardCharsets.UTF_8), sigData);
            return "0x" + Keys.getAddress(publicKey);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_SIGNATURE);
        }
    }
}
