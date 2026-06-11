package ua.military.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ua.military.agent.AnalystAgent;
import ua.military.agent.MethodychnaEditorAgent;
import ua.military.agent.RaportEditorAgent;
import ua.military.repository.DocumentRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrchestratorServiceTest {

    @Mock private AnalystAgent           analystAgent;
    @Mock private RaportEditorAgent      raportEditorAgent;
    @Mock private MethodychnaEditorAgent methodychnaEditorAgent;
    @Mock private DocxService            docxService;
    @Mock private RagService             ragService;
    @Mock private DocumentRepository     documentRepository;

    @InjectMocks
    private OrchestratorService orchestratorService;

    // 4 поля — рівно як повертає AnalystAgent (documentType, author, recipient, subject)
    private static final String SAMPLE_JSON = """
            {"documentType":"рапорт","author":"матрос Іваненко І.І.",
             "recipient":"командиру частини","subject":"щорічна відпустка"}
            """;

    private static final String    RAG_CONTEXT = "Рапорт — службовий документ, яким військовослужбовець звертається до командира.";
    private static final RagResult RAG_RESULT  = new RagResult(RAG_CONTEXT, 2);
    private static final RagResult RAG_EMPTY   = new RagResult("", 0);

    @Test
    @DisplayName("Конвеєр викликає всі 4 кроки по порядку")
    void generateDocument_callsAllStepsInOrder() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(raportEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Доповідаю, що прошу надати відпустку.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        orchestratorService.generateDocument("тестові нотатки");

        verify(analystAgent,      times(1)).analyze(anyString());
        verify(ragService,        times(1)).findRelevantContext(anyString());
        verify(raportEditorAgent, times(1)).rewrite(anyString(), anyString(), anyString());
        verify(docxService,       times(1)).generateDocx(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Результат містить ім'я файлу і текст документа")
    void generateDocument_resultContainsFileNameAndText() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(raportEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Доповідаю, що прошу надати відпустку.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/document_123.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки для рапорту");

        assertEquals("document_123.docx",                      result.fileName());
        assertEquals("Доповідаю, що прошу надати відпустку.", result.text());
        assertTrue(result.processingTimeMs() >= 0);
    }

    @Test
    @DisplayName("Метрики: ragFragmentsUsed, ragContextFound, detectedDocumentType")
    void generateDocument_metricsAreCorrect() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(raportEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Доповідаю.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки");

        assertEquals(2,        result.ragFragmentsUsed(),     "Має бути 2 фрагменти з RAG_RESULT");
        assertTrue(result.ragContextFound(),                   "RAG контекст знайдено");
        assertEquals("рапорт", result.detectedDocumentType(), "Тип документа — рапорт");
    }

    @Test
    @DisplayName("Метрики: ragContextFound=false коли RAG не знайшов нічого")
    void generateDocument_noRagContext_metricsShowFalse() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_EMPTY);
        when(raportEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Доповідаю.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        GenerationResponse result = orchestratorService.generateDocument("нотатки");

        assertEquals(0,    result.ragFragmentsUsed());
        assertFalse(result.ragContextFound());
    }

    @Test
    @DisplayName("RaportEditorAgent отримує RAG-контекст від RagService")
    void generateDocument_editorReceivesAnalystOutputAndRagContext() {
        when(analystAgent.analyze(anyString())).thenReturn(SAMPLE_JSON);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(raportEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Доповідаю.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        orchestratorService.generateDocument("будь-який текст");

        // Перевіряємо що ragContext передається другим аргументом
        verify(raportEditorAgent).rewrite(anyString(), eq(RAG_CONTEXT), anyString());
    }

    @Test
    @DisplayName("Помилка AnalystAgent — кидає RuntimeException")
    void generateDocument_whenAnalystFails_throwsException() {
        when(analystAgent.analyze(anyString()))
                .thenThrow(new RuntimeException("Ollama недоступна"));

        assertThrows(RuntimeException.class,
                () -> orchestratorService.generateDocument("тест"));
    }

    @Test
    @DisplayName("MethodychnaEditorAgent викликається для типу 'методична розробка'")
    void generateDocument_methodychnaType_callsMethodychnaEditor() {
        String methodychnaJson = """
                {"documentType":"методична розробка","author":"старший викладач Петренко О.В.",
                 "recipient":"начальник кафедри","subject":"застосування AI у документообігу"}
                """;
        when(analystAgent.analyze(anyString())).thenReturn(methodychnaJson);
        when(ragService.findRelevantContext(anyString())).thenReturn(RAG_RESULT);
        when(methodychnaEditorAgent.rewrite(anyString(), anyString(), anyString()))
                .thenReturn("Методична розробка лекції з AI.");
        when(docxService.generateDocx(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("generated/test.docx");

        orchestratorService.generateDocument("методична розробка лекції з AI");

        verify(methodychnaEditorAgent, times(1)).rewrite(anyString(), anyString(), anyString());
        verify(raportEditorAgent,      never()).rewrite(anyString(), anyString(), anyString());
    }
}
