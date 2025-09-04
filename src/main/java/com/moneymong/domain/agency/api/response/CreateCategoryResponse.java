package com.moneymong.domain.agency.api.response;

public record CreateCategoryResponse(
    Long agencyId,
    String name
) {
}
