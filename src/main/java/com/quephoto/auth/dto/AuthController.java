package com.quephoto.auth.dto;

import com.quephoto.auth.AuthService;
import com.quephoto.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(
            @Valid @RequestBody AuthDtos.LoginRequest request) {
        return authService.login(request);
    }
}
