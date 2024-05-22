package com.bada.badaback.member.service;

import com.bada.badaback.auth.service.AuthCodeService;
import com.bada.badaback.auth.service.AuthService;
import com.bada.badaback.auth.service.TokenService;
import com.bada.badaback.family.domain.Family;
import com.bada.badaback.family.service.FamilyFindService;
import com.bada.badaback.family.service.FamilyService;
import com.bada.badaback.file.service.FileService;
import com.bada.badaback.global.exception.BaseException;
import com.bada.badaback.member.domain.Member;
import com.bada.badaback.member.domain.MemberRepository;
import com.bada.badaback.member.dto.MemberDetailResponseDto;
import com.bada.badaback.member.exception.MemberErrorCode;
import com.bada.badaback.state.domain.StateRepository;
import com.bada.badaback.state.service.StateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MemberService {
    private final MemberFindService memberFindService;
    private final FileService fileService;
    private final AuthService authService;
    private final TokenService tokenService;
    private final AuthCodeService authCodeService;
    private final StateService stateService;
    private final MemberRepository memberRepository;
    private final FamilyService familyService;
    private final StateRepository stateRepository;
    private final FamilyFindService familyFindService;

    @Transactional
    public MemberDetailResponseDto read(Long memberId) {
        Member findMember = memberFindService.findById(memberId);
        Family findFamily = familyFindService.findByFamilyCode(findMember.getFamilyCode());
        return MemberDetailResponseDto.from(findMember, findFamily.getFamilyName());
    }

    @Transactional
    public void update(Long memberId, String childId, String name, MultipartFile file) {
        Member updateMember = memberFindService.findById(memberId);

        // updateMember 아이로 변경
        if(!childId.isEmpty()) {
            Member findChild = memberFindService.findById(Long.valueOf(childId));
            checkParent(updateMember.getFamilyCode(), findChild.getFamilyCode());

            updateMember = findChild;
        }

        String profileUrl = updateMember.getProfileUrl();
        // 프로필 수정
        if (file != null) {
            if(profileUrl != null)
                fileService.deleteFiles(updateMember.getProfileUrl());
            profileUrl = fileService.uploadMemberFiles(file);
        }

        updateMember.updateMember(name, profileUrl);
    }

    @Transactional
    public void delete(Long memberId) {
        Member findMember = memberFindService.findById(memberId);

        authService.logout(findMember.getId());

        if(findMember.getProfileUrl() != null){
            fileService.deleteFiles(findMember.getProfileUrl());
        }
        tokenService.deleteRefreshTokenByMemberId(memberId);
        authCodeService.delete(memberId);

        if(stateRepository.existsByMember(findMember)){
            stateService.deleteState(memberId);
        }

        // 마지막 가족이 탈퇴하면 그 가족 제거
        if(memberRepository.familyList(findMember.getFamilyCode()).size() == 1){
            familyService.delete(memberId);
        }

        memberRepository.delete(findMember);
    }

    @Transactional
    public void updateMovingState(Long memberId, int movingState) {
        Member findMember = memberFindService.findById(memberId);
        findMember.updateMovingState(movingState);
    }

    private void checkParent (String memberFamilyCode, String childFamilyCode) {
        if(!memberFamilyCode.equals(childFamilyCode))
            throw BaseException.type(MemberErrorCode.MEMBER_IS_NOT_CHILD_PARENT);
    }
}
