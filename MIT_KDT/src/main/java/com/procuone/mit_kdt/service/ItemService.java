package com.procuone.mit_kdt.service;

import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;

import java.util.List;
import java.util.Optional;

public interface ItemService {
    // 상위 아이템(제품군) 조회
    List<ItemDTO> getTopItems();
    // 품목 저장 (DTO를 통해 저장 처리)
    boolean saveItem(ItemDTO itemDTO);

    // ID로 품목 조회 (DTO 반환)
    Optional<ItemDTO> getItemById(Long id);

    // 모든 품목 조회 (DTO 반환)
    List<ItemDTO> getAllItems();

    // 품목명과 규격으로 품목 검색 (DTO 반환)
    Optional<ItemDTO> findByItemNameAndDimensions(String itemName, String dimensions);

    // 제품 코드로 품목 조회 (DTO 반환)
    Optional<ItemDTO> findByProductCode(String productCode);

    // 품목 삭제
    void deleteItem(Long id);
}
