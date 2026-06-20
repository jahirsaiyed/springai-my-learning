package com.example.api.security;

import java.util.UUID;

public record AuthenticatedUser(UUID id, String email) {}
