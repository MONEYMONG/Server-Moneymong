package com.moneymong.domain.agency.repository;

import com.moneymong.domain.agency.repository.dto.AgencyUserResponse;
import com.moneymong.domain.agency.entity.Agency;

import java.util.List;

public interface AgencyUserRepositoryCustom {
    List<AgencyUserResponse> findByAgencyId(Long agencyId);

    List<Agency> findAgencyListByUserId(Long userId);
}
