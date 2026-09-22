package com.api.quimia.domain.account.event;

import java.util.UUID;

public record UserRegisteredEvent(UUID userId, String email) {}
