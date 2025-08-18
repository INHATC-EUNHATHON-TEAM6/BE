package com.words_hanjoom.domain.wordbooks.repository;

import com.words_hanjoom.domain.wordbooks.entity.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WordRepository extends JpaRepository<Word, Long> {

    // “배(과일)/배(타는 것)” 같이 동형이의어는 (targetCode, senseNo)로 구분
    boolean existsByTargetCodeAndSenseNo(Long targetCode, Short senseNo);

    Optional<Word> findByTargetCodeAndSenseNo(Long targetCode, Short senseNo);

    Optional<Word> findByWordName(String wordName);

    // 같은 표기어(동음이의/동형이의) 전부 가져올 때
    List<Word> findAllByWordName(String wordName);

    // (선택) 필요하면 이름으로 빠르게 존재 확인
    boolean existsByWordName(String wordName);
}