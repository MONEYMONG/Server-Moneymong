package com.moneymong.domain.agency.service.dto.response;

import com.moneymong.domain.agency.repository.dto.AgencyUserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class AgencyUserResponses {
    private List<AgencyUserResponse> agencyUsers;
    private int count;

    public static AgencyUserResponses from(List<AgencyUserResponse> agencyUsers) {
        return AgencyUserResponses.builder()
                .agencyUsers(agencyUsers)
                .count(agencyUsers.size())
                .build();
    }
}
