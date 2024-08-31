package com.moneymong.domain.user.api.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserUniversityRequest {
    private String universityName;
    private int grade;
}
