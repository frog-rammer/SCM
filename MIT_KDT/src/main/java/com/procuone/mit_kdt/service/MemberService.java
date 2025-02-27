package com.procuone.mit_kdt.service;

import com.procuone.mit_kdt.dto.MemberDTO;
import com.procuone.mit_kdt.entity.Member;

import java.util.Optional;

public interface MemberService {

    public String login(String username, String password);

    String signup(MemberDTO memberDTO);

    boolean isMemberIdExists(String memberId);  // 아이디 중복 체크 메서드 추가

    String getUserType(String memberId);

    Optional<Member> getMember(String memberId);
}
