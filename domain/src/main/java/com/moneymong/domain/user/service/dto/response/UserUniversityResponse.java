package com.moneymong.domain.user.service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserUniversityResponse {
    private String universityName;
    private Integer grade;

    public static UserUniversityResponse of(String universityName, Integer grade) {
        return UserUniversityResponse.builder()
                .universityName(universityName)
                .grade(grade)
                .build();
    }
}
