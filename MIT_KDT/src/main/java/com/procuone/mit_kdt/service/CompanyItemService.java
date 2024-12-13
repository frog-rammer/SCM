package com.procuone.mit_kdt.service;

import com.procuone.mit_kdt.dto.CompanyItemDTO;
import com.procuone.mit_kdt.dto.ItemDTOs.ItemDTO;
import com.procuone.mit_kdt.entity.CompanyItem;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;

public interface CompanyItemService {
    void saveCompanyItems(List<CompanyItemDTO> companyItemDTOs);

    // 품목 ID로 공급업체 리스트를 조회하는 메서드 선언
    List<CompanyItemDTO> getSuppliersByItem(Long itemId);

    // BusinessId로 CompanyItemDTO 조회
    CompanyItemDTO getCompanyItemByBussinessId(String businessId);

    // Contract 상태 업데이트
    void updateContractStatus(Long itemId, String businessId, boolean status);

    CompanyItemDTO getCompanyItemByBussinessIdAnditemId(String businessId,Long itemId);
    // Item ID와 Business ID로 CompanyItem 조회
    Optional<CompanyItem> findByItemIdAndBusinessId(Long itemId, String businessId);

    // CompanyItem 업데이트
    void update(CompanyItem companyItem);

    List<ItemDTO> getItemsByBusinessId(String businessId);

    void processExcelFile(MultipartFile file) throws Exception;
}
