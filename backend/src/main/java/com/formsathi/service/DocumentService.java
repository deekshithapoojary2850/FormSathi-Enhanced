package com.formsathi.service;

import com.formsathi.model.*;
import com.formsathi.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DocumentService {
    private final DocumentRepository repository;

    public DocumentService(DocumentRepository repository) {
        this.repository = repository;
    }

    public AnalysisResponse analyzeFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Please select a non-empty PDF or TXT file.");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("File must be 10 MB or smaller.");
        String name = Optional.ofNullable(file.getOriginalFilename()).orElse("uploaded-document");
        String text;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            try (var pdf = Loader.loadPDF(file.getBytes())) {
                text = new PDFTextStripper().getText(pdf);
            }
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("No selectable text was found. This may be a scanned PDF; please use a text-based PDF or paste its text.");
            }
        } else if (lower.endsWith(".txt")) {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } else {
            throw new IllegalArgumentException("Only PDF and TXT files are supported.");
        }
        return analyzeText(text, name);
    }

    public AnalysisResponse analyzeText(String text, String filename) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Please upload a document or paste its text.");
        if (text.length() > 200_000) throw new IllegalArgumentException("Text is too long. Please use a document under 200,000 characters.");

        String normalized = text.replaceAll("\\s+", " ").trim();
        List<String> eligibility = extractSentences(normalized,
                "eligible", "eligibility", "must be", "minimum", "marks", "percentage", "income", "resident", "enrolled", "age");
        List<String> docs = extractDocuments(normalized);
        String deadline = extractDeadline(normalized);
        List<String> important = new ArrayList<>();
        if (!deadline.equals("Not clearly found")) important.add("Application deadline: " + deadline);
        important.addAll(eligibility.stream().limit(4).toList());
        if (important.isEmpty()) important.add("Review the notice carefully; no clear eligibility or deadline statements were detected.");

        String summary = buildSummary(filename, deadline, eligibility, docs, normalized);
        DocumentRecord record = new DocumentRecord();
        record.setFilename(filename == null || filename.isBlank() ? "pasted-notice.txt" : filename);
        record.setExtractedText(normalized);
        record.setSummary(summary);
        record.setEligibilityCriteria(String.join("\n", eligibility));
        record.setDeadline(deadline);
        record.setRequiredDocuments(String.join("\n", docs));
        record = repository.save(record);

        return new AnalysisResponse(record.getId(), record.getFilename(), summary, important,
                eligibility, deadline, docs, normalized);
    }

    public AnalysisResponse get(Long id) {
        DocumentRecord r = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found."));
        return toResponse(r);
    }

    public List<AnalysisResponse> recent() {
        return repository.findTop10ByOrderByCreatedAtDesc().stream().map(this::toResponse).toList();
    }

    public EligibilityResponse checkEligibility(EligibilityRequest request) {
        DocumentRecord doc = repository.findById(request.documentId())
                .orElseThrow(() -> new NoSuchElementException("Analyze a notice first."));
        String text = doc.getExtractedText().toLowerCase(Locale.ROOT);
        List<String> checks = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        if (text.contains("resident of india") || text.contains("indian citizen")) {
            boolean ok = request.residency() != null && request.residency().toLowerCase(Locale.ROOT).contains("yes");
            checks.add("Indian residency/citizenship condition: " + (ok ? "Applicant marked Yes" : "Applicant has not confirmed this condition."));
            if (!ok) failures.add("Confirm the residency/citizenship requirement.");
        }
        if (text.contains("enrolled") || text.contains("student")) {
            boolean ok = request.enrollment() != null && request.enrollment().toLowerCase(Locale.ROOT).contains("yes");
            checks.add("Current student/enrollment condition: " + (ok ? "Applicant marked Yes" : "Applicant has not confirmed this condition."));
            if (!ok) failures.add("Confirm current enrollment in a recognised institution.");
        }
        Matcher marksMatcher = Pattern.compile("(?:at least|minimum of|minimum)\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*%").matcher(text);
        if (marksMatcher.find() && request.marks() != null) {
            double min = Double.parseDouble(marksMatcher.group(1));
            boolean ok = request.marks() >= min;
            checks.add(String.format(Locale.ROOT, "Marks: %.1f%% entered; notice asks for at least %.1f%% — %s.", request.marks(), min, ok ? "meets this condition" : "below this condition"));
            if (!ok) failures.add("Marks are below the percentage stated in the notice.");
        } else {
            checks.add("Marks requirement: could not reliably compare a numeric threshold; verify the notice.");
        }

        Matcher incomeMatcher = Pattern.compile("(?:not exceed|below|less than|maximum|up to)\\s*(?:rs\\.?|inr|₹)?\\s*([\\d,]+)").matcher(text);
        if (incomeMatcher.find() && request.annualFamilyIncome() != null) {
            double max = Double.parseDouble(incomeMatcher.group(1).replace(",", ""));
            boolean ok = request.annualFamilyIncome() <= max;
            checks.add(String.format(Locale.ROOT, "Annual family income: ₹%.0f entered; notice limit is ₹%.0f — %s.", request.annualFamilyIncome(), max, ok ? "meets this condition" : "above this condition"));
            if (!ok) failures.add("Annual family income is above the limit stated in the notice.");
        } else {
            checks.add("Income requirement: could not reliably compare a numeric threshold; verify the notice.");
        }

        String result = failures.isEmpty() ? "No mismatch found in the details you entered. Verify all conditions in the official notice."
                : "Some conditions need attention. Review the checks below and verify the official notice.";
        return new EligibilityResponse(result, checks,
                List.of("This is an automated preliminary check, not an official decision.",
                        "The result depends on the information entered and the text successfully extracted from the notice.",
                        "Confirm every requirement with the scholarship provider."));
    }

    public AskResponse answerQuestion(Long id, String question) {
        DocumentRecord doc = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Document not found."));
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Please enter a question.");
        String q = question.toLowerCase(Locale.ROOT);
        String answer;
        if (q.contains("last date") || q.contains("deadline") || q.contains("date")) {
            answer = "Deadline found in the notice: " + doc.getDeadline();
        } else if (q.contains("document") || q.contains("certificate") || q.contains("upload")) {
            answer = doc.getRequiredDocuments().isBlank() ? "No specific document list was detected. Please check the original notice."
                    : "Documents detected in the notice: " + doc.getRequiredDocuments().replace("\n", "; ");
        } else if (q.contains("eligible") || q.contains("eligibility") || q.contains("marks") || q.contains("income")) {
            answer = doc.getEligibilityCriteria().isBlank() ? "No clear eligibility statements were detected. Please check the original notice."
                    : "Eligibility details detected: " + doc.getEligibilityCriteria().replace("\n", "; ");
        } else {
            answer = "Here is the notice summary: " + doc.getSummary() + " Try asking about the deadline, eligibility, marks, income or required documents.";
        }
        return new AskResponse(answer);
    }

    private AnalysisResponse toResponse(DocumentRecord r) {
        return new AnalysisResponse(r.getId(), r.getFilename(), r.getSummary(),
                Arrays.asList(r.getSummary().split("\\n")), lines(r.getEligibilityCriteria()),
                r.getDeadline(), lines(r.getRequiredDocuments()), r.getExtractedText());
    }

    private List<String> lines(String value) {
        if (value == null || value.isBlank()) return List.of();
        return Arrays.stream(value.split("\\n")).filter(s -> !s.isBlank()).toList();
    }

    private List<String> extractSentences(String text, String... keywords) {
        List<String> found = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            String s = sentence.trim();
            String l = s.toLowerCase(Locale.ROOT);
            if (Arrays.stream(keywords).anyMatch(l::contains) && s.length() > 15 && !found.contains(s)) {
                found.add(s.length() > 260 ? s.substring(0, 257) + "..." : s);
            }
        }
        return found.stream().limit(10).toList();
    }

    private List<String> extractDocuments(String text) {
        String[] keywords = {"aadhaar", "identity", "passport-size", "photograph", "marksheet", "grade report",
                "income certificate", "bank passbook", "cancelled cheque", "bonafide", "admission certificate"};
        List<String> docs = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            String s = sentence.trim();
            String l = s.toLowerCase(Locale.ROOT);
            if (Arrays.stream(keywords).anyMatch(l::contains)) {
                String cleaned = s.replaceAll("^[\\d.\\s]+", "").trim();
                if (!cleaned.isBlank() && !docs.contains(cleaned)) docs.add(cleaned);
            }
        }
        return docs.stream().limit(12).toList();
    }

    private String extractDeadline(String text) {
        Pattern p = Pattern.compile("(?i)(?:last date to apply|application deadline|deadline|apply before|last date)\\s*[:\\-]?\\s*([^.;\\n]{3,70})");
        Matcher m = p.matcher(text);
        if (m.find()) return m.group(1).trim();
        Matcher date = Pattern.compile("\\b\\d{1,2}\\s+[A-Za-z]+\\s+20\\d{2}\\b").matcher(text);
        return date.find() ? date.group() : "Not clearly found";
    }

    private String buildSummary(String filename, String deadline, List<String> eligibility,
                                List<String> docs, String text) {
        StringBuilder b = new StringBuilder();
        b.append("Notice: ").append(filename == null ? "Uploaded notice" : filename).append(". ");
        b.append("Deadline: ").append(deadline).append(". ");
        b.append("Eligibility statements detected: ").append(eligibility.size()).append(". ");
        b.append("Required-document items detected: ").append(docs.size()).append(". ");
        if (!eligibility.isEmpty()) b.append("Key criteria: ").append(eligibility.get(0)).append(" ");
        if (text.toLowerCase(Locale.ROOT).contains("scholarship")) b.append("This appears to be a scholarship-related notice.");
        else b.append("The document may not be a scholarship notice; review the extracted content.");
        return b.toString();
    }
}
