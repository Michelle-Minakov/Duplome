package ua.military.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "document_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String        documentType;
    private String        fileName;
    private LocalDateTime createdAt;
    private long          processingTimeMs;
    private int           ragFragmentsUsed;
    private boolean       ragContextFound;
    private boolean       compareMode;
}
