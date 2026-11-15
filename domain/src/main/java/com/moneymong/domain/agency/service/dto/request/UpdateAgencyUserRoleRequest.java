package com.moneymong.domain.agency.service.dto.request;

import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class UpdateAgencyUserRoleRequest {
    @NotBlank
    private Long userId;

    @NotBlank
    private AgencyUserRole role;
}
