package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.response.WordItemDto;
import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import com.words_hanjoom.domain.wordbooks.repository.WordbookRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WordbookWordService {

    private final WordbookWordRepository wordbookWordRepository;
    private final WordbookRepository wordbookRepository;

    @Transactional(readOnly = true)
    public Page<WordItemDto> getMyWordbook(Long userId, Pageable pageable) {
        return wordbookWordRepository.findMyWords(userId, pageable);
    }

    /** 단어장 없으면 생성해서 반환 */
    @Transactional
    public Wordbook ensureWordbook(Long userId) {
        return wordbookRepository.ensureWordbook(userId);
    }
}
