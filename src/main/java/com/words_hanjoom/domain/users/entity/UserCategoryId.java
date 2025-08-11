package com.words_hanjoom.domain.users.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserCategoryId implements java.io.Serializable {
    @Column(name = "user_id")
    private Long userId;
    @Column(name = "category_id")
    private Long categoryId;
}