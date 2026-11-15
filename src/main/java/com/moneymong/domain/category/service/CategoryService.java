package com.moneymong.domain.category.service;

import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.api.response.CategoryResponse;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.event.CategoryDeletedEvent;
import com.moneymong.domain.category.repository.CategoryRepository;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AgencyUserRepository agencyUserRepository;

    @Transactional
    public CategoryResponse createCategory(Long userId, String name, Long agencyId) {
        validateStaffUser(userId, agencyId);

        Category category = Category.of(name, agencyId);
        Category savedCategory = categoryRepository.save(category);
        
        return CategoryResponse.of(savedCategory);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByAgencyId(Long userId, Long agencyId) {
        validateActiveMember(userId, agencyId);

        List<Category> categories = categoryRepository.findByAgencyId(agencyId);
        return categories.stream()
                .map(CategoryResponse::of)
                .toList();
    }

    @Transactional
    public void deleteCategory(Long userId, Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.LEDGER_CATEGORY_NOT_FOUND));

        validateStaffUser(userId, category.getAgencyId());

        // 카테고리를 참조하는 장부 내역의 카테고리를 먼저 비운다. listener는 같은 transaction에서 동기로 실행된다.
        eventPublisher.publishEvent(new CategoryDeletedEvent(category));

        categoryRepository.delete(category);
    }

    private AgencyUser getAgencyUser(Long userId, Long agencyId) {
        return agencyUserRepository.findByUserIdAndAgencyId(userId, agencyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENCY_USER_NOT_FOUND));
    }

    private void validateStaffUser(Long userId, Long agencyId) {
        if (!AgencyUserRole.isStaffUser(getAgencyUser(userId, agencyId).getAgencyUserRole())) {
            throw new InvalidAccessException(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        }
    }

    private void validateActiveMember(Long userId, Long agencyId) {
        if (AgencyUserRole.isBlockedUser(getAgencyUser(userId, agencyId).getAgencyUserRole())) {
            throw new InvalidAccessException(ErrorCode.INVALID_AGENCY_USER_ACCESS);
        }
    }
}
