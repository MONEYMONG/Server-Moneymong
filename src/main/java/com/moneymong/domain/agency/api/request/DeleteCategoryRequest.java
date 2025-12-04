package com.moneymong.domain.agency.api.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DeleteCategoryRequest {
    
    @NotNull(message = "카테고리 ID를 입력해주세요.")
    private Long categoryId;
}
