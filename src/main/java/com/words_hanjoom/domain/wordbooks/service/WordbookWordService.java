package com.words_hanjoom.domain.wordbooks.service;

import com.words_hanjoom.domain.wordbooks.dto.response.WordItemDto;
import com.words_hanjoom.domain.wordbooks.entity.Wordbook;
import com.words_hanjoom.domain.wordbooks.repository.WordbookRepository;
import com.words_hanjoom.domain.wordbooks.repository.WordbookWordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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

    @Transactional
    public void deleteWordFromWordbook(Long userId, Long wordId) {
        // 1. 해당 유저의 단어장 ID를 찾습니다.
        Wordbook wordbook = wordbookRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wordbook not found for user"));

        // 2. 단어장 ID와 단어 ID로 단어를 물리적으로 삭제합니다.
        // 이 로직은 해당 단어가 사용자 단어장에 있는지 먼저 확인하지 않습니다.
        // WordbookWordRepository의 deleteByWordbookIdAndWordId 메서드를 사용합니다.
        wordbookWordRepository.deleteByWordbookIdAndWordId(wordbook.getWordbookId(), wordId);
    }
}
