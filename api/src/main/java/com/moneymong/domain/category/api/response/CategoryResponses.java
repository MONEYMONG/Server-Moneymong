package com.moneymong.domain.category.api.response;

import java.util.List;

public record CategoryResponses(
    Long agencyId,
    List<CategoryItemResponse> categories
) {
}
