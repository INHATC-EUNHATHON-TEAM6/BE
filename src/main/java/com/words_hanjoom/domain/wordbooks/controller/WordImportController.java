import com.words_hanjoom.domain.wordbooks.service.WordImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/words/import")
@RequiredArgsConstructor
public class WordImportController {

    private final WordImportService importService;

    @PostMapping
    public Map<String, Object> importWords(@RequestParam String q) {
        int count = importService.importBySurface(q);
        return Map.of("imported", count);
    }
}