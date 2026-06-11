package ua.military.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

@Slf4j
@Service
public class RagService {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    private final ResourcePatternResolver resourceResolver =
            new PathMatchingResourcePatternResolver();

    private static final String FEEDBACK_FILE = "rag/generated_samples.txt";

    @PostConstruct
    public void init() {
        log.info("Ініціалізація RAG сервісу...");
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();

        int fileCount = loadFromResourceFolder();
        if (fileCount == 0) {
            log.warn("Файли в classpath:rag/ не знайдені — використовую вбудовані зразки.");
            loadHardcodedSamples();
        }

        loadFeedbackSamples();

        log.info("RAG сервіс готовий.");
    }

    // ── Завантаження накопичених зразків з диску (між перезапусками) ──
    private void loadFeedbackSamples() {
        File file = new File(FEEDBACK_FILE);
        if (!file.exists()) return;
        try {
            String text = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            if (!text.isBlank()) {
                addToStore(text, "generated_samples.txt");
                log.info("Завантажено накопичені зразки генерацій: {}", FEEDBACK_FILE);
            }
        } catch (IOException e) {
            log.warn("Не вдалося завантажити feedback-файл {}: {}", FEEDBACK_FILE, e.getMessage());
        }
    }

    // ── Збереження нового зразка у файл і одразу у пам'ять ──
    public void addGeneratedSample(String text, String documentType) {
        if (text == null || text.isBlank()) return;
        try {
            File file = new File(FEEDBACK_FILE);
            file.getParentFile().mkdirs();
            String entry = "\n\n---\nТИП: " + documentType + "\n---\n" + text;
            Files.writeString(file.toPath(), entry, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            addToStore(text, "feedback:" + documentType);
            log.info("Зразок збережено у базу знань ({} символів)", text.length());
        } catch (IOException e) {
            log.warn("Не вдалося зберегти зразок: {}", e.getMessage());
        }
    }

    public int getFeedbackCount() {
        File file = new File(FEEDBACK_FILE);
        if (!file.exists()) return 0;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            return (int) content.lines().filter(l -> l.startsWith("ТИП:")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    // Завантаження .txt і .pdf з classpath:rag/ при старті
    private int loadFromResourceFolder() {
        int loaded = 0;

        // .txt файли
        try {
            Resource[] txtFiles = resourceResolver.getResources("classpath:rag/*.txt");
            for (Resource res : txtFiles) {
                try (InputStream is = res.getInputStream()) {
                    String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                    addToStore(text, res.getFilename());
                    loaded++;
                } catch (IOException e) {
                    log.warn("Не вдалося прочитати {}: {}", res.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Немає .txt файлів у classpath:rag/");
        }

        // .pdf файли — завантажуємо тільки перші 25 сторінок (формати і правила)
        try {
            Resource[] pdfFiles = resourceResolver.getResources("classpath:rag/*.pdf");
            for (Resource res : pdfFiles) {
                try (InputStream is = res.getInputStream();
                     PDDocument doc = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(Math.min(25, doc.getNumberOfPages()));
                    String text = stripper.getText(doc);
                    addToStore(text, res.getFilename());
                    loaded++;
                    log.info("PDF з classpath завантажено (стор. 1-25): {}", res.getFilename());
                } catch (IOException e) {
                    log.warn("Не вдалося прочитати PDF {}: {}", res.getFilename(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("Немає .pdf файлів у classpath:rag/");
        }

        if (loaded > 0) log.info("Завантажено {} файлів з classpath:rag/", loaded);
        return loaded;
    }

    // Завантаження PDF з файлової системи (розширюваний метод)
    public void loadFromPdf(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.warn("PDF не знайдено: {}", filePath);
            return;
        }
        try (PDDocument document = PDDocument.load(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            addToStore(text, file.getName());
            log.info("PDF завантажено в RAG: {} ({} символів)", file.getName(), text.length());
        } catch (IOException e) {
            log.error("Помилка завантаження PDF {}: {}", filePath, e.getMessage());
        }
    }

    // ── Загальний помічник: розбиття на фрагменти і векторизація ──
    private void addToStore(String text, String source) {
        if (text == null || text.isBlank()) return;
        DocumentSplitter splitter = DocumentSplitters.recursive(400, 80);
        List<TextSegment> segments = splitter.split(Document.from(text));
        List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
        embeddingStore.addAll(embeddings, segments);
        log.info("  [RAG] {} → {} фрагментів", source, segments.size());
    }

    // ── Вбудовані зразки — fallback якщо папка rag/ порожня ──
    private void loadHardcodedSamples() {
        List<String> samples = List.of(

            // РАПОРТ — зразок 1: щорічна відпустка
            """
            ПРАВИЛА СКЛАДАННЯ РАПОРТУ (Інструкція з діловодства ЗСУ, Наказ МО № 40):
            Рапорт — особисте звернення військовослужбовця до прямого командира.
            Обов'язкова структура:
            - Адресат: посада, звання, прізвище та ініціали командира (правий верхній кут)
            - Від кого: звання, прізвище та ім'я автора у родовому відмінку
            - Вступна фраза: "Доповідаю, що прошу..." або "Доповідаю, що..."
            - Суть прохання з обґрунтуванням
            - Дата складання (ліворуч) та підпис автора (праворуч)
            Тон — офіційно-діловий, без зайвих слів.
            """,

            // РАПОРТ — зразок 2: відпустка
            """
            ТИПОВІ ФОРМУЛЮВАННЯ РАПОРТУ:
            - Відпустка: "Доповідаю, що прошу надати мені щорічну відпустку
              тривалістю ___ діб з ___ року у зв'язку з ___"
            - Переведення: "Доповідаю, що прошу розглянути питання щодо
              мого переведення до ___"
            - Медична: "Доповідаю, що прошу надати відпустку за станом
              здоров'я на підставі довідки ___"
            - Виїзд: "Доповідаю, що прошу дозволити виїзд за межі розташування
              частини ___ у зв'язку з ___. Зобов'язуюся прибути ___"
            """,

            // РАПОРТ — зразок 3: вимоги ДСТУ
            """
            ВИМОГИ ДСТУ 4163:2020 ДО РАПОРТУ:
            - Шрифт Times New Roman 14pt, інтервал 1.5
            - Поля: ліве 30мм, праве 15мм, верх/низ 20мм
            - Адресат — у правому верхньому куті
            - Від кого — у родовому відмінку під адресатом
            - Дата складання: ДД місяць РРРР року
            - Підпис: звання, підпис, ініціали та прізвище
            """,

            // МЕТОДИЧНА РОЗРОБКА — зразок 1: структура
            """
            ПРАВИЛА СКЛАДАННЯ МЕТОДИЧНОЇ РОЗРОБКИ (вимоги ВВНЗ):
            Методична розробка — навчально-методичний документ викладача.
            Обов'язкова структура:
            - Вид заняття: лекційне / практичне / семінарське
            - Навчальна дисципліна та тема заняття
            - Мета: навчальна (знати) та виховна (розуміти)
            - Час та метод проведення, місце проведення
            - Матеріально-технічне забезпечення
            - Хід заняття: І. Вступна (10 хв), ІІ. Основна (70 хв), ІІІ. Заключна (10 хв)
            - Список літератури (основна та додаткова) за ДСТУ 8302:2015
            """,

            // МЕТОДИЧНА РОЗРОБКА — зразок 2: типові формулювання
            """
            ТИПОВІ ФОРМУЛЮВАННЯ МЕТОДИЧНОЇ РОЗРОБКИ:
            - Навчальна мета: "Навчальна мета: ознайомити курсантів з..."
            - Виховна мета: "Виховна мета: сформувати розуміння..."
            - Вступна частина: "Перевірка наявності особового складу.
              Оголошення теми та мети заняття. Зв'язок з попередньою темою."
            - Заключна частина: "Підведення підсумків заняття.
              Відповіді на запитання. Завдання для самопідготовки."
            - Нумерація сторінок починається з другої
            - Додатки нумеруються літерами: Додаток А, Додаток Б
            """
        );

        DocumentSplitter splitter = DocumentSplitters.recursive(400, 80);
        for (String sample : samples) {
            List<TextSegment> segments = splitter.split(Document.from(sample));
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }
        log.info("Завантажено {} вбудованих зразків (fallback)", samples.size());
    }

    // пошук — maxResults=3, minScore=0.45
    public RagResult findRelevantContext(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(2)
                        .minScore(0.45)
                        .build()
                ).matches();

        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : matches) {
            // Фільтруємо технічні роздільники щоб вони не потрапляли у промпт і UI
            String cleaned = match.embedded().text().lines()
                    .filter(l -> !l.strip().startsWith("════") && !l.strip().equals("---")
                            && !l.strip().startsWith("ТИП:"))
                    .collect(java.util.stream.Collectors.joining("\n"));
            context.append(cleaned.strip()).append("\n\n");
        }

        log.info("RAG: знайдено {} релевантних фрагментів (score≥0.45, max=3)", matches.size());
        return new RagResult(context.toString().strip(), matches.size());
    }
}
