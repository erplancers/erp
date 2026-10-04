package com.erplancers.erp.common.security.web;

import com.erplancers.erp.common.security.ErpUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class RegistrationController {

    private final ErpUserService users;

    @PostMapping("/register")
    public ResponseEntity<RegistrationResponse> register(
            @Valid @RequestBody RegistrationRequest request) {
        users.create(request.username().trim(), request.password(), "USER", "USER");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new RegistrationResponse(request.username().trim(), "Registration successful"));
    }

    public record RegistrationRequest(
            @NotBlank @Size(min = 3, max = 120) String username,
            @NotBlank @Size(min = 8, max = 128) String password) {}

    public record RegistrationResponse(String username, String message) {}
}
