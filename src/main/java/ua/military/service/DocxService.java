package ua.military.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class DocxService {

    private static final String OUTPUT_DIR = "generated/";

    public String generateDocx(String documentText, String documentType, String author) {
        // Створюємо папку якщо не існує
        new java.io.File(OUTPUT_DIR).mkdirs();

        String fileName = OUTPUT_DIR + "document_" +
                System.currentTimeMillis() + ".docx";

        try (XWPFDocument document = new XWPFDocument()) {

            // Заголовок документа
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(documentType.toUpperCase());
            titleRun.setBold(true);
            titleRun.setFontSize(14);
            titleRun.setFontFamily("Times New Roman");

            // Номер та дата
            XWPFParagraph numberParagraph = document.createParagraph();
            numberParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun numberRun = numberParagraph.createRun();
            String date = LocalDate.now()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            numberRun.setText("№ ___ від " + date);
            numberRun.setFontSize(12);
            numberRun.setFontFamily("Times New Roman");

            // Порожній рядок
            document.createParagraph();

            // Основний текст документа
            XWPFParagraph bodyParagraph = document.createParagraph();
            bodyParagraph.setAlignment(ParagraphAlignment.BOTH);
            bodyParagraph.setIndentationFirstLine(720); // абзац
            XWPFRun bodyRun = bodyParagraph.createRun();
            bodyRun.setText(documentText);
            bodyRun.setFontSize(12);
            bodyRun.setFontFamily("Times New Roman");

            // Порожній рядок перед підписом
            document.createParagraph();

            // Підпис
            XWPFParagraph signParagraph = document.createParagraph();
            signParagraph.setAlignment(ParagraphAlignment.LEFT);
            XWPFRun signRun = signParagraph.createRun();
            signRun.setText(author);
            signRun.setFontSize(12);
            signRun.setFontFamily("Times New Roman");

            // Зберігаємо файл
            try (FileOutputStream out = new FileOutputStream(fileName)) {
                document.write(out);
            }

            log.info("Документ збережено: {}", fileName);
            return fileName;

        } catch (IOException e) {
            log.error("Помилка генерації документа: {}", e.getMessage());
            throw new RuntimeException("Не вдалося згенерувати документ", e);
        }
    }
}