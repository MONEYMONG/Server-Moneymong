package com.moneymong.domain.invitationcode.api.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CertifyInvitationCodeResponse {
    private boolean certified;
    private long agencyId;

    public static CertifyInvitationCodeResponse from(boolean certified, long agencyId) {
        return CertifyInvitationCodeResponse.builder()
                .certified(certified)
                .agencyId(agencyId)
                .build();
    }
}
