package com.formsathi.repository;

import com.formsathi.model.DocumentRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentRecord, Long> {
    List<DocumentRecord> findTop10ByOrderByCreatedAtDesc();
}
