package ua.military.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import ua.military.model.DocumentRecord;

import java.util.List;

public interface DocumentRepository extends JpaRepository<DocumentRecord, Long> {

    List<DocumentRecord> findTop20ByOrderByCreatedAtDesc();

    @Query("SELECT r.documentType, COUNT(r) FROM DocumentRecord r " +
           "GROUP BY r.documentType ORDER BY COUNT(r) DESC")
    List<Object[]> countByDocumentType();
}
