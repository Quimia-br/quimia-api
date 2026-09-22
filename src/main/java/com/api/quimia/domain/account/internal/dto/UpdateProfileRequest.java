package com.api.quimia.domain.account.internal.dto;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record UpdateProfileRequest(@Size(min = 2, max = 255) String nome, @Past LocalDate dataNasc) {}
