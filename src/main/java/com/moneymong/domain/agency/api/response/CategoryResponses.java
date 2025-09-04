package com.moneymong.domain.agency.api.response;

import java.util.List;

public record CategoryResponses(
    Long agencyId,
    List<CategoryResponse> categories
) {
}
