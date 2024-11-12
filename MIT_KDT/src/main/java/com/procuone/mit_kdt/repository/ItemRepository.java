package com.procuone.mit_kdt.repository;

import com.procuone.mit_kdt.entity.BOM.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Long> {
    // 기본 CRUD 메서드는 JpaRepository에서 자동으로 제공됩니다.
}
