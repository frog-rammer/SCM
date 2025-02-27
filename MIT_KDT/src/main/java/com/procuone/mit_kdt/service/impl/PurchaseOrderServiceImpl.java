package com.procuone.mit_kdt.service.impl;

import com.procuone.mit_kdt.dto.CompanyDTO;
import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;
import com.procuone.mit_kdt.dto.ProcumentPlanDTO;
import com.procuone.mit_kdt.dto.ProgressInspectionDTO;
import com.procuone.mit_kdt.dto.PurchaseOrderDTO;
import com.procuone.mit_kdt.entity.*;
import com.procuone.mit_kdt.entity.BOM.BOMRelationship;
import com.procuone.mit_kdt.entity.BOM.Item;
import com.procuone.mit_kdt.entity.BOM.PurchaseBOM;
import com.procuone.mit_kdt.repository.*;
import com.procuone.mit_kdt.service.CompanyService;
import com.procuone.mit_kdt.service.ItemService;
import com.procuone.mit_kdt.service.PurchaseOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PurchaseOrderServiceImpl implements PurchaseOrderService {
    @Autowired
    private PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    private BOMRelationshipRepository BOMRelationshipRepository;
    @Autowired
    private PurchaseBOMRepository purchaseBOMRepository;
    @Autowired
    private ItemService itemService;
    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private ProgressInspectionRepository progressInspectionRepository;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private CompanyService  companyService;
    @Autowired
    ContractRepository contractRepository;
    @Override
    public Page<PurchaseOrderDTO> getOrdersByStatus(String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        // 엔티티를 DTO로 변환하는 로직 포함
        Page<PurchaseOrderDTO> purchaseOrderDTOS = purchaseOrderRepository.findByStatus(status, pageable)
                .map(this::convertEntityToDTO);
        for(PurchaseOrderDTO purchaseOrderDTO : purchaseOrderDTOS.getContent()) {
            Optional<ItemDTO> itemDTO = itemService.findByProductCode(purchaseOrderDTO.getProductCode());
            itemDTO.ifPresent(dto -> purchaseOrderDTO.setItemName(dto.getItemName()));
            CompanyDTO companyDTO = companyService.getCompanyDetails(purchaseOrderDTO.getBusinessId());
            purchaseOrderDTO.setComName(companyDTO.getComName());
        }
        return purchaseOrderDTOS;
    }

    @Override
    public List<PurchaseOrderDTO> getCompletedOrders(String status) {
        List<PurchaseOrder> completedOrders = purchaseOrderRepository.findByStatus("발주완료");
        return completedOrders.stream()
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void completeOrders(List<String> orderIds) {
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllById(orderIds);
        for (PurchaseOrder order : orders) {
            // 상태 변경
            order.setStatus("발주완료");
            purchaseOrderRepository.save(order);
            // productCode를 기반으로 ItemDTO 조회
            Optional<ItemDTO> itemDTOOptional = itemService.findByProductCode(order.getProductCode());
            if (itemDTOOptional.isEmpty()) {
                throw new IllegalStateException("해당 productCode에 대한 Item을 찾을 수 없습니다: " + order.getProductCode());
            }
            // DTO -> Entity 변환
            Item item = dtoToEntity(itemDTOOptional.get());
//            // Inventory 업데이트 또는 삽입
//            Inventory inventory = inventoryRepository.findByItemId(item.getId())
//                    .orElseGet(() -> {
//                        Inventory newInventory = new Inventory();
//                        newInventory.setItem(item); // 연관된 Item 설정
//                        newInventory.setItemName(item.getItemName()); // 보조 필드
//                        newInventory.setCurrentQuantity(0);
//                        newInventory.setReservedQuantity(0);
//                        newInventory.setMinimumRequired(0);
//                        return newInventory;
//                    });
//            // 재고 업데이트
//            inventory.setReservedQuantity(inventory.getReservedQuantity() + Math.toIntExact(order.getQuantity()));
//            inventoryRepository.save(inventory);
            Optional<Company> companyDTO = companyRepository.findByBusinessId(order.getBusinessId());
            //진척 검수 테이블에 추가
            ProgressInspection progressInspection = new ProgressInspection();
            progressInspection.setPurchaseOrder(order);
            progressInspection.setProductCode(order.getProductCode());
            progressInspection.setProductName(item.getItemName());
            progressInspection.setTotalQuantity(order.getQuantity());
            progressInspection.setInspectedQuantity(0L);
            progressInspection.setInspectionStatus("검수예정");
            progressInspection.setExpectedArrivalDate(order.getExpectedArrivalDate());
            progressInspection.setBusinessId(order.getBusinessId());
            progressInspection.setComName(companyDTO.get().getComName());
            progressInspection.setInspectionDate(LocalDate.of(1000,1,1));
            progressInspection.setOrderDate(LocalDate.now());
            progressInspectionRepository.save(progressInspection);
        }
    }
    private Item dtoToEntity(ItemDTO dto) {
        return Item.builder()
                .id(dto.getId())
                .productCode(dto.getProductCode())
                .itemName(dto.getItemName())
                .build();
    }

    @Override
    public void registerPurchaseOrder(ProcumentPlanDTO procurementPlanDTO,String userName) {
        System.out.println("""
                SADSADADSAFSAFSAHFUISAHDUISAHDUILHSAULDHSADHUILASHDUILSAHDUILSAHDSAUHDUILA""");
        // 1. 부모 품목(생산 품목)에 대한 하위 품목 가져오기
        List<BOMRelationship> childItems = BOMRelationshipRepository.findChildItemsByParentProductCode(procurementPlanDTO.getProductCode());
        // 2. 하위 품목들의 수량 계산 및 조달 계획에 따른 발주서 생성
        List<PurchaseOrderDTO> purchaseOrderList = new ArrayList<>();

        for (BOMRelationship relationship : childItems) {
            String childProductCode = relationship.getChildItem().getProductCode();
            long requiredQuantity = relationship.getQuantity() * procurementPlanDTO.getProcurementQuantity();

            // 2.1 인벤토리와 비교하여 가용 재고를 차감한 후, 발주 필요 수량 계산
            Optional<ItemDTO> itemDTO = itemService.findByProductCode(childProductCode);
            long availableInventory = 0; // 기본적으로 가용 재고를 0으로 간주
            if (itemDTO.isPresent()) {
                Long itemId = itemDTO.get().getId();
                Inventory inventory = inventoryRepository.findByItemId(itemId).orElse(null);

                if (inventory != null) {
                    availableInventory = inventory.getCurrentQuantity() != null ? inventory.getCurrentQuantity() : 0;
                    // 가용 재고 차감
                    long adjustedInventory = Math.max(availableInventory - requiredQuantity, 0);
                    long inventoryConsumed = availableInventory - adjustedInventory;
                    inventory.setCurrentQuantity((int) adjustedInventory);
                    inventoryRepository.save(inventory); // 업데이트
                    // 발주 필요 수량에서 가용 재고만큼 차감
                    requiredQuantity -= inventoryConsumed;
                }
            }
            // 발주 수량이 0이면 발주 생성을 생략
            if (requiredQuantity <= 0) {
                continue;
            }
            // 3. P-BOM에서 각 하위 품목의 단일 사업자 ID 가져오기
            Optional<PurchaseBOM> purchaseBOM = purchaseBOMRepository.findByProductCode(childProductCode);
            if (purchaseBOM.isEmpty()) {
                throw new IllegalStateException("PBOM 구성이 완료되지 않았습니다: 하위 품목 코드 " + childProductCode +
                        ". PBOM 구성 완료 후 다시 시도해주세요.");
            }
            String businessId = purchaseBOM.get().getCompany().getBusinessId();
            if (businessId == null) {
                throw new IllegalStateException("사업자 ID를 찾을 수 없습니다: 하위 품목 코드 " + childProductCode);
            }
            // 4. 발주서 생성
            PurchaseOrderDTO purchaseOrderDTO = new PurchaseOrderDTO();
            // 단가 및 총 결제 금액 계산
            Long pricePerUnit = purchaseBOM.get().getUnitCost() != null ? purchaseBOM.get().getUnitCost() : 0L;
            Long totalPrice = pricePerUnit * requiredQuantity;
            // 발주 상태 기본값 설정
            purchaseOrderDTO.setStatus("자동생성");
            // 발주서 DTO 구성
            purchaseOrderDTO.setProductPlanCode(procurementPlanDTO.getProductPlanCode());
            purchaseOrderDTO.setProcurementPlanCode(procurementPlanDTO.getProcurementPlanCode());
            purchaseOrderDTO.setProductCode(childProductCode);
            purchaseOrderDTO.setBusinessId(businessId);
            purchaseOrderDTO.setProcurementEndDate(procurementPlanDTO.getProcurementEndDate());
            purchaseOrderDTO.setQuantity(requiredQuantity);
            purchaseOrderDTO.setPrice(totalPrice);
            purchaseOrderDTO.setCreatedBy(userName);
            purchaseOrderDTO.setCreatedDate(LocalDate.now());
            purchaseOrderDTO.setExpectedArrivalDate(calculateExpectedArrivalDateAndConvertToDTO(purchaseOrderDTO));
            // DTO 리스트에 추가
            purchaseOrderList.add(purchaseOrderDTO);
        }
        // 5. 발주서 테이블에 저장
        List<PurchaseOrder> purchaseOrders = purchaseOrderList.stream()
                .map(this::dtoToEntity)
                .collect(Collectors.toList());

        purchaseOrderRepository.saveAll(purchaseOrders);
    }

    // 리드타임 계산 및 DTO 변환
    private LocalDate calculateExpectedArrivalDateAndConvertToDTO(PurchaseOrderDTO purchaseOrder) {

        if (purchaseOrder != null) {
            String businessId = purchaseOrder.getBusinessId();
            String productCode = purchaseOrder.getProductCode();

            // Contract 레포지토리 사용
            Contract contract = contractRepository.findContractByBusinessIdAndProductCode(businessId, productCode);

            if (contract != null) {
                int leadTime = contract.getLeadTime(); // 리드타임 (일 단위)
                int productionQty = contract.getProductionQty(); // 리드타임당 생산량
                int supplyUnitLeadTime = contract.getSupplyUnit();

                Long totalQuantity = purchaseOrder.getQuantity(); // 발주된 총 수량
                LocalDate orderDate = purchaseOrder.getCreatedDate(); // 발주일

                if (totalQuantity != null && productionQty > 0 && leadTime > 0) {
                    // 필요한 리드타임 계산

                    // 협력회사 생산 리드타임
                    int requiredLeadTimes = (int) Math.ceil((double) totalQuantity / ((double)productionQty/leadTime));
                    //2명이서 2분동안 각각 1개씩 제품 진척검수를 한다 가정 함.
                    /*  하루 근무 시간: 오전 9시 ~ 오후 6시 → 9시간
                        실질적인 작업시간 8시간 = 480분
                        사람당 하루 검수량 240개  *2 = 480개
                     */
                    // 진척 검수 일정 추가
                    requiredLeadTimes+= (int)Math.ceil((double)totalQuantity/480);
                    //공급 운반 일정 추가
                    requiredLeadTimes += (int)Math.ceil((double)totalQuantity/supplyUnitLeadTime);
                    // 입고 예정일 계산
                    LocalDate expectedArrivalDate = orderDate.plusDays((long)(requiredLeadTimes));
                    purchaseOrder.setExpectedArrivalDate(expectedArrivalDate); // LocalDate로 설정
                } else {
                    purchaseOrder.setExpectedArrivalDate(null); // 계산 불가 시 null 처리
                }
            } else {
                purchaseOrder.setExpectedArrivalDate(null); // 계약 정보가 없으면 null
            }
        }

        assert purchaseOrder != null;
        return purchaseOrder.getExpectedArrivalDate();
    }


    @Override
    public List<PurchaseOrderDTO> getCompletedOrdersBybusinessId(String businessId) {
        return purchaseOrderRepository.findByBusinessId(businessId).stream()
                .map(this::convertEntityToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void update(PurchaseOrderDTO purchaseOrderDTO) {
        // 기존 발주서 조회
        PurchaseOrder existingOrder = purchaseOrderRepository.findById(purchaseOrderDTO.getPurchaseOrderCode())
                .orElseThrow(() -> new IllegalArgumentException("해당 발주서를 찾을 수 없습니다: " + purchaseOrderDTO.getPurchaseOrderCode()));

        // 업데이트할 값 적용
        existingOrder.setQuantity(purchaseOrderDTO.getQuantity());
        existingOrder.setPrice(purchaseOrderDTO.getPrice()); // 가격 재계산이 필요한 경우
        existingOrder.setUpdatedBy("김미영"); // 업데이트 담당자 설정
        existingOrder.setUpdatedDate(LocalDate.now());

        // 저장
        purchaseOrderRepository.save(existingOrder);
    }

    @Override
    public List<PurchaseOrderDTO> getOrdersByStatusAndProcurementPlanCode(String status, String procurementPlanCode) {
        List<PurchaseOrder> purchaseOrders = purchaseOrderRepository.findByStatusAndProcurementPlanCode(status, procurementPlanCode);

        return purchaseOrders.stream()
                .map(this::convertEntityToDTO) // 엔티티를 DTO로 변환
                .collect(Collectors.toList());
    }

    @Override
    public void delete(PurchaseOrderDTO purchaseOrderDTO) {
        if (purchaseOrderDTO.getPurchaseOrderCode() == null || purchaseOrderDTO.getPurchaseOrderCode().isEmpty()) {
            throw new IllegalArgumentException("삭제하려는 발주서 코드가 비어있습니다.");
        }

        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderDTO.getPurchaseOrderCode())
                .orElseThrow(() -> new RuntimeException("해당 발주서를 찾을 수 없습니다: " + purchaseOrderDTO.getPurchaseOrderCode()));

        purchaseOrderRepository.delete(purchaseOrder);
    }



    private PurchaseOrder dtoToEntity(PurchaseOrderDTO dto) {
        return PurchaseOrder.builder()
                .purchaseOrderCode(dto.getPurchaseOrderCode())
                .productPlanCode(dto.getProductPlanCode())
                .procurementPlanCode(dto.getProcurementPlanCode())
                .productCode(dto.getProductCode())
                .businessId(dto.getBusinessId())
                .procurementEndDate(dto.getProcurementEndDate())
                .quantity(dto.getQuantity())
                .Price(dto.getPrice())
                .status(dto.getStatus())
                .createdBy(dto.getCreatedBy())
                .createdDate(dto.getCreatedDate())
                .updatedBy(dto.getUpdatedBy())
                .updatedDate(dto.getUpdatedDate())
                .ExpectedArrivalDate(dto.getExpectedArrivalDate())
                .build();
    }

    // 엔티티를 DTO로 변환하는 메서드 추가
    private PurchaseOrderDTO convertEntityToDTO(PurchaseOrder entity) {
        return PurchaseOrderDTO.builder()
                .purchaseOrderCode(entity.getPurchaseOrderCode())
                .productPlanCode(entity.getProductPlanCode())
                .procurementPlanCode(entity.getProcurementPlanCode())
                .productCode(entity.getProductCode())
                .businessId(entity.getBusinessId())
                .procurementEndDate(entity.getProcurementEndDate())
                .quantity(entity.getQuantity())
                .Price(entity.getPrice())
                .status(entity.getStatus())
                .createdBy(entity.getCreatedBy())
                .createdDate(entity.getCreatedDate())
                .updatedBy(entity.getUpdatedBy())
                .updatedDate(entity.getUpdatedDate())
                .ExpectedArrivalDate(entity.getExpectedArrivalDate())
                .build();
    }
    @Override
    public List<PurchaseOrderDTO> searchOrders(String status, String keyword, String type, LocalDate startDate, LocalDate endDate) {
        return purchaseOrderRepository.searchOrders(status, type, keyword, startDate, endDate);
    }
    @Override
    public PurchaseOrderDTO getpurchaseOrderById(String purchaseOrderCode) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findById(purchaseOrderCode)
                .orElseThrow(() -> new RuntimeException("PurchaseOrder 데이터를 찾을 수 없습니다."));
        return convertEntityToDTO(purchaseOrder);
    }

}