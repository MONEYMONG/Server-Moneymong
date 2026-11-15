package com.moneymong.domain.category.api;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moneymong.domain.category.api.response.CategoryResponse;
import com.moneymong.domain.category.service.CategoryService;
import com.moneymong.support.ControllerTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.http.MediaType;

class CategoryControllerTest extends ControllerTestSupport {

    private static final Long AGENCY_ID = 10L;

    @Mock
    private CategoryService categoryService;

    @Override
    protected Object controller() {
        return new CategoryController(categoryService);
    }

    @Test
    @DisplayName("카테고리를 생성하면 소속 ID와 카테고리 이름을 반환한다.")
    void createCategory() throws Exception {
        given(categoryService.createCategory(USER_ID, "회식비", AGENCY_ID))
                .willReturn(new CategoryResponse(5L, "회식비", AGENCY_ID));

        mockMvc.perform(post("/api/v1/agencies/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("agencyId", AGENCY_ID, "name", "회식비"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyId").value(AGENCY_ID))
                .andExpect(jsonPath("$.name").value("회식비"));

        then(categoryService).should().createCategory(USER_ID, "회식비", AGENCY_ID);
    }

    @Test
    @DisplayName("소속의 카테고리 목록을 조회한다.")
    void getCategories() throws Exception {
        given(categoryService.getCategoriesByAgencyId(USER_ID, AGENCY_ID))
                .willReturn(List.of(
                        new CategoryResponse(5L, "회식비", AGENCY_ID),
                        new CategoryResponse(6L, "간식비", AGENCY_ID)
                ));

        mockMvc.perform(get("/api/v1/agencies/categories").param("agencyId", String.valueOf(AGENCY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agencyId").value(AGENCY_ID))
                .andExpect(jsonPath("$.categories.length()").value(2))
                .andExpect(jsonPath("$.categories[0].id").value(5))
                .andExpect(jsonPath("$.categories[1].name").value("간식비"));

        then(categoryService).should().getCategoriesByAgencyId(USER_ID, AGENCY_ID);
    }

    @Test
    @DisplayName("카테고리를 삭제한다.")
    void deleteCategory() throws Exception {
        mockMvc.perform(delete("/api/v1/agencies/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(toJson(Map.of("categoryId", 5))))
                .andExpect(status().isOk());

        then(categoryService).should().deleteCategory(USER_ID, 5L);
    }

    @Test
    @DisplayName("카테고리 ID 없이 삭제를 요청하면 400을 반환한다.")
    void deleteCategoryWithoutId() throws Exception {
        mockMvc.perform(delete("/api/v1/agencies/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GLOBAL-400"));

        then(categoryService).shouldHaveNoInteractions();
    }
}
