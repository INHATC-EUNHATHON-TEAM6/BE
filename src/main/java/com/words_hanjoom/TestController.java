package com.words_hanjoom;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @Operation(summary = "테스트용 API")
    @GetMapping("/test")
    public String test() {
        return "테스트용 API입니다.";
    }
}
