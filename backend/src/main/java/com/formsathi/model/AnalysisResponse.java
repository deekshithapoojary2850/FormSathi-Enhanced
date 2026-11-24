package com.formsathi.model;

import java.util.List;

public record AnalysisResponse(
        Long id,
        String filename,
        String summary,
        List<String> importantPoints,
        List<String> eligibilityCriteria,
        String deadline,
        List<String> requiredDocuments,
        String extractedText
) {}
