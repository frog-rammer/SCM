package com.procuone.mit_kdt.service.impl;

import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;
import com.procuone.mit_kdt.dto.ProcumentPlanDTO;
import com.procuone.mit_kdt.dto.ShipmentDTO;
import com.procuone.mit_kdt.entity.BOM.BOMRelationship;
import com.procuone.mit_kdt.entity.Inventory;
import com.procuone.mit_kdt.entity.Shipment;
import com.procuone.mit_kdt.repository.BOMRelationshipRepository;
import com.procuone.mit_kdt.repository.InventoryRepository;
import com.procuone.mit_kdt.repository.ShipmentRepository;
import com.procuone.mit_kdt.service.ItemService;
import com.procuone.mit_kdt.service.MaterialIssueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MaterialIssueServiceImpl implements MaterialIssueService {

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ItemService itemService;
    @Autowired
    BOMRelationshipRepository bomRelationshipRepository;
    @Autowired
    InventoryRepository inventoryRepository;


    // 저장 로직 구현
    @Override
    public void saveShipment(List<ShipmentDTO> shipments) {
        // DTO 리스트를 엔티티 리스트로 변환 후 저장
        List<Shipment> shipmentEntities = shipments.stream()
                .map(this::convertToEntity)
                .collect(Collectors.toList());

        shipmentRepository.saveAll(shipmentEntities);
    }
    // 발주서와 연동하여 Shipment 자동 생성
    @Override
    public void createAndSaveShipmentsFromProcurementPlan(ProcumentPlanDTO procurementPlanDTO) {
        // 1. 조달 계획 기반 하위 품목 가져오기
        List<BOMRelationship> childItems = bomRelationshipRepository.findChildItemsByParentProductCode(procurementPlanDTO.getProductCode());

        List<ShipmentDTO> shipmentList = new ArrayList<>(); // Shipment DTO 리스트 생성

        for (BOMRelationship relationship : childItems) {
            String childProductCode = relationship.getChildItem().getProductCode();
            long requiredQuantity = relationship.getQuantity() * procurementPlanDTO.getProcurementQuantity();

            // 현재 재고 수량 조회
            long availableInventory = getAvailableInventory(childProductCode);

            // 3. ShipmentDTO 생성
            ShipmentDTO shipmentDTO = ShipmentDTO.builder()
                    .requestDate(LocalDate.now())
                    .manager("SYSTEM") // 자동 생성 시 시스템 기본값
                    .procurementPlanCode(procurementPlanDTO.getProcurementPlanCode())
                    .productPlanCode(procurementPlanDTO.getProductPlanCode())
                    .productCode(childProductCode)
                    .itemName(relationship.getChildItem().getItemName())
                    .currentQuantity(availableInventory)
                    .requestedQuantity(requiredQuantity)
                    .registrationDate(LocalDate.now())
                    .shipmentStatus("대기") // 기본 상태
                    .build();

            shipmentList.add(shipmentDTO);
        }

        // 4. 생성된 ShipmentDTO 리스트 저장 호출
        saveShipment(shipmentList);
    }

    // 모든 출고 데이터 조회
    @Override
    public Page<ShipmentDTO> getAllShipments(Pageable pageable) {
        // 페이징된 엔티티를 DTO로 변환
        return shipmentRepository.findAll(pageable)
                .map(this::convertToDTO);
    }

    // 현재 재고 수량 업데이트 메서드
    public void updateInventoryFromShipments() {
        List<Shipment> shipments = shipmentRepository.findAll();

        for (Shipment shipment : shipments) {
            String productCode = shipment.getProductCode();
            long requestedQuantity = shipment.getRequestedQuantity();

            Optional<Inventory> inventoryOpt = inventoryRepository.findByItemId(
                    itemService.findByProductCode(productCode).map(ItemDTO::getId).orElse(null)
            );

            if (inventoryOpt.isPresent()) {
                Inventory inventory = inventoryOpt.get();
                long currentQuantity = inventory.getCurrentQuantity() != null ? inventory.getCurrentQuantity() : 0;

                if (currentQuantity >= requestedQuantity) {
                    inventory.setCurrentQuantity((int) (currentQuantity - requestedQuantity));
                    shipment.setCurrentQuantity(currentQuantity - requestedQuantity);
                    shipment.setShipmentStatus("출고 완료");
                } else {
                    shipment.setShipmentStatus("재고 부족");
                }

                inventoryRepository.save(inventory);
                shipmentRepository.save(shipment);
            } else {
                shipment.setShipmentStatus("재고 정보 없음");
                shipmentRepository.save(shipment);
            }
        }
    }

    // 현재 재고 수량 조회 헬퍼 메서드
    private long getAvailableInventory(String productCode) {
        return itemService.findByProductCode(productCode)
                .map(item -> inventoryRepository.findByItemId(item.getId())
                        .map(Inventory::getCurrentQuantity)
                        .orElse(0))
                .orElse(0);
    }

    // 엔티티 -> DTO 변환
    @Override
    public ShipmentDTO convertToDTO(Shipment shipment) {
        if (shipment == null) {
            return null;
        }

        return ShipmentDTO.builder()
                .shipmentId(shipment.getShipmentId())
                .requestDate(shipment.getRequestDate())
                .procurementPlanCode(shipment.getProcurementPlanCode())
                .manager(shipment.getManager())
                .productPlanCode(shipment.getProductPlanCode())
                .shipmentDate(shipment.getShipmentDate())
                .productCode(shipment.getProductCode())
                .itemName(shipment.getItemName())
                .currentQuantity(shipment.getCurrentQuantity())
                .requestedQuantity(shipment.getRequestedQuantity())
                .registrationDate(shipment.getRegistrationDate())
                .updateDate(shipment.getUpdateDate())
                .shipmentStatus(shipment.getShipmentStatus())
                .build();
    }

    // DTO -> 엔티티 변환
    @Override
    public Shipment convertToEntity(ShipmentDTO shipmentDTO) {
        if (shipmentDTO == null) {
            return null;
        }

        return Shipment.builder()
                .shipmentId(shipmentDTO.getShipmentId())
                .requestDate(shipmentDTO.getRequestDate())
                .procurementPlanCode(shipmentDTO.getProcurementPlanCode())
                .manager(shipmentDTO.getManager())
                .productPlanCode(shipmentDTO.getProductPlanCode())
                .shipmentDate(shipmentDTO.getShipmentDate())
                .productCode(shipmentDTO.getProductCode())
                .itemName(shipmentDTO.getItemName())
                .currentQuantity(shipmentDTO.getCurrentQuantity())
                .requestedQuantity(shipmentDTO.getRequestedQuantity())
                .registrationDate(shipmentDTO.getRegistrationDate())
                .updateDate(shipmentDTO.getUpdateDate())
                .shipmentStatus(shipmentDTO.getShipmentStatus())
                .build();
    }





}
