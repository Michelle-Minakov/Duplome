package ua.military.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.military.agent.AnalystAgent;
import ua.military.agent.EditorAgent;
import ua.military.model.DocumentRecord;
import ua.military.repository.DocumentRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final AnalystAgent       analystAgent;
    private final EditorAgent        editorAgent;
    private final DocxService        docxService;
    private final RagService         ragService;
    private final StatisticsService  statisticsService;
    private final DocumentRepository documentRepository;

    public GenerationResponse generateDocument(String rawNotes) {
        long start = System.currentTimeMillis();

        log.info("=== Крок 1: Аналіз вхідних даних ===");
        String structuredJson = analystAgent.analyze(rawNotes);
        String documentType   = extractField(structuredJson, "documentType", "документ");
        log.info("JSON від аналітика: {}", structuredJson);

        log.info("=== Крок 2: Пошук релевантних фрагментів (RAG) ===");
        RagResult ragResult = ragService.findRelevantContext(rawNotes);
        log.info("RAG: {} фрагментів, знайдено={}", ragResult.fragmentsUsed(), ragResult.hasContext());

        log.info("=== Крок 3: Генерація офіційного тексту ===");
        String officialText = editorAgent.rewrite(structuredJson, ragResult.context());

        log.info("=== Крок 4: Збереження у .docx ===");
        String filePath = docxService.generateDocx(
                officialText, documentType,
                extractField(structuredJson, "author",    "невідомо"),
                extractField(structuredJson, "recipient", ""));

        String fileName       = new java.io.File(filePath).getName();
        long   processingTime = System.currentTimeMillis() - start;

        statisticsService.record(documentType);
        documentRepository.save(DocumentRecord.builder()
                .documentType(documentType)
                .fileName(fileName)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(processingTime)
                .ragFragmentsUsed(ragResult.fragmentsUsed())
                .ragContextFound(ragResult.hasContext())
                .compareMode(false)
                .build());

        log.info("Готово за {} мс. RAG={} фрагм. Файл: {}",
                processingTime, ragResult.fragmentsUsed(), fileName);

        return new GenerationResponse(
                documentType, officialText, fileName, processingTime,
                null, null, null, ragResult.context(),
                ragResult.fragmentsUsed(), documentType, ragResult.hasContext());
    }

    public GenerationResponse generateDocumentCompare(String rawNotes) {
        long start = System.currentTimeMillis();

        log.info("=== [COMPARE] Крок 1: Аналіз ===");
        String structuredJson = analystAgent.analyze(rawNotes);
        String documentType   = extractField(structuredJson, "documentType", "документ");
        String author         = extractField(structuredJson, "author",    "невідомо");
        String recipient      = extractField(structuredJson, "recipient", "");

        log.info("=== [COMPARE] Крок 2: RAG-пошук ===");
        RagResult ragResult = ragService.findRelevantContext(rawNotes);
        log.info("RAG: {} фрагментів", ragResult.fragmentsUsed());

        log.info("=== [COMPARE] Крок 3а: Генерація З RAG ===");
        String textWithRag    = editorAgent.rewrite(structuredJson, ragResult.context());

        log.info("=== [COMPARE] Крок 3б: Генерація БЕЗ RAG ===");
        String textWithoutRag = editorAgent.rewrite(structuredJson, "");

        log.info("=== [COMPARE] Крок 4а: Збереження .docx (з RAG) ===");
        String filePathWithRag = docxService.generateDocx(textWithRag,    documentType, author, recipient);

        log.info("=== [COMPARE] Крок 4б: Збереження .docx (без RAG) ===");
        String filePathNoRag   = docxService.generateDocx(textWithoutRag, documentType, author, recipient);

        String fileNameWithRag = new java.io.File(filePathWithRag).getName();
        String fileNameNoRag   = new java.io.File(filePathNoRag).getName();
        long   processingTime  = System.currentTimeMillis() - start;

        statisticsService.record(documentType);
        documentRepository.save(DocumentRecord.builder()
                .documentType(documentType)
                .fileName(fileNameWithRag)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(processingTime)
                .ragFragmentsUsed(ragResult.fragmentsUsed())
                .ragContextFound(ragResult.hasContext())
                .compareMode(true)
                .build());

        log.info("[COMPARE] Готово за {} мс.", processingTime);

        return new GenerationResponse(
                documentType, textWithRag, fileNameWithRag, processingTime,
                textWithRag, textWithoutRag, fileNameNoRag, ragResult.context(),
                ragResult.fragmentsUsed(), documentType, ragResult.hasContext());
    }

    private String extractField(String json, String field, String fallback) {
        try {
            String key     = "\"" + field + "\"";
            int    keyIdx  = json.indexOf(key);
            if (keyIdx == -1) return fallback;
            int colonIdx   = json.indexOf(':', keyIdx);
            int quoteStart = json.indexOf('"', colonIdx + 1);
            int quoteEnd   = json.indexOf('"', quoteStart + 1);
            if (quoteStart == -1 || quoteEnd == -1) return fallback;
            String value   = json.substring(quoteStart + 1, quoteEnd).strip();
            return value.isBlank() ? fallback : value;
        } catch (Exception e) {
            log.warn("Не вдалося витягти поле '{}' з JSON", field);
            return fallback;
        }
    }
}
