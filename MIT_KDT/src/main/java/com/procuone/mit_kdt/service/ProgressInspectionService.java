package com.procuone.mit_kdt.service;

import com.procuone.mit_kdt.dto.ProgressInspectionDTO;
import com.procuone.mit_kdt.entity.ProgressInspection;
import com.procuone.mit_kdt.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

public interface ProgressInspectionService {
    //진척검수 현황 페이지
    Page<ProgressInspectionDTO> searchProgressInspections(
            String productCodeQuery, String productNameQuery, String procurementPlanCodeQuery, LocalDate dateStart,LocalDate dateEnd, Pageable pageable);
    // 사업자번호로 가져오기
    public Page<ProgressInspectionDTO> getInspectionsByBusinessId(String businessId,Pageable pageable);
    // 검수현황 업데이트 
    void updateInspectedQuantity(String progressInspectionCode, Long newInspectionQuantity, String businessId);
    // 엔티티 < - > DTO 간 변환 메서드
    public ProgressInspection dtoToEntity(ProgressInspectionDTO dto, PurchaseOrder purchaseOrder);
    public ProgressInspectionDTO entityToDto(ProgressInspection entity);



}
