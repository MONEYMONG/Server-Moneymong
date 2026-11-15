package com.moneymong.domain.category.service;

import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.category.api.response.CategoryResponse;
import com.moneymong.domain.category.entity.Category;
import com.moneymong.domain.category.repository.CategoryRepository;
import com.moneymong.domain.ledger.entity.LedgerDetail;
import com.moneymong.domain.ledger.repository.LedgerDetailRepository;
import com.moneymong.global.exception.custom.InvalidAccessException;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {
    
    private final CategoryRepository categoryRepository;
    private final LedgerDetailRepository ledgerDetailRepository;
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

        // 해당 카테고리를 사용하는 LedgerDetail들의 category를 null로 설정
        List<LedgerDetail> ledgerDetailsWithCategory = ledgerDetailRepository.findAllByCategory(category);
        for (LedgerDetail ledgerDetail : ledgerDetailsWithCategory) {
            ledgerDetail.updateLedgerDetailInfo(
                    ledgerDetail.getStoreInfo(),
                    ledgerDetail.getAmount(),
                    ledgerDetail.getDescription(),
                    ledgerDetail.getPaymentDate(),
                    null // 카테고리를 null로 설정
            );
        }
        
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
