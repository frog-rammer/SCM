package com.procuone.mit_kdt.controller;

import com.procuone.mit_kdt.dto.MemberDTO;
import com.procuone.mit_kdt.service.CompanyService;
import com.procuone.mit_kdt.service.MemberService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
public class MemberController {

    @Autowired
    MemberService memberService;
    @Autowired
    CompanyService companyService;

    @GetMapping({"/", "/login"})
    public String login(Model model) {
        model.addAttribute("memberDTO", new MemberDTO());
        return "login";
    }

    @GetMapping("/logout")
    public String logout(HttpServletRequest request) {
        request.getSession().invalidate();
        return "redirect:/";
    }

    @PostMapping("/login")
    public String loginService(@ModelAttribute MemberDTO memberDTO, HttpSession session) {
        // 로그인 시도
        String ok = memberService.login(memberDTO.getMemberId(), memberDTO.getPassword());

        if (ok.equals("success")) {
            // 로그인 성공 후, 사용자 정보와 타입을 세션에 저장
            String userType = memberService.getUserType(memberDTO.getMemberId()); // 회원의 타입을 서비스에서 가져옴
            if(userType.equals("협력업체")){
                String businessId = companyService.getCompanyBusinessIdByAccount(memberDTO.getMemberId());
                if(businessId != null){
                    session.setAttribute("businessId", businessId);
                }else{
                    System.out.println("Business ID not found!");
                }
            }
            session.setAttribute("username", memberDTO.getMemberId()); // 사용자 ID 세션에 저장
            session.setAttribute("userType", userType); // 사용자 타입 세션에 저장

            // 세션 값 확인을 위한 출력
            System.out.println("UserType: " + session.getAttribute("userType"));
            System.out.println("Username: " + session.getAttribute("username"));

            return "redirect:/Home"; // 홈 페이지로 리다이렉트
        } else {
            return "redirect:/login?error=true"; // 로그인 페이지로 다시 리다이렉트
        }
    }
    @GetMapping("/compSignup")
    public String compSignupPage() {
        return "compSignup";  // 'compSignup.html'을 반환
    }
    @PostMapping("/signup")
    public String signup(MemberDTO dto, RedirectAttributes redirectAttributes) {
        String memberId = memberService.signup(dto);
        redirectAttributes.addFlashAttribute("memberDTO", dto);
        return "redirect:/login";
    }

    @GetMapping("/check-id")
    public ResponseEntity<Map<String, Boolean>> checkId(@RequestParam String memberId) {
        // memberService에서 중복 아이디 확인
        boolean exists = memberService.isMemberIdExists(memberId);
        Map<String, Boolean> response = new HashMap<>();
        response.put("exists", exists);
        return ResponseEntity.ok(response);
    }

    // 회원가입 페이지 요청 처리
    @GetMapping("/signup")
    public String signupForm(Model model) {
        model.addAttribute("memberDTO", new MemberDTO());  // 빈 MemberDTO 객체를 뷰에 전달
        return "signup";  // 회원가입 폼을 렌더링
    }
}