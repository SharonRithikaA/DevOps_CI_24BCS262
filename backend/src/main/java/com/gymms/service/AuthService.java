package com.gymms.service;

import com.gymms.dto.AuthDto;
import com.gymms.entity.AppUser;
import com.gymms.exception.InvalidCredentialsException;
import com.gymms.repository.AppUserRepository;
import com.gymms.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthService {

    private static final String BAD_CREDENTIALS = "Invalid username or password";

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(AppUserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        AppUser user = userRepository.findByUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new InvalidCredentialsException(BAD_CREDENTIALS));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException(BAD_CREDENTIALS);
        }
        return new AuthDto.LoginResponse(jwtService.issueToken(user), user.getUsername(), user.getFullName(),
                user.getRole().name());
    }

    @Transactional(readOnly = true)
    public AuthDto.UserResponse me(String username) {
        AppUser user = find(username);
        return new AuthDto.UserResponse(user.getUsername(), user.getFullName(), user.getRole().name());
    }

    public void changePassword(String username, AuthDto.ChangePasswordRequest request) {
        AppUser user = find(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException("New password must be different from the current password");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private AppUser find(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("User no longer exists"));
    }
}
