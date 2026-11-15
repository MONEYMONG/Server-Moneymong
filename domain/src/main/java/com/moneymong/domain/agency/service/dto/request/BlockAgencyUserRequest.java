package com.moneymong.domain.agency.service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class BlockAgencyUserRequest {

    @NotBlank
    private Long userId;
}
