package com.trindadeeesx.stocksentry.web.controller;

import com.trindadeeesx.stocksentry.application.user.UserService;
import com.trindadeeesx.stocksentry.web.dto.user.CreateUserRequest;
import com.trindadeeesx.stocksentry.web.dto.user.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@RequestBody @Valid CreateUserRequest request) {
        return userService.createOperator(request);
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.findAll();
    }
}
