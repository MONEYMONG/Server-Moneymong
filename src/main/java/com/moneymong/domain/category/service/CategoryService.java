package com.moneymong.domain.category.service;

import com.moneymong.domain.category.api.response.CategoryResponse;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    
    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse createCategory(String name, Long agencyId) {
        Category category = Category.of(name, agencyId);
        Category savedCategory = categoryRepository.save(category);
        
        return CategoryResponse.of(savedCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByAgencyId(Long agencyId) {
        List<Category> categories = categoryRepository.findByAgencyId(agencyId);
        return categories.stream()
                .map(CategoryResponse::of)
                .toList();
    }
}
