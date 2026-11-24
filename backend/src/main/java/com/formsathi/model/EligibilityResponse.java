package com.formsathi.model;

import java.util.List;

public record EligibilityResponse(String result, List<String> checks, List<String> caveats) {}
