package com.api.quimia.domain.account.dto;

import java.util.UUID;

public record UserSummary(UUID id, String nome, String email) {}
