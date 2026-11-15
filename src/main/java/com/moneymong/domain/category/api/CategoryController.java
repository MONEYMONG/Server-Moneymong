package com.moneymong.domain.category.api;

import com.moneymong.domain.category.api.request.CreateCategoryRequest;
import com.moneymong.domain.category.api.request.DeleteCategoryRequest;
import com.moneymong.domain.category.api.response.CategoryItemResponse;
import com.moneymong.domain.category.api.response.CategoryResponses;
import com.moneymong.domain.category.api.response.CreateCategoryResponse;
import com.moneymong.domain.category.service.CategoryService;
import com.moneymong.global.security.token.dto.jwt.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 기존 클라이언트 호환을 위해 URL과 Swagger tag는 소속 API와 동일하게 유지한다.
@Tag(name = "3. [소속]")
@RequestMapping("/api/v1/agencies/categories")
@RestController
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    public CreateCategoryResponse createCategory(@RequestBody CreateCategoryRequest request, @AuthenticationPrincipal JwtAuthentication user) {
        var categoryResponse = categoryService.createCategory(user.getId(), request.name(), request.agencyId());
        return new CreateCategoryResponse(categoryResponse.getAgencyId(), categoryResponse.getName());
    }

    @GetMapping
    public CategoryResponses getCategories(@RequestParam("agencyId") Long agencyId, @AuthenticationPrincipal JwtAuthentication user) {
        var categoryResponses = categoryService.getCategoriesByAgencyId(user.getId(), agencyId);

        List<CategoryItemResponse> agencyCategoryResponses = categoryResponses.stream()
                .map(response -> new CategoryItemResponse(response.getId(), response.getName()))
                .toList();

        return new CategoryResponses(agencyId, agencyCategoryResponses);
    }

    @Operation(summary = "카테고리 삭제 API")
    @DeleteMapping
    public void deleteCategory(
            @RequestBody @Valid DeleteCategoryRequest request,
            @AuthenticationPrincipal JwtAuthentication user
    ) {
        categoryService.deleteCategory(user.getId(), request.getCategoryId());
    }
}
