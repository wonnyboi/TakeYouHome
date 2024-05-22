package com.bada.badaback.member.dto;

import com.bada.badaback.member.domain.Member;
import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record MemberDetailResponseDto(
        Long memberId,
        String name,
        String phone,
        String email,
        String social,
        String profileUrl,
        LocalDateTime createdAt,
        String familyCode,
        String familyName,
        int movingState,
        String fcmToken

) {
    public static MemberDetailResponseDto from(Member findMember, String familyName) {
        return MemberDetailResponseDto.builder()
                .memberId(findMember.getId())
                .name(findMember.getName())
                .phone(findMember.getPhone())
                .email(findMember.getEmail())
                .social(findMember.getSocial().getSocialType())
                .profileUrl(findMember.getProfileUrl())
                .familyCode(findMember.getFamilyCode())
                .familyName(familyName)
                .createdAt(findMember.getCreatedAt())
                .movingState(findMember.getMovingState())
                .fcmToken(findMember.getFcmToken())
                .build();
    }
}
