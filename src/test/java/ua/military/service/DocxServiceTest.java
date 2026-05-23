package ua.military.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тести генерації .docx файлів через DocxService.
 * Перевіряємо що файл створюється, не порожній і є валідним .docx.
 */
class DocxServiceTest {

    private final DocxService docxService = new DocxService();
    private String generatedFilePath;

    @AfterEach
    void cleanup() {
        // Видаляємо тестовий файл після кожного тесту
        if (generatedFilePath != null) {
            new File(generatedFilePath).delete();
        }
    }

    @Test
    @DisplayName("Генерація рапорту — файл створюється і не порожній")
    void generateDocx_rapor_createsNonEmptyFile() {
        generatedFilePath = docxService.generateDocx(
                "Доповідаю, що прошу надати відпустку тривалістю 3 доби.",
                "рапорт",
                "підполковник Коваленко О.В.",
                "командир в/ч А0000"
        );

        File file = new File(generatedFilePath);
        assertTrue(file.exists(), "Файл повинен існувати");
        assertTrue(file.length() > 0, "Файл не повинен бути порожнім");
        assertTrue(generatedFilePath.endsWith(".docx"), "Розширення файлу має бути .docx");
    }

    @Test
    @DisplayName("Згенерований файл є валідним .docx (XWPFDocument)")
    void generateDocx_producesValidDocxFormat() throws Exception {
        generatedFilePath = docxService.generateDocx(
                "Звітую про виконану роботу за семестр. Проведено 14 лекцій.",
                "звіт",
                "доцент Іваненко І.І.",
                "начальник кафедри"
        );

        // Відкриваємо файл через Apache POI — якщо не валідний .docx, кине виняток
        try (FileInputStream fis = new FileInputStream(generatedFilePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            assertNotNull(doc, "Документ повинен відкриватись як XWPFDocument");
            assertTrue(doc.getParagraphs().size() > 0, "Документ повинен містити параграфи");
        }
    }

    @Test
    @DisplayName("Адресат 'невідомо' не потрапляє в документ")
    void generateDocx_unknownRecipient_notIncluded() throws Exception {
        generatedFilePath = docxService.generateDocx(
                "Методична розробка з дисципліни.",
                "методична розробка",
                "викладач Петренко О.В.",
                "невідомо"
        );

        try (FileInputStream fis = new FileInputStream(generatedFilePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            boolean hasNevIdomo = doc.getParagraphs().stream()
                    .anyMatch(p -> p.getText().equalsIgnoreCase("невідомо"));
            assertFalse(hasNevIdomo, "Слово 'невідомо' не повинно бути в документі");
        }
    }

    @Test
    @DisplayName("Заголовок документа містить тип великими літерами")
    void generateDocx_titleIsUpperCase() throws Exception {
        generatedFilePath = docxService.generateDocx(
                "Наказую: призначити капітана на посаду.",
                "наказ",
                "полковник Шевченко М.І.",
                ""
        );

        try (FileInputStream fis = new FileInputStream(generatedFilePath);
             XWPFDocument doc = new XWPFDocument(fis)) {
            boolean hasTitle = doc.getParagraphs().stream()
                    .anyMatch(p -> p.getText().equals("НАКАЗ"));
            assertTrue(hasTitle, "Заголовок 'НАКАЗ' повинен бути в документі");
        }
    }
}
