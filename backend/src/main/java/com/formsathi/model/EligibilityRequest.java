package com.formsathi.model;

public record EligibilityRequest(
        Long documentId,
        String residency,
        String enrollment,
        Double marks,
        Double annualFamilyIncome
) {}
