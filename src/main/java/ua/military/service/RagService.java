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
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class RagService {

    private EmbeddingModel embeddingModel;
    private EmbeddingStore<TextSegment> embeddingStore;

    @PostConstruct
    public void init() {
        log.info("Ініціалізація RAG сервісу...");
        embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        embeddingStore = new InMemoryEmbeddingStore<>();
        loadDocuments();
        log.info("RAG сервіс готовий.");
    }

    private void loadDocuments() {
        // Еталонні зразки військових документів
        List<String> samples = List.of(
            """
            РАПОРТ
            Командиру військової частини А0000
            капітана Мельника Івана Васильовича
            
            Доповідаю, що прошу надати мені щорічну відпустку тривалістю 10 діб
            з 01 серпня 2024 року по 10 серпня 2024 року
            у зв'язку із сімейними обставинами.
            """,
            """
            ДОПОВІДНА ЗАПИСКА
            Начальнику штабу військової частини
            
            Доповідаю, що особовий склад підрозділу у кількості 25 осіб
            прибув до місця призначення у повному складі.
            Втрати відсутні. Техніка справна.
            """,
            """
            НАКАЗ № 001
            по військовій частині А0000
            від 01 січня 2024 року
            
            Наказую: призначити капітана Коваленка О.В. виконуючим обов'язки
            начальника служби з 01 січня 2024 року.
            """,
            """
            ЗВІТ
            про проведення бойового навчання
            
            У період з 15 по 20 грудня 2023 року проведено планове бойове навчання.
            Особовий склад показав задовільний рівень підготовки.
            Всі завдання виконані в повному обсязі.
            """,
            """
            Відповідно до вимог ДСТУ 4163:2020 документи військового діловодства
            повинні містити такі реквізити: найменування організації, назва виду документа,
            дата, реєстраційний номер, заголовок до тексту, текст, підпис.
            """
        );

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

        for (String sample : samples) {
            Document doc = Document.from(sample);
            List<TextSegment> segments = splitter.split(doc);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }

        log.info("Завантажено {} зразків документів у векторну базу", samples.size());
    }

    public String findRelevantContext(String query) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        // Новий API 0.31.0 (замість deprecated findRelevant)
        List<EmbeddingMatch<TextSegment>> matches =
                embeddingStore.search(
                    dev.langchain4j.store.embedding.EmbeddingSearchRequest.builder()
                        .queryEmbedding(queryEmbedding)
                        .maxResults(2)
                        .minScore(0.5)
                        .build()
                ).matches();

        StringBuilder context = new StringBuilder();
        for (EmbeddingMatch<TextSegment> match : matches) {
            context.append(match.embedded().text()).append("\n\n");
        }

        return context.toString().strip();
    }
}