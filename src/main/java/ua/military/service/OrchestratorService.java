package ua.military.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ua.military.agent.AnalystAgent;
import ua.military.agent.RaportEditorAgent;
import ua.military.agent.MethodychnaEditorAgent;
import ua.military.model.DocumentRecord;
import ua.military.repository.DocumentRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrchestratorService {

    private final AnalystAgent           analystAgent;
    private final RaportEditorAgent      raportEditorAgent;
    private final MethodychnaEditorAgent methodychnaEditorAgent;
    private final DocxService            docxService;
    private final RagService             ragService;
    private final DocumentRepository     documentRepository;
    private final GrammarCheckService    grammarCheckService;

    public GenerationResponse generateDocument(String rawNotes) {
        long start = System.currentTimeMillis();

        // Якщо вхідний текст вже є готовим документом — не переписувати
        if (isCompleteDocument(rawNotes)) {
            log.info("=== Режим прямого форматування (готовий документ) ===");
            return formatDirectly(rawNotes, start);
        }

        log.info("=== Крок 1: Аналіз вхідних даних ===");
        String structuredJson = analystAgent.analyze(rawNotes);
        String documentType   = normalizeDocumentType(extractField(structuredJson, "documentType", "рапорт"));
        log.info("JSON від аналітика: {}", structuredJson);

        log.info("=== Крок 2: Пошук релевантних фрагментів (RAG) ===");
        RagResult ragResult = ragService.findRelevantContext(rawNotes);
        log.info("RAG: {} фрагментів, знайдено={}", ragResult.fragmentsUsed(), ragResult.hasContext());

        log.info("=== Крок 3: Генерація офіційного тексту ===");
        String officialText = cleanOutput(
                callEditor(documentType, structuredJson, ragResult.context(), rawNotes));

        log.info("=== Крок 4: Збереження у .docx ===");
        String filePath = docxService.generateDocx(
                officialText, documentType,
                extractField(structuredJson, "author",    "невідомо"),
                extractField(structuredJson, "recipient", ""));

        String fileName       = new java.io.File(filePath).getName();
        long   processingTime = System.currentTimeMillis() - start;

        log.info("=== Крок 5: Граматична перевірка (LanguageTool) ===");
        var grammarWarnings = grammarCheckService.check(officialText);
        if (!grammarWarnings.isEmpty()) {
            log.info("LanguageTool: знайдено {} попереджень", grammarWarnings.size());
        }

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
                ragResult.fragmentsUsed(), documentType, ragResult.hasContext(),
                grammarWarnings);
    }

    // Визначає чи текст є вже готовим документом (не нотатками)
    private boolean isCompleteDocument(String text) {
        if (text.length() < 500) return false;
        String t = text.toLowerCase();
        boolean isRaport = t.contains("рапорт")
                || t.contains("клопочу")
                || t.contains("доповідаю")
                || t.contains("командиру")
                || t.contains("начальнику")
                || t.contains("прошу розглянути")
                || t.contains("прошу надати")
                || t.contains("прошу дозволити");
        // Методична вважається готовою тільки якщо є структурні розділи ходу заняття
        boolean isMethodychna = (t.contains("методична розробка") && t.contains("хід заняття"))
                || (t.contains("хід заняття") && t.contains("мета"))
                || (t.contains("вступна частина") && t.contains("основна частина"));
        return isRaport || isMethodychna;
    }

    // Зберігає готовий документ напряму у .docx без переписування моделлю
    private GenerationResponse formatDirectly(String text, long start) {
        String documentType = text.toLowerCase().contains("методична розробка")
                ? "методична розробка" : "рапорт";

        String filePath = docxService.generateDocx(text, documentType, "", "");
        String fileName = new java.io.File(filePath).getName();
        long processingTime = System.currentTimeMillis() - start;

        documentRepository.save(DocumentRecord.builder()
                .documentType(documentType)
                .fileName(fileName)
                .createdAt(LocalDateTime.now())
                .processingTimeMs(processingTime)
                .ragFragmentsUsed(0)
                .ragContextFound(false)
                .compareMode(false)
                .build());

        log.info("Прямий формат (без AI): {} мс. Файл: {}", processingTime, fileName);

        return new GenerationResponse(
                documentType, text, fileName, processingTime,
                null, null, null, "",
                0, documentType, false);
    }

    public GenerationResponse generateDocumentCompare(String rawNotes) {
        long start = System.currentTimeMillis();

        log.info("=== [COMPARE] Крок 1: Аналіз ===");
        String structuredJson = analystAgent.analyze(rawNotes);
        String documentType   = normalizeDocumentType(extractField(structuredJson, "documentType", "рапорт"));
        String author         = extractField(structuredJson, "author",    "невідомо");
        String recipient      = extractField(structuredJson, "recipient", "");

        log.info("=== [COMPARE] Крок 2: RAG-пошук ===");
        RagResult ragResult = ragService.findRelevantContext(rawNotes);
        log.info("RAG: {} фрагментів", ragResult.fragmentsUsed());

        log.info("=== [COMPARE] Крок 3а: Генерація З RAG ===");
        String textWithRag    = cleanOutput(callEditor(documentType, structuredJson, ragResult.context(), rawNotes));

        log.info("=== [COMPARE] Крок 3б: Генерація БЕЗ RAG ===");
        String textWithoutRag = cleanOutput(callEditor(documentType, structuredJson, "", rawNotes));

        log.info("=== [COMPARE] Крок 4а: Збереження .docx (з RAG) ===");
        String filePathWithRag = docxService.generateDocx(textWithRag,    documentType, author, recipient);

        log.info("=== [COMPARE] Крок 4б: Збереження .docx (без RAG) ===");
        String filePathNoRag   = docxService.generateDocx(textWithoutRag, documentType, author, recipient);

        String fileNameWithRag = new java.io.File(filePathWithRag).getName();
        String fileNameNoRag   = new java.io.File(filePathNoRag).getName();
        long   processingTime  = System.currentTimeMillis() - start;

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

    private String callEditor(String documentType, String structuredJson,
                              String ragContext, String originalText) {
        String type = documentType.toLowerCase().strip();
        if (type.contains("методична")) {
            // Для методичної: фільтруємо рапорт-фрагменти з RAG, беремо до 600 символів
            String cleanedRag = filterRagForMethodychna(ragContext);
            String ragSnippet = (cleanedRag != null && cleanedRag.length() > 600)
                    ? cleanedRag.substring(0, 600) : cleanedRag;
            return methodychnaEditorAgent.rewrite(structuredJson, ragSnippet, originalText);
        }
        // Для рапорту — обрізаємо RAG до 120 символів щоб не копіювало чужі імена
        String ragSnippet = (ragContext != null && ragContext.length() > 120)
                ? ragContext.substring(0, 120) : ragContext;
        return raportEditorAgent.rewrite(structuredJson, ragSnippet, originalText);
    }

    // Видаляє з RAG фрагменти що стосуються рапортів (щоб не потрапляли у методичну)
    private String filterRagForMethodychna(String ragContext) {
        if (ragContext == null || ragContext.isBlank()) return "";
        return ragContext.lines()
                .filter(line -> {
                    String t = line.toLowerCase();
                    return !t.contains("доповідаю")
                        && !t.contains("прошу надати")
                        && !t.contains("прошу дозволити")
                        && !t.contains("звільнення")
                        && !t.contains("відпустку")
                        && !t.contains("правилами поведінки")
                        && !t.contains("проінструктован")
                        && !t.contains("солдат")
                        && !t.contains("рапорт")
                        // Рядки з правил рапорту (raport_rules.txt)
                        && !t.contains("замість давального")
                        && !t.contains("замість родового")
                        && !t.contains("відсутність дати")
                        && !t.contains("зайві емоційні")
                        && !t.contains("розмите формулювання")
                        && !t.contains("адресат у")
                        && !t.contains("у давальному відмінку")
                        && !t.contains("документа-обґрунтування");
                })
                .collect(java.util.stream.Collectors.joining("\n"))
                .strip();
    }

    // Видаляє артефакти які 3b-модель іноді вставляє у вивід
    private String cleanOutput(String text) {
        if (text == null) return "";
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        int blankCount = 0;
        for (String line : lines) {
            String t = line.strip();
            // Пропускаємо сміттєві рядки і артефакти моделі
            if (t.contains("════") || t.startsWith("ТИП:") || t.contains("{{")
                    || t.startsWith("довідка:") || t.startsWith("МЕТАДАНІ")
                    || t.startsWith("ТЕКСТ ЗАПИТУ") || t.startsWith("| довідка")
                    || t.startsWith("---") || t.equalsIgnoreCase("невідомо")
                    || t.equalsIgnoreCase("невідомо.") || t.equalsIgnoreCase("невідомо,")) continue;
            if (t.isEmpty()) {
                blankCount++;
                if (blankCount <= 1) sb.append("\n");
            } else {
                blankCount = 0;
                sb.append(line).append("\n");
            }
        }
        return sb.toString().strip();
    }

    // Нормалізує тип документа — модель іноді повертає "Raport" або "raport" замість "рапорт"
    private String normalizeDocumentType(String type) {
        if (type == null) return "рапорт";
        String t = type.toLowerCase().strip();
        if (t.contains("рапорт") || t.contains("raport") || t.contains("rapport")) return "рапорт";
        if (t.contains("методична") || t.contains("metodychna")) return "методична розробка";
        if (t.contains("наказ")    || t.contains("nakaz"))       return "наказ";
        if (t.contains("план")     || t.contains("конспект"))    return "план-конспект";
        if (t.contains("доповідна"))                              return "доповідна записка";
        return "рапорт";
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
