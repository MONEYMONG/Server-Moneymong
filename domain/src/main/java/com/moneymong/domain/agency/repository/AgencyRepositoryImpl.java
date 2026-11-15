package com.moneymong.domain.agency.repository;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.enums.AgencyType;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.function.Supplier;

import static com.moneymong.domain.agency.entity.QAgency.agency;
import static com.moneymong.domain.agency.entity.enums.AgencyType.GENERAL;

@Repository
@RequiredArgsConstructor
public class AgencyRepositoryImpl implements AgencyRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<Agency> findByUniversityNameByPaging(String universityName, Pageable pageable) {
        JPAQuery<Agency> query = queryFactory.selectFrom(agency)
                .where(eqUniversityName(universityName))
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset());

        List<Agency> result = query.fetch();
        JPAQuery<Agency> countQuery = getCountQuery(universityName);

        return PageableExecutionUtils.getPage(result, pageable, () -> countQuery.fetch().size());
    }

    @Override
    public Page<Agency> findByUniversityNameAndAgencyTypeByPaging(String universityName, AgencyType type,
        Pageable pageable) {
        JPAQuery<Agency> query = queryFactory.selectFrom(agency)
            .where(agency.agencyType.eq(type).or(eqUniversityName(universityName)))
            .limit(pageable.getPageSize())
            .offset(pageable.getOffset());

        List<Agency> result = query.fetch();
        JPAQuery<Agency> countQuery = getCountQuery(universityName, type);

        return PageableExecutionUtils.getPage(result, pageable, () -> countQuery.fetch().size());

    }

    @Override
    public List<Agency> findByKeyword(String university, String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            return List.of();
        }

        return queryFactory
                .selectFrom(agency)
                .distinct()
                .where(
                        agency.agencyType.eq(GENERAL)
                                .and(agency.agencyName.containsIgnoreCase(keyword))
                                .or(
                                        eqUniversityName(university)
                                                .and(agency.agencyName.containsIgnoreCase(keyword))
                                )
                )
                .fetch();
    }


    private JPAQuery<Agency> getCountQuery(String universityName) {
        return queryFactory.selectFrom(agency)
                .where(eqUniversityName(universityName));
    }

    private JPAQuery<Agency> getCountQuery(String universityName, AgencyType type) {
        return queryFactory.selectFrom(agency)
            .where(eqUniversityName(universityName).or(agency.agencyType.eq(type)));
    }

    private BooleanBuilder eqUniversityName(String universityName) {
        return nullSafeBooleanBuilder(() -> agency.universityName.eq(universityName));
    }

    private BooleanBuilder nullSafeBooleanBuilder(Supplier<BooleanExpression> supplier) {
        try {
            return new BooleanBuilder(supplier.get());
        } catch (IllegalArgumentException e) {
            return new BooleanBuilder();
        }
    }
}
