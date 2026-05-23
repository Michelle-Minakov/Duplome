package ua.military.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.military.agent.AnalystAgent;
import ua.military.agent.EditorAgent;
import ua.military.repository.DocumentRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock private AnalystAgent       analystAgent;
    @Mock private EditorAgent        editorAgent;
    @Mock private DocxService        docxService;
    @Mock private RagService         ragService;
    @Mock private StatisticsService  statisticsService;
    @Mock private DocumentRepository documentRepository;

    @InjectMocks
    private OrchestratorService orchestratorService;

    private static final String SAMPLE_JSON = """
            {"documentType":"звіт","author":"доцент Іваненко І.І.",
             "date":"15.01.2025","recipient":"начальник кафедри",
             "subject":"результати роботи","keyPoints":["14 лекцій","2 статті"]}
            """;

    private static final String     RAG_CONTEXT = "Звіт — документ про виконання планових показників.";
    private static final RagResult  RAG_RESULT  = new RagResult(RAG_CONTEXT, 2);
    private static final RagResult  RAG_EMPTY   = new RagResult("", 0);

    @Test
    @DisplayName("Конвеєр викликає всі 4 кроки по порядку")
    void generateDocument_callsAllStepsInOrder() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(editorAgent.rewrite(anyString(), anyString())).thenReturn("Звітую про виконану роботу.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        orchestratorService.generateDocument("тестові нотатки");

        verify(analystAgent, times(1)).analyze(anyString());
        verify(ragService,   times(1)).findRelevantContext(anyString());
        verify(editorAgent,  times(1)).rewrite(anyString(), anyString());
        verify(docxService,  times(1)).generateDocx(anyString(), anyString(),
                                                     anyString(), anyString());
    }

    @Test
    @DisplayName("Результат містить ім'я файлу і текст документа")
    void generateDocument_resultContainsFileNameAndText() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(editorAgent.rewrite(anyString(), anyString())).thenReturn("Звітую про виконану роботу.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/document_123.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки для звіту");

        assertEquals("document_123.docx", result.fileName());
        assertEquals("Звітую про виконану роботу.", result.text());
        assertTrue(result.processingTimeMs() >= 0);
    }

    @Test
    @DisplayName("Метрики: ragFragmentsUsed, ragContextFound, detectedDocumentType")
    void generateDocument_metricsAreCorrect() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(editorAgent.rewrite(anyString(), anyString())).thenReturn("Звітую.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки");

        assertEquals(2,      result.ragFragmentsUsed(),   "Має бути 2 фрагменти з RAG_RESULT");
        assertTrue(result.ragContextFound(),               "RAG контекст знайдено");
        assertEquals("звіт", result.detectedDocumentType(), "Тип документа — звіт");
    }

    @Test
    @DisplayName("Метрики: ragContextFound=false коли RAG не знайшов нічого")
    void generateDocument_noRagContext_metricsShowFalse() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_EMPTY);
        when(editorAgent.rewrite(anyString(), anyString())).thenReturn("Звітую.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки");

        assertEquals(0,     result.ragFragmentsUsed());
        assertFalse(result.ragContextFound());
    }

    @Test
    @DisplayName("EditorAgent отримує JSON від AnalystAgent та RAG-контекст")
    void generateDocument_editorReceivesAnalystOutputAndRagContext() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(editorAgent.rewrite(anyString(), anyString())).thenReturn("Звітую.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        orchestratorService.generateDocument("будь-який текст");

        verify(editorAgent).rewrite(SAMPLE_JSON, RAG_CONTEXT);
    }

    @Test
    @DisplayName("Помилка AnalystAgent — кидає RuntimeException")
    void generateDocument_whenAnalystFails_throwsException() {
        when(analystAgent.analyze(anyString()))
                .thenThrow(new RuntimeException("Ollama недоступна"));

        assertThrows(RuntimeException.class,
                () -> orchestratorService.generateDocument("тест"));
    }
}
