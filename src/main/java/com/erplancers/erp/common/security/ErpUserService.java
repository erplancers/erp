package com.erplancers.erp.common.security;

import com.erplancers.erp.common.security.domain.ErpUser;
import com.erplancers.erp.common.security.repository.ErpUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class ErpUserService {
    private final ErpUserRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ErpUser create(String username, String rawPassword, String level, String... roles) {
        if (users.findByUsernameIgnoreCase(username).isPresent()) {
            throw new IllegalArgumentException("ERP username already exists: " + username);
        }
        ErpUser user = new ErpUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setLevel(level == null || level.isBlank() ? "USER" : level);
        user.getRoles().addAll(Arrays.asList(roles));
        return users.save(user);
    }
}
