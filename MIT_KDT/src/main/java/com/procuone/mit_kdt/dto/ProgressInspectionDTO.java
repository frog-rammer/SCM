package com.procuone.mit_kdt.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressInspectionDTO {
    private String progressInspectionCode; // 검수 코드
    private String purchaseOrderCode;      // 발주서 코드
    private String productCode;            // 품목 코드
    private String productName;            // 품목명
    private LocalDate expectedArrivalDate;  // 리드타임
    private Long totalQuantity;            // 발주 수량
    private Long inspectedQuantity;        // 검수된 수량
    private String inspectionStatus;       // 검수 상태
    private LocalDate inspectionDate;      // 검수 일자
    private LocalDate orderDate;           // 발주 일자
}
