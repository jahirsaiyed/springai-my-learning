package com.example.api.websocket;

import com.example.api.security.JwtService;
import com.example.core.tenant.TenantContext;
import com.example.core.tenant.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Intercepts WebSocket CONNECT frames to authenticate via JWT
 * and resolve the tenant from headers.
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthInterceptor.class);

    private final JwtService jwtService;
    private final TenantRepository tenantRepository;

    public WebSocketAuthInterceptor(JwtService jwtService, TenantRepository tenantRepository) {
        this.jwtService = jwtService;
        this.tenantRepository = tenantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
            message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = extractToken(accessor);
            if (token != null && jwtService.isTokenValid(token)) {
                UUID userId = jwtService.extractUserId(token);
                var claims = jwtService.parseToken(token);
                String email = claims.get("email", String.class);

                var auth = new UsernamePasswordAuthenticationToken(
                    new com.example.api.security.AuthenticatedUser(userId, email),
                    null, List.of()
                );
                accessor.setUser(auth);

                // Resolve tenant
                String tenantSlug = accessor.getFirstNativeHeader("X-Tenant-Slug");
                if (tenantSlug != null) {
                    tenantRepository.findBySlug(tenantSlug).ifPresent(TenantContext::set);
                }

                log.debug("WebSocket authenticated: userId={}, tenant={}", userId, tenantSlug);
            }
        }

        return message;
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        // Also check query parameter for SockJS fallback
        return accessor.getFirstNativeHeader("token");
    }
}
