package com.words_hanjoom.domain.wordbooks.dto.request;

import java.util.Optional;

public record SearchRequest(
        String q,
        String reqType,
        Integer start,
        Integer num,
        String advanced,
        Optional<Integer> target,
        Optional<String> method,
        Optional<String> type1,
        Optional<String> type2,
        Optional<String> pos,
        Optional<String> cat,
        Optional<String> multimedia,
        Optional<Integer> letterS,
        Optional<Integer> letterE,
        Optional<Integer> updateS,
        Optional<Integer> updateE
) {
    // 기본값 & null 방어 (compact canonical constructor)
    public SearchRequest {
        reqType   = (reqType == null || reqType.isBlank()) ? "json" : reqType;
        start     = (start == null) ? 1  : start;
        num       = (num == null)   ? 10 : num;
        advanced  = (advanced == null || advanced.isBlank()) ? "n" : advanced;

        target     = target     == null ? Optional.empty() : target;
        method     = method     == null ? Optional.empty() : method;
        type1      = type1      == null ? Optional.empty() : type1;
        type2      = type2      == null ? Optional.empty() : type2;
        pos        = pos        == null ? Optional.empty() : pos;
        cat        = cat        == null ? Optional.empty() : cat;
        multimedia = multimedia == null ? Optional.empty() : multimedia;
        letterS    = letterS    == null ? Optional.empty() : letterS;
        letterE    = letterE    == null ? Optional.empty() : letterE;
        updateS    = updateS    == null ? Optional.empty() : updateS;
        updateE    = updateE    == null ? Optional.empty() : updateE;
    }

    // 사용 중인 코드 호환용 getter (record 기본 접근자 q(), reqType()도 그대로 쓸 수 있음)
    public String getQ()       { return q; }
    public String getReqType() { return reqType; }
    public Integer getStart()  { return start; }
    public Integer getNum()    { return num; }
    public String getAdvanced(){ return advanced; }

    public static SearchRequest basic(String q) {
        return new SearchRequest(
                q, "json", 1, 10, "n",
                Optional.empty(),  // target
                Optional.empty(),  // method
                Optional.empty(),  // type1
                Optional.empty(),  // type2
                Optional.empty(),  // pos
                Optional.empty(),  // cat
                Optional.empty(),  // multimedia
                Optional.empty(),  // letterS
                Optional.empty(),  // letterE
                Optional.empty(),  // updateS
                Optional.empty()   // updateE
        );
    }
}