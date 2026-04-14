package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.auth.AuthService;
import com.trindadeeesx.stocksentry.web.dto.AuthResponse;
import com.trindadeeesx.stocksentry.web.dto.LoginRequest;
import com.trindadeeesx.stocksentry.web.dto.RegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@RequestBody @Valid RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@RequestBody @Valid LoginRequest request) {
        return authService.login(request);
    }
}
