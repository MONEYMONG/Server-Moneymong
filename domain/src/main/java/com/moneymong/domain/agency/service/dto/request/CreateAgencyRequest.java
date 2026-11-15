package com.moneymong.domain.agency.service.dto.request;

import com.moneymong.domain.agency.entity.enums.AgencyType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateAgencyRequest {
    private String name;
    private AgencyType agencyType;
}
