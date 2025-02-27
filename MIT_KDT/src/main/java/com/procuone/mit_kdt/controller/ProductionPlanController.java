package com.procuone.mit_kdt.controller;

import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;
import com.procuone.mit_kdt.dto.ProductionPlanDTO;
import com.procuone.mit_kdt.service.ItemService;
import com.procuone.mit_kdt.service.ProductionPlanService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Controller
@RequestMapping("/productionPlan")
public class ProductionPlanController {

    @Autowired
    private ProductionPlanService productionPlanService;
    @Autowired
    private ItemService itemService;

    @GetMapping("/input")
    public String input(Model model) {
        model.addAttribute("productionPlanDTO", new ProductionPlanDTO());
        // 상위 아이템 리스트를 모델에 추가
        List<ItemDTO> topItems = itemService.getTopItems();
        model.addAttribute("topItems", topItems);

        return "production/productionPlanInput";  // 템플릿 이름
    }

    @GetMapping("/view")
    public String view(Model model, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("productPlanCode").descending());
        Page<ProductionPlanDTO> productionPlanPage = productionPlanService.getAllPlans(pageable);

        int startPage = Math.max(0, productionPlanPage.getNumber() - 2);
        int endPage = Math.min(startPage + 5, productionPlanPage.getTotalPages());

        model.addAttribute("productionPlanList", productionPlanPage.getContent()); // 생산 계획 목록
        model.addAttribute("currentPage", productionPlanPage.getNumber());  // 현재 페이지
        model.addAttribute("totalPages", productionPlanPage.getTotalPages());  // 총 페이지 수
        model.addAttribute("totalItems", productionPlanPage.getTotalElements()); // 전체 아이템 수
        model.addAttribute("startPage", startPage);  // 시작 페이지
        model.addAttribute("endPage", endPage);  // 끝 페이지

        return "production/productionPlanView";
    }
    @PostMapping("/save")
    public String planSave(@ModelAttribute("productionPlanDTO") ProductionPlanDTO productionPlanDTO,
                           BindingResult result, Model model,
                           @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        if (result.hasErrors()) {
            return "production/productionPlanInput";  // 유효성 검사 실패 시 입력 폼으로 돌아가기
        }

        // 엔티티 저장
        Optional<ItemDTO> itemDTO = itemService.findByProductCode(productionPlanDTO.getProductCode());
        itemDTO.ifPresent(item -> productionPlanDTO.setProductName(item.getItemName()));
        productionPlanService.savePlan(productionPlanDTO);  // DTO를 서비스에 전달
        // 페이지네이션 처리
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductionPlanDTO> productionPlanPage = productionPlanService.getAllPlans(pageable);

        // 성공 메시지 추가
        model.addAttribute("successMessage", "생산 계획이 성공적으로 저장되었습니다.");
        model.addAttribute("productionPlanList", productionPlanPage.getContent()); // 생산 계획 목록
        model.addAttribute("currentPage", productionPlanPage.getNumber());  // 현재 페이지
        model.addAttribute("totalPages", productionPlanPage.getTotalPages());  // 총 페이지 수
        model.addAttribute("totalItems", productionPlanPage.getTotalElements()); // 전체 아이템 수

        return "redirect:/productionPlan/input";  // 저장 후 "view" 페이지로 리다이렉트
    }
    @PatchMapping("/update")
    public String updatePlan(
            @RequestParam String productPlanCode,
            @RequestParam LocalDate planStartDate,
            @RequestParam LocalDate planEndDate,
            @RequestParam int quantity,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            RedirectAttributes redirectAttributes) {
        try {
            // 입력된 데이터 디버그용 출력
            System.out.println("Received productPlanCode: " + productPlanCode);
            System.out.println("Received planStartDate: " + planStartDate);
            System.out.println("Received planEndDate: " + planEndDate);
            System.out.println("Received quantity: " + quantity);

            // 기존 생산 계획 찾기
            ProductionPlanDTO existingPlan = productionPlanService.getPlanById(productPlanCode);

            if (existingPlan != null) {
                // 기존 데이터를 새로운 값으로 업데이트
                existingPlan.setPlanStartDate(planStartDate);
                existingPlan.setPlanEndDate(planEndDate);
                existingPlan.setQuantity(quantity);

                // 변경사항 저장
                productionPlanService.updateProductionPlan(existingPlan);

                redirectAttributes.addFlashAttribute("successMessage", "생산 계획이 성공적으로 업데이트되었습니다.");
            } else {
                // 기존 데이터가 없을 경우
                redirectAttributes.addFlashAttribute("errorMessage", "해당 생산 계획을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            // 예외 처리 및 오류 메시지 설정
            redirectAttributes.addFlashAttribute("errorMessage", "업데이트 중 오류가 발생했습니다: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/productionPlan/view"; // 업데이트 후 뷰로 리다이렉트
    }

    @PostMapping("/delete")
    public String deleteProductionPlan(
            @RequestParam("productPlanCode") String productPlanCode,
            RedirectAttributes redirectAttributes) {
        if (productPlanCode == null || productPlanCode.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "생산계획번호가 제공되지 않았습니다.");
            return "redirect:/productionPlan/view"; // 삭제 실패 시 목록 페이지로 리다이렉트
        }

        try {
            boolean isDeleted = productionPlanService.deleteProductionPlan(productPlanCode);
            if (isDeleted) {
                redirectAttributes.addFlashAttribute("successMessage", "생산 계획이 성공적으로 삭제되었습니다.");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "해당 생산 계획을 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "삭제 중 오류가 발생했습니다: " + e.getMessage());
        }

        return "redirect:/productionPlan/view"; // 삭제 후 목록 페이지로 리다이렉트
    }

    @PostMapping("/search")
    public String searchPlans(
            @RequestParam(required = false) String searchType, // 검색 기준
            @RequestParam(required = false) String searchKeyword, // 검색 키워드
            @RequestParam(required = false) String startDate, // 시작 날짜
            @RequestParam(required = false) String endDate, // 종료 날짜
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);

        // 서비스에서 검색 처리
        Page<ProductionPlanDTO> productionPlanPage = productionPlanService.searchPlans(
                searchType, searchKeyword, startDate, endDate, pageable);

        model.addAttribute("productionPlanList", productionPlanPage.getContent());
        model.addAttribute("currentPage", productionPlanPage.getNumber());
        model.addAttribute("totalPages", productionPlanPage.getTotalPages());
        model.addAttribute("totalItems", productionPlanPage.getTotalElements());
        model.addAttribute("searchType", searchType);
        model.addAttribute("searchKeyword", searchKeyword);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "production/productionPlanView"; // 결과를 보여줄 뷰
    }

    @PostMapping("/uploadExcel")
    @ResponseBody
    public ResponseEntity<?> uploadExcel(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file uploaded.");
        }
        try {
            productionPlanService.processExcelFile(file);
            return ResponseEntity.ok("Excel file uploaded and processed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to upload Excel file: " + e.getMessage());
        }
    }
}
