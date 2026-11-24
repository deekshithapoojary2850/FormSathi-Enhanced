package com.formsathi.controller;

import com.formsathi.model.*;
import com.formsathi.service.DocumentService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class DocumentController {
    private final DocumentService service;

    public DocumentController(DocumentService service) { this.service = service; }

    @GetMapping("/health")
    public Map<String, String> health() { return Map.of("status", "UP", "application", "FormSathi"); }

    @PostMapping(value = "/documents/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AnalysisResponse analyze(@RequestParam("file") MultipartFile file) throws IOException {
        return service.analyzeFile(file);
    }

    @PostMapping("/documents/analyze-text")
    public AnalysisResponse analyzeText(@RequestBody AnalyzeTextRequest request) {
        return service.analyzeText(request.text(), request.filename());
    }

    @GetMapping("/documents/recent")
    public List<AnalysisResponse> recent() { return service.recent(); }

    @GetMapping("/documents/{id}")
    public AnalysisResponse get(@PathVariable Long id) { return service.get(id); }

    @PostMapping("/eligibility/check")
    public EligibilityResponse eligibility(@RequestBody EligibilityRequest request) {
        return service.checkEligibility(request);
    }

    @PostMapping("/documents/{id}/ask")
    public AskResponse ask(@PathVariable Long id, @RequestBody AskRequest request) {
        return service.answerQuestion(id, request.question());
    }
}
