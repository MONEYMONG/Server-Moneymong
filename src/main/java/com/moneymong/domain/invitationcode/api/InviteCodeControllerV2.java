package com.moneymong.domain.invitationcode.api;

import com.moneymong.domain.invitationcode.api.request.CertifyInvitationCodeRequest;
import com.moneymong.domain.invitationcode.api.response.CertifyInvitationCodeResponse;
import com.moneymong.domain.invitationcode.service.InvitationCodeService;
import com.moneymong.global.security.token.dto.jwt.JwtAuthentication;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "4. [초대코드]")
@RequestMapping("/api/v2/agencies/invitation-code")
@RequiredArgsConstructor
@RestController
public class InviteCodeControllerV2 {

    private final InvitationCodeService invitationCodeService;

    @Operation(summary = "초대코드 인증 V2")
    @PostMapping
    public CertifyInvitationCodeResponse certify(
            @AuthenticationPrincipal JwtAuthentication user,
            @RequestBody @Valid CertifyInvitationCodeRequest request
    ) {
        return invitationCodeService.certifyV2(request, user.getId());
    }
}
