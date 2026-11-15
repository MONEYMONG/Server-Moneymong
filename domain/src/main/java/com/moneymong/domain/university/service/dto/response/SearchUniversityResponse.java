package com.moneymong.domain.university.service.dto.response;

import com.moneymong.domain.university.service.dto.response.UniversityResponse;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SearchUniversityResponse {
    private List<UniversityResponse> universities;
}
