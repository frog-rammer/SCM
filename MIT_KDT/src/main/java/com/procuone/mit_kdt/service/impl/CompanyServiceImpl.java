package com.procuone.mit_kdt.service.impl;

import com.procuone.mit_kdt.dto.CompanyDTO;
import com.procuone.mit_kdt.entity.Company;
import com.procuone.mit_kdt.repository.CompanyRepository;
import com.procuone.mit_kdt.service.CompanyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompanyServiceImpl implements CompanyService {

    private final CompanyRepository companyRepository;

    public CompanyServiceImpl(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    @Override
    @Transactional
    public void registerCompany(CompanyDTO companyDTO) {
        // DTO -> Entity 변환
        Company company = Company.builder()
                .businessId(companyDTO.getBusinessId())
                .comAccount(companyDTO.getComAccount())
                .comAdd(companyDTO.getComAdd())
                .comEmail(companyDTO.getComEmail())
                .comManage(companyDTO.getComManage())
                .comName(companyDTO.getComName())
                .comPhone(companyDTO.getComPhone())
                .build();

        // DB에 저장
        companyRepository.save(company);
    }

    @Override
    public List<Company> getAllCompanies() {
        return companyRepository.findAll(); // 모든 회사 데이터를 DB에서 가져오기
    }

    // DTO -> Entity 변환
    private Company dtoToEntity(CompanyDTO companyDTO) {
        return Company.builder()
                .businessId(companyDTO.getBusinessId())
                .comAccount(companyDTO.getComAccount())
                .comAdd(companyDTO.getComAdd())
                .comEmail(companyDTO.getComEmail())
                .comManage(companyDTO.getComManage())
                .comName(companyDTO.getComName())
                .comPhone(companyDTO.getComPhone())
                .build();
    }

    // Entity -> DTO 변환
    private CompanyDTO entityToDto(Company company) {
        return CompanyDTO.builder()
                .businessId(company.getBusinessId())
                .comAccount(company.getComAccount())
                .comAdd(company.getComAdd())
                .comEmail(company.getComEmail())
                .comManage(company.getComManage())
                .comName(company.getComName())
                .comPhone(company.getComPhone())
                .build();
    }
}
