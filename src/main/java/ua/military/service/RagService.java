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
        // Тільки структурні правила — без зразків тексту, щоб модель не копіювала
        List<String> samples = List.of(

            // Загальні вимоги
            """
            Правила оформлення документів (ДСТУ 4163:2020):
            Шрифт Times New Roman 14 пт, інтервал 1.5, поля: ліве 30 мм, праве 15 мм.
            Назва виду документа — по центру, великі літери, жирний шрифт.
            Адресат — у правому верхньому куті (посада, звання, прізвище та ініціали).
            Підпис — посада автора, прізвище та ініціали — під текстом.
            """,

            // Звіт — лише структура
            """
            Звіт: документ про виконання планів за звітний період.
            Обов'язкові елементи тексту звіту:
            - вступна фраза: "Звітую про виконану роботу за [період]."
            - перелік виконаних завдань з конкретними показниками (кількість занять, статей тощо)
            - підсумкова фраза про виконання планових показників
            Дата, автор, адресат — з вхідних даних документа.
            """,

            // Рапорт — лише структура
            """
            Рапорт: особистий документ військовослужбовця з проханням до командира.
            Обов'язкові елементи тексту рапорту:
            - вступна фраза: "Доповідаю, що прошу [суть прохання]."
            - конкретне прохання з обґрунтуванням
            - заключна фраза: "Прошу розглянути та прийняти рішення."
            Дата, автор, адресат — з вхідних даних документа.
            """,

            // Доповідна записка — лише структура
            """
            Доповідна записка: інформаційний документ до керівника.
            Обов'язкові елементи тексту:
            - вступна фраза: "Доповідаю, що [суть ситуації]."
            - виклад проблеми або інформації
            - пропозиції або прохання
            Дата, автор, адресат — з вхідних даних документа.
            """,

            // Наказ — лише структура
            """
            Наказ: розпорядчий документ командира.
            Обов'язкові елементи тексту наказу:
            - розпорядча частина починається словом "НАКАЗУЮ:"
            - нумеровані пункти: кожен містить дію, виконавця і строк
            - останній пункт: "Контроль за виконанням наказу залишаю за собою."
            Виконавець, строки, зміст дій — виключно з вхідних даних JSON.
            """,

            // Методична розробка — лише структура
            """
            Методична розробка: навчально-методичний документ.
            Обов'язкові елементи тексту:
            - вступна фраза: "Методична розробка присвячена [темі]."
            - цільова аудиторія (курсанти, слухачі тощо)
            - короткий опис змісту (теорія, практика, завдання)
            Дата, автор — з вхідних даних документа.
            """,

            // План-конспект — лише структура
            """
            План-конспект: документ для проведення навчального заняття.
            Обов'язкові елементи тексту:
            - "Тема заняття: [назва теми]."
            - "Мета: [що засвоять курсанти]."
            - вид заняття та тривалість (хвилини)
            - основні навчальні питання
            Тема, мета, час — з вхідних даних документа.
            """,

            // Пояснювальна записка — лише структура
            """
            Пояснювальна записка до дипломного проєкту.
            Обов'язкові елементи тексту:
            - вступна фраза: "Пояснювальна записка до дипломного проєкту на тему [назва]."
            - актуальність теми та мета роботи
            - короткий опис розробленої системи або рішення
            Тема, автор, науковий керівник — з вхідних даних документа.
            """
        );

        DocumentSplitter splitter = DocumentSplitters.recursive(300, 50);

        for (String sample : samples) {
            Document doc = Document.from(sample);
            List<TextSegment> segments = splitter.split(doc);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }

        log.info("Завантажено {} структурних правил діловодства ЗСУ", samples.size());
    }

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
            context.append(match.embedded().text()).append("\n\n");
        }

        log.info("RAG: знайдено {} релевантних фрагментів (score >= 0.45)", matches.size());
        return new RagResult(context.toString().strip(), matches.size());
    }
}
