package com.procuone.mit_kdt.entity.BOM;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class Item {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 기본 키 (자동 생성)

    @Column(unique = true, nullable = false)
    private String productCode; // 제품 코드 (유일하고 변경 가능)

    private String itemName; // 품목명
    private String drawingFile; // 도면 파일 경로
    private String dimensions; // 치수 정보 (예: 100x50x30 mm)

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category; // 이 부품이 속하는 카테고리
}
