package ua.military.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.military.agent.AnalystAgent;
import ua.military.agent.EditorAgent;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final AnalystAgent analystAgent;
    private final EditorAgent  editorAgent;
    private final DocxService  docxService;
    private final RagService   ragService;

    public String generateDocument(String rawNotes) {
        log.info("=== Крок 1: Аналіз вхідних даних ===");
        String structuredJson = analystAgent.analyze(rawNotes);
        log.info("JSON від аналітика: {}", structuredJson);

        log.info("=== Крок 2: Пошук релевантних зразків (RAG) ===");
        String ragContext = ragService.findRelevantContext(rawNotes);
        if (!ragContext.isBlank()) {
            log.info("RAG знайшов контекст ({} символів)", ragContext.length());
        } else {
            log.info("RAG контекст не знайдено, генеруємо без зразків");
        }

        log.info("=== Крок 3: Генерація офіційного тексту ===");
        String editorInput = buildEditorInput(structuredJson, ragContext);
        String officialText = editorAgent.rewrite(editorInput);
        log.info("Текст документа готовий");

        log.info("=== Крок 4: Збереження у .docx ===");
        String filePath = docxService.generateDocx(
                officialText,
                extractField(structuredJson, "documentType", "рапорт"),
                extractField(structuredJson, "author", "невідомо")
        );

        return "Документ збережено: " + filePath + "\n\n" + officialText;
    }

    /**
     * Якщо RAG знайшов зразки — додаємо їх перед JSON як підказку для редактора.
     * Це покращує відповідність ДСТУ без зміни інтерфейсу агента.
     */
    private String buildEditorInput(String json, String ragContext) {
        if (ragContext.isBlank()) {
            return json;
        }
        return "Зразки офіційних документів для орієнтиру:\n"
                + ragContext
                + "\n---\nДані для генерації документа:\n"
                + json;
    }

    /**
     * Простий парсер поля з JSON-рядка без залежності від Jackson.
     * Повертає значення або fallback якщо поле відсутнє.
     */
    private String extractField(String json, String field, String fallback) {
        try {
            String key = "\"" + field + "\"";
            int keyIdx = json.indexOf(key);
            if (keyIdx == -1) return fallback;

            int colonIdx = json.indexOf(':', keyIdx);
            int quoteStart = json.indexOf('"', colonIdx + 1);
            int quoteEnd = json.indexOf('"', quoteStart + 1);
            if (quoteStart == -1 || quoteEnd == -1) return fallback;

            String value = json.substring(quoteStart + 1, quoteEnd).strip();
            return value.isBlank() ? fallback : value;
        } catch (Exception e) {
            log.warn("Не вдалося витягти поле '{}' з JSON", field);
            return fallback;
        }
    }
}
