package com.moneymong.domain.agency.repository.dto;

import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AgencyUserResponse {
    private Long id;
    private Long userId;
    private String nickname;
    private AgencyUserRole agencyUserRole;
}
