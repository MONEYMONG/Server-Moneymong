package com.moneymong.domain.category.api.response;

import com.moneymong.domain.category.entity.Category;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class CategoryResponse {
    
    private Long id;
    private String name;
    private Long agencyId;

    public static CategoryResponse of(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .name(category.getName())
                .agencyId(category.getAgencyId())
                .build();
    }
}
