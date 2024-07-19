package com.moneymong.domain.agency.repository;

import java.util.List;

import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.enums.AgencyType;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgencyRepository extends JpaRepository<Agency, Long>, AgencyRepositoryCustom {
	List<Agency> findAgenciesByAgencyType(AgencyType agencyType);
}
