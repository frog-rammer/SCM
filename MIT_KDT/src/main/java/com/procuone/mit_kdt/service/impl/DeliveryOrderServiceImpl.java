package com.procuone.mit_kdt.service.impl;

import com.procuone.mit_kdt.dto.CompanyInventoryDTO;
import com.procuone.mit_kdt.dto.CompanyItemDTO;
import com.procuone.mit_kdt.dto.DeliveryOrderDTO;
import com.procuone.mit_kdt.dto.InspectionDTO;
import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;
import com.procuone.mit_kdt.entity.BOM.Item;
import com.procuone.mit_kdt.entity.DeliveryOrder;
import com.procuone.mit_kdt.entity.PurchaseOrder;
import com.procuone.mit_kdt.repository.DeliveryOrderRepository;
import com.procuone.mit_kdt.repository.PurchaseOrderRepository;
import com.procuone.mit_kdt.service.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DeliveryOrderServiceImpl implements DeliveryOrderService {

    @Autowired
    CompanyItemService companyItemService;

    @Autowired
    ItemService itemService;
    @Autowired
    CompanyInventoryService companyInventoryService;

    @Autowired
    PurchaseOrderRepository purchaseOrderRepository;

    @Autowired
    DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    InspectionService inspectionService;
    @Override
    public DeliveryOrderDTO registerDeliveryOrder(DeliveryOrderDTO deliveryOrderDTO) {
        // 1. 품목 ID 가져오기
        Long itemId = itemService.getItemIdByProductCode(deliveryOrderDTO.getProductCode());

        // 2. CompanyItemDTO 조회
        CompanyItemDTO companyItemDTO = companyItemService.getCompanyItemByBussinessIdAnditemId(deliveryOrderDTO.getBusinessId(), itemId);
        if (companyItemDTO == null) {
            throw new IllegalArgumentException("해당 품목과 사업자 ID로 CompanyItem을 찾을 수 없습니다.");
        }

        // 3. 납품 수량에 따른 납품일 계산 (납품 수량 / 최소 공급 단위)
        int supplyUnit = companyItemDTO.getSupplyUnit() != null ? companyItemDTO.getSupplyUnit() : 1; // 기본값 1
        int additionalDays = (int) Math.ceil((double) deliveryOrderDTO.getDeliveryQuantity() / supplyUnit); // 납품 수량 / 공급 단위
        LocalDate deliveryDate = LocalDate.now().plusDays(additionalDays); // 현재 날짜 + 추가 공급일

        // 4. 가격 설정 (단가 * 납품 수량)
        long unitCost = companyItemDTO.getUnitCost() != null ? companyItemDTO.getUnitCost() : 0L;
        long price = unitCost * deliveryOrderDTO.getDeliveryQuantity();

        // 5. 납품 상태 설정 ("운송중") 및 DTO 세팅
        deliveryOrderDTO.setStatus("운송중");
        deliveryOrderDTO.setDeliveryDate(deliveryDate);
        deliveryOrderDTO.setPrice(price);

        // 6. PurchaseOrder 조회
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(deliveryOrderDTO.getPurchaseOrderCode())
                .orElseThrow(() -> new IllegalArgumentException("해당 발주서 코드를 찾을 수 없습니다."));

        // 7. DTO → 엔티티 변환
        DeliveryOrder deliveryOrder = convertDTOToEntity(deliveryOrderDTO, purchaseOrder);

        // 8. 저장
        DeliveryOrder savedOrder = deliveryOrderRepository.save(deliveryOrder);

        // 9. CompanyInventory 차감
        CompanyInventoryDTO companyInventoryDTO = companyInventoryService.getInventoryByBusinessIdAndItemId(deliveryOrderDTO.getBusinessId(), itemId);
        if (companyInventoryDTO == null) {
            throw new IllegalArgumentException("해당 사업자 ID와 품목 ID로 재고 정보를 찾을 수 없습니다.");
        }

        int currentQuantity = companyInventoryDTO.getCurrentQuantity();
        int deliveryQuantity = deliveryOrderDTO.getDeliveryQuantity().intValue();

        if (currentQuantity < deliveryQuantity) {
            throw new IllegalArgumentException("현재 재고보다 요청된 납품 수량이 많습니다.");
        }

        companyInventoryDTO.setCurrentQuantity(currentQuantity - deliveryQuantity);
        companyInventoryService.saveOrUpdateInventory(companyInventoryDTO);

        // 10. 저장된 엔티티를 DTO로 변환 후 반환
        return convertEntityToDTO(savedOrder);
    }


    @Override
    public List<DeliveryOrder> findCompletedOrders() {
        return deliveryOrderRepository.findByStatus("완료");
    }

    /**
     * 매일 자정에 실행 (cron 표현식: "0 0 0 * * *")
     */
    @Override
    @Transactional
    public void updateDeliveryStatus() {
        LocalDate today = LocalDate.now();
        // 운송중 상태이고 배송 날짜가 오늘인 모든 DeliveryOrder를 조회
        // 테스트를 위해 주석처리함
//        List<DeliveryOrder> ordersToComplete = deliveryOrderRepository.findByStatusAndDeliveryDate("운송중", today);
        //
        List<DeliveryOrder> ordersToComplete = deliveryOrderRepository.findAll();
        // 상태를 "완료"로 변경
        InspectionDTO inspectionDTO = new InspectionDTO();

        for (DeliveryOrder order : ordersToComplete) {
            order.setStatus("완료");
            //입고 검수 테이블에 검수중으로 추가
            inspectionDTO.setDeliveryOrder(order);
            inspectionDTO.setQuantity(order.getDeliveryQuantity());
            inspectionDTO.setDefectiveQuantity(0L);
            Item item = itemService.getItemEntityByProductCode(order.getProductCode());
            inspectionDTO.setProductName(item.getItemName());
            inspectionService.saveInspection(inspectionDTO);
        }
        // 변경된 상태를 저장
        deliveryOrderRepository.saveAll(ordersToComplete);


        System.out.println(ordersToComplete.size() + "개의 배송 상태가 '완료'로 변경되었습니다.");
    }

    // 변환 메서드
    @Override
    public DeliveryOrderDTO convertEntityToDTO(DeliveryOrder deliveryOrder) {
        return DeliveryOrderDTO.builder()
                .deliveryCode(deliveryOrder.getDeliveryCode()) // 납품 지시 코드
                .purchaseOrderCode(deliveryOrder.getPurchaseOrder().getPurchaseOrderCode()) // 발주서 코드
                .businessId(deliveryOrder.getBusinessId()) // 사업자 ID
                .productCode(deliveryOrder.getProductCode()) // 품목 코드
                .deliveryQuantity(deliveryOrder.getDeliveryQuantity()) // 납품 수량
                .deliveryDate(deliveryOrder.getDeliveryDate()) // 납품일
                .status(deliveryOrder.getStatus()) // 납품 상태
                .price(deliveryOrder.getPrice())
                .createdDate(deliveryOrder.getCreatedDate()) // 생성일
                .build();
    }

    @Override
    public DeliveryOrder convertDTOToEntity(DeliveryOrderDTO dto, PurchaseOrder purchaseOrder) {
        return DeliveryOrder.builder()
                .deliveryCode(dto.getDeliveryCode()) // 납품 지시 코드
                .purchaseOrder(purchaseOrder) // 연관된 발주서 엔티티
                .businessId(dto.getBusinessId()) // 사업자 ID
                .productCode(dto.getProductCode()) // 품목 코드
                .deliveryQuantity(dto.getDeliveryQuantity()) // 납품 수량
                .deliveryDate(dto.getDeliveryDate()) // 납품일
                .price(dto.getPrice())
                .status(dto.getStatus()) // 납품 상태
                .createdDate(dto.getCreatedDate() != null ? dto.getCreatedDate() : LocalDate.now()) // 생성일
                .build();
    }
}
