package com.moneymong.domain.category.api.response;

public record CreateCategoryResponse(
    Long agencyId,
    String name
) {
}
