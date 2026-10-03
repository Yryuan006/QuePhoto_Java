package com.quephoto.auth;

import com.quephoto.auth.dto.AuthDtos.LoginRequest;
import com.quephoto.auth.dto.AuthDtos.LoginResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

@Service
public class AuthService {
    private final AdminProperties adminProperties;
    private final JwtProperties jwtProperties;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final LoginRateLimiter loginRateLimiter;

    public AuthService(
            AdminProperties adminProperties,
            JwtProperties jwtProperties,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            LoginRateLimiter loginRateLimiter
    ) {
        this.adminProperties = adminProperties;
        this.jwtProperties = jwtProperties;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.loginRateLimiter = loginRateLimiter;
    }

    public LoginResponse login(LoginRequest request) {
        String username = request.username();
        String password = request.password();

        // 1. 检查输入格式。
        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "用户名和密码不能为空");
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "密码不能超过72字节");
        }

        loginRateLimiter.checkAndRecord();
        // 2. 无论用户名是否正确，都执行一次密码核验。
        boolean passwordMatches = passwordEncoder.matches(
                password, adminProperties.passwordHash());

        boolean usernameMatches =
                adminProperties.username().equals(username);

        if (!usernameMatches || !passwordMatches) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        // 3. 核验通过，构造令牌声明。
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(3600);

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject("admin")
                .audience(List.of(jwtProperties.audience()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("scope", "admin")
                .build();

        // 4. 固定使用 HS256 签名。
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .build();

        String accessToken = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();

        return new LoginResponse(accessToken, expiresAt);
    }
}