package com.moneymong.domain.agency.service.dto.response;

import com.moneymong.domain.agency.service.dto.response.AgencyResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SearchAgencyResponse {
    private List<AgencyResponse> agencyList;
    private long totalCount;
}
