package com.example.api.security;

import com.example.core.auth.AuthProvider;
import com.example.core.auth.User;
import com.example.core.auth.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GitHubOAuthService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOAuthService.class);

    private final String clientId;
    private final String clientSecret;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate;

    public GitHubOAuthService(
            @Value("${app.github.client-id}") String clientId,
            @Value("${app.github.client-secret}") String clientSecret,
            UserRepository userRepository) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.userRepository = userRepository;
        this.restTemplate = new RestTemplate();
    }

    public User authenticateWithCode(String code) {
        String accessToken = exchangeCodeForToken(code);
        Map<String, Object> githubUser = fetchGitHubUser(accessToken);

        String githubId = String.valueOf(githubUser.get("id"));
        String email = (String) githubUser.get("email");
        String name = (String) githubUser.get("name");
        String avatarUrl = (String) githubUser.get("avatar_url");

        if (email == null) {
            email = fetchPrimaryEmail(accessToken);
        }

        final String resolvedEmail = email;
        return userRepository.findByAuthProviderAndProviderId(AuthProvider.GITHUB, githubId)
            .orElseGet(() -> {
                User existing = resolvedEmail != null
                    ? userRepository.findByEmail(resolvedEmail).orElse(null)
                    : null;

                if (existing != null) {
                    return existing;
                }

                User newUser = new User(resolvedEmail, name, AuthProvider.GITHUB, githubId);
                newUser.setAvatarUrl(avatarUrl);
                return userRepository.save(newUser);
            });
    }

    @SuppressWarnings("unchecked")
    private String exchangeCodeForToken(String code) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        Map<String, String> body = Map.of(
            "client_id", clientId,
            "client_secret", clientSecret,
            "code", code
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            "https://github.com/login/oauth/access_token",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            Map.class
        );

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || responseBody.containsKey("error")) {
            throw new RuntimeException("GitHub OAuth token exchange failed");
        }

        return (String) responseBody.get("access_token");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchGitHubUser(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<Map> response = restTemplate.exchange(
            "https://api.github.com/user",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            Map.class
        );

        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private String fetchPrimaryEmail(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        ResponseEntity<java.util.List> response = restTemplate.exchange(
            "https://api.github.com/user/emails",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            java.util.List.class
        );

        if (response.getBody() != null) {
            for (Object item : response.getBody()) {
                Map<String, Object> emailEntry = (Map<String, Object>) item;
                if (Boolean.TRUE.equals(emailEntry.get("primary"))) {
                    return (String) emailEntry.get("email");
                }
            }
        }
        return null;
    }
}
