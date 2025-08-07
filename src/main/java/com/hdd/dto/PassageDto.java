package com.hdd.dto;

import com.hdd.entity.Passage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PassageDto {

    private Long passageId;
    private String content;

    public PassageDto(Passage passage) {
        this.passageId = passage.getPassageId();
        this.content = passage.getContent();
    }

}
