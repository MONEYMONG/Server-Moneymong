package com.moneymong.domain.category.api.request;

public record CreateCategoryRequest(
    Long agencyId,
    String name
) {
}
