package com.moneymong.domain.agency.api.request;

public record CreateCategoryRequest(
    Long agencyId,
    String name
) {
}
