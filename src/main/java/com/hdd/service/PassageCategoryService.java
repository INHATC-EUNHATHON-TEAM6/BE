package com.hdd.service;

import com.hdd.dto.PassageDto;
import com.hdd.entity.Passage;
import com.hdd.repository.PassageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class PassageCategoryService {
    private final PassageRepository passageRepository;

    // 프론트에서 받은 categoryId를 기반으로 DB에서 랜덤 지문을 조회
    public PassageDto getRandomPassage(Long categoryId) {

        // 해당 분야(categoryId)에 속하고, sourceType이 'random'인 지문 리스트 가져오기
        List<Passage> passages = passageRepository.findByCategory_CategoryIdAndSourceType(categoryId, "random");

        // 리스트가 비어 있으면 예외 발생 → 프론트에 에러 응답 가능
        if (passages.isEmpty()) {
            throw new NoSuchElementException("해당 분야의 지문이 없습니다.");
        }

        // 리스트 중에서 하나를 무작위로 선택
        Passage random = passages.get(new Random().nextInt(passages.size()));
        return new PassageDto(random);
    }
}
