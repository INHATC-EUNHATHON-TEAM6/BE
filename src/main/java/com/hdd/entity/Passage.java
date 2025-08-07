package com.hdd.entity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "passages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Passage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "passage_id")
    private Long passageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(columnDefinition = "Text", nullable = false)
    private String content;

    @Column(nullable = false)
    private String sourceType;

}
