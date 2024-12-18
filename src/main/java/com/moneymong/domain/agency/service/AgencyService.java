package com.moneymong.domain.agency.service;

import com.moneymong.domain.agency.api.request.CreateAgencyRequest;
import com.moneymong.domain.agency.api.response.*;
import com.moneymong.domain.agency.entity.Agency;
import com.moneymong.domain.agency.entity.AgencyUser;
import com.moneymong.domain.agency.entity.enums.AgencyUserRole;
import com.moneymong.domain.agency.exception.BlockedAgencyUserException;
import com.moneymong.domain.agency.repository.AgencyRepository;
import com.moneymong.domain.agency.repository.AgencyUserRepository;
import com.moneymong.domain.invitationcode.entity.CertificationStatus;
import com.moneymong.domain.invitationcode.entity.InvitationCode;
import com.moneymong.domain.invitationcode.entity.InvitationCodeCertification;
import com.moneymong.domain.invitationcode.exception.CertificationNotExistException;
import com.moneymong.domain.invitationcode.repository.InvitationCodeCertificationRepository;
import com.moneymong.domain.invitationcode.repository.InvitationCodeRepository;
import com.moneymong.domain.ledger.entity.Ledger;
import com.moneymong.domain.ledger.repository.LedgerRepository;
import com.moneymong.domain.ledger.service.manager.LedgerService;
import com.moneymong.domain.user.entity.UserUniversity;
import com.moneymong.domain.user.repository.UserUniversityRepository;
import com.moneymong.global.exception.custom.NotFoundException;
import com.moneymong.global.exception.enums.ErrorCode;
import com.moneymong.utils.RandomCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.moneymong.domain.agency.entity.enums.AgencyType.GENERAL;
import static com.moneymong.domain.agency.entity.enums.AgencyUserRole.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgencyService {

    private static final int INITIAL_HEAD_COUNT = 1;
    private final AgencyUserRepository agencyUserRepository;
    private final AgencyRepository agencyRepository;
    private final UserUniversityRepository userUniversityRepository;
    private final LedgerRepository ledgerRepository;
    private final InvitationCodeRepository invitationCodeRepository;
    private final InvitationCodeCertificationRepository invitationCodeCertificationRepository;
    private final LedgerService ledgerService;

    public SearchAgencyResponse getAgencyList(Long userId, Pageable pageable) {
        String universityName = getUniversityName(userId);

        Page<Agency> findByUniversityNameResult = agencyRepository.findByUniversityNameAndAgencyTypeByPaging(
            universityName,
            GENERAL,
            pageable);

        long totalCount = findByUniversityNameResult.getTotalElements();

        List<AgencyResponse> responses = findByUniversityNameResult.stream()
                .map(AgencyResponse::from)
                .toList();

        return new SearchAgencyResponse(responses, totalCount);
    }

    @Transactional
    public CreateAgencyResponse create(Long userId, CreateAgencyRequest request) {
        String universityName = getUniversityName(userId);

        Agency agency = Agency.of(
                request.getName(),
                request.getAgencyType(),
                INITIAL_HEAD_COUNT,
                universityName
        );

        AgencyUser agencyUser = AgencyUser.of(agency, userId, AgencyUserRole.STAFF);

        agency.addAgencyUser(agencyUser);

        agencyRepository.save(agency);

        // === 장부 ===
        Ledger ledger = Ledger.of(agency, 0);
        ledgerRepository.save(ledger);

        invitationCodeRepository.save(InvitationCode.of(agency.getId(), RandomCodeGenerator.generateCode()));
        invitationCodeCertificationRepository.save(InvitationCodeCertification.of(userId, agency.getId(), CertificationStatus.DONE));

        return new CreateAgencyResponse(agency.getId());
    }

    @Transactional
    public List<AgencyResponse> search(Long userId, String keyword) {
        String university = getUniversityName(userId);
        List<Agency> agencies = agencyRepository.findByKeyword(university, keyword);

        return agencies.stream()
                .map(AgencyResponse::from)
                .toList();
    }

    private String getUniversityName(Long userId) {
        return userUniversityRepository.findByUserId(userId)
            .map(UserUniversity::getUniversityName)
            .orElse(null);
    }

    public AgencyUserResponses getAgencyUserList(Long userId, Long agencyId) {
        AgencyUserRole agencyUserRole = validateAgencyUserRole(userId, agencyId);

        validateCertificationExists(userId, agencyId, agencyUserRole);

        List<AgencyUserResponse> agencyUsers = agencyUserRepository.findByAgencyId(agencyId);

        return AgencyUserResponses.from(agencyUsers);
    }

    private AgencyUserRole validateAgencyUserRole(Long userId, Long agencyId) {
        AgencyUser agencyUser = agencyUserRepository.findByUserIdAndAgencyId(userId, agencyId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.AGENCY_USER_NOT_FOUND));

        AgencyUserRole role = agencyUser.getAgencyUserRole();

        if (isBlockedUser(role)) {
            throw new BlockedAgencyUserException(ErrorCode.BLOCKED_AGENCY_USER);
        }

        return role;
    }

    private void validateCertificationExists(Long userId, Long agencyId, AgencyUserRole agencyUserRole) {
        if (isMemberUser(agencyUserRole)) {
            invitationCodeCertificationRepository.findByUserIdAndAgencyId(userId, agencyId)
                    .orElseThrow(() -> new CertificationNotExistException(ErrorCode.INVITATION_CODE_NOT_CERTIFIED_USER));
        }
    }

    public List<AgencyResponse> getMyAgency(Long userId) {
        List<Agency> agencyList = agencyUserRepository.findAgencyListByUserId(userId);

        return agencyList.stream()
                .map(AgencyResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long userId, Long agencyId) {
        validateAgencyUserRole(userId, agencyId);

        Agency agency = agencyRepository.findById(agencyId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.AGENCY_NOT_FOUND));

        // ledgerService.deleteLedger(agencyId);

        agencyRepository.delete(agency);
    }
}
