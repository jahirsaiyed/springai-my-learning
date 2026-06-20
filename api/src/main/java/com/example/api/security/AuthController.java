package com.example.api.security;

import com.example.core.auth.AuthProvider;
import com.example.core.auth.User;
import com.example.core.auth.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final GitHubOAuthService gitHubOAuthService;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          GitHubOAuthService gitHubOAuthService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.gitHubOAuthService = gitHubOAuthService;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            return ResponseEntity.badRequest()
                .body(new AuthResponse(null, "Email already registered"));
        }

        User user = User.ofEmail(
            request.email(),
            request.name(),
            passwordEncoder.encode(request.password())
        );
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getEmail(), Map.of());
        return ResponseEntity.ok(new AuthResponse(token, null));
    }

    @PostMapping("/signin")
    public ResponseEntity<AuthResponse> signin(@Valid @RequestBody SigninRequest request) {
        return userRepository.findByEmail(request.email())
            .filter(user -> user.getPasswordHash() != null
                && passwordEncoder.matches(request.password(), user.getPasswordHash()))
            .map(user -> {
                String token = jwtService.generateToken(user.getId(), user.getEmail(), Map.of());
                return ResponseEntity.ok(new AuthResponse(token, null));
            })
            .orElse(ResponseEntity.status(401)
                .body(new AuthResponse(null, "Invalid credentials")));
    }

    @PostMapping("/github")
    public ResponseEntity<AuthResponse> githubAuth(@Valid @RequestBody GitHubAuthRequest request) {
        User user = gitHubOAuthService.authenticateWithCode(request.code());
        String token = jwtService.generateToken(user.getId(), user.getEmail(), Map.of());
        return ResponseEntity.ok(new AuthResponse(token, null));
    }

    public record SignupRequest(
        @NotBlank @Email String email,
        @NotBlank String name,
        @NotBlank @Size(min = 8) String password
    ) {}

    public record SigninRequest(
        @NotBlank @Email String email,
        @NotBlank String password
    ) {}

    public record GitHubAuthRequest(@NotBlank String code) {}

    public record AuthResponse(String token, String error) {}
}
