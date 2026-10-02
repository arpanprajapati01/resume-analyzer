package com.resumeanalyzer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class AnalyzeController {
    private final ResumeTextExtractor extractor;
    private final ClaudeReportService claude;
    private final AtsEngine engine = new AtsEngine();

    public AnalyzeController(ResumeTextExtractor extractor, ClaudeReportService claude) {
        this.extractor = extractor;
        this.claude = claude;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file,
                                     @RequestParam(value = "jd", required = false) String jd,
                                     @RequestParam(value = "ai", defaultValue = "false") boolean ai) throws Exception {
        String text = extractor.extract(file);
        if (text.isBlank()) return ResponseEntity.badRequest().body(Map.of("error",
            "No text could be extracted. If your PDF is a scanned image, ATS systems can't read it either."));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("report", engine.analyze(text, jd));
        if (ai) {
            out.put("aiReport", claude.enabled() ? claude.generate(text, jd)
                : "Set the ANTHROPIC_API_KEY environment variable to enable the AI line-by-line report.");
        }
        return ResponseEntity.ok(out);
    }
}
