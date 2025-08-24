package com.words_hanjoom.domain.wordbooks.controller;

import com.words_hanjoom.domain.users.repository.UserRepository;
import com.words_hanjoom.domain.wordbooks.dto.request.WordbookDeletionRequest;
import com.words_hanjoom.domain.wordbooks.dto.response.WordItemDto;
import com.words_hanjoom.domain.wordbooks.service.WordbookWordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestController
@RequestMapping("/api/wordbook")
@RequiredArgsConstructor
public class WordbookController {

    private final WordbookWordService wordbookWordService;
    private final UserRepository userRepository;

    @GetMapping //
    public Page<WordItemDto> getMyWordbook(
            Authentication authentication,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authentication");
        }
        String loginId = authentication.getName();
        Long userId = userRepository.findIdByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        return wordbookWordService.getMyWordbook(userId, pageable);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteWordFromWordbook(
            Authentication authentication,
            @RequestBody WordbookDeletionRequest request) { // 단어 ID를 요청 본문으로 받음

        if (authentication == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "no authentication");
        }
        String loginId = authentication.getName();

        Long userId = userRepository.findIdByLoginId(loginId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user not found"));

        wordbookWordService.deleteWordFromWordbook(userId, request.getWordId());

        log.info("Successfully deleted wordId {} from wordbook for userId {}", request.getWordId(), userId);
        return ResponseEntity.ok().build(); // 성공 시 200 OK 반환
    }
}
