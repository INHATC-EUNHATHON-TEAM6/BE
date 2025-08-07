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

    @Column(columnDefinition = "Text", nullable = false)
    private String content;
}
