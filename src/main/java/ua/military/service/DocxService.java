package ua.military.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Service;

import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

@Slf4j
@Service
public class DocxService {

    private static final String OUTPUT_DIR  = "generated/";
    private static final String FONT        = "Times New Roman";
    private static final int    FONT_SIZE   = 14;
    private static final int    FONT_SIZE_TABLE = 12;

    // Береги за ДСТУ 4163:2020 (1 мм ≈ 56.7 twips)
    private static final long MARGIN_LEFT    = 1701; // 30 мм
    private static final long MARGIN_RIGHT   =  851; // 15 мм
    private static final long MARGIN_TOP     = 1134; // 20 мм
    private static final long MARGIN_BOTTOM  = 1134; // 20 мм

    private static final long LINE_SPACING      = 360; // 1.5 рядки
    private static final long FIRST_LINE_INDENT = 709; // 1.25 см

    // Ширина колонок таблиці план-конспекту (twips); загальна = 9354
    private static final int COL_TIME    = 1200;
    private static final int COL_CONTENT = 6000;
    private static final int COL_METHOD  = 2154;

    public String generateDocx(String documentText, String documentType,
                                String author, String recipient) {
        new java.io.File(OUTPUT_DIR).mkdirs();
        String fileName = OUTPUT_DIR + "document_" + java.util.UUID.randomUUID() + ".docx";

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc);

            // Адресат — правий верхній кут
            if (recipient != null && !recipient.isBlank()
                    && !recipient.equalsIgnoreCase("невідомо")) {
                addLine(doc, recipient, ParagraphAlignment.RIGHT, false);
                addEmptyLine(doc);
            }

            // Назва виду документа — по центру, жирний
            addLine(doc, documentType.toUpperCase(), ParagraphAlignment.CENTER, true);

            // Дата та номер — по центру
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            addLine(doc, "№ ___ від " + date, ParagraphAlignment.CENTER, false);
            addEmptyLine(doc);

            // Тіло — залежить від типу документа
            String type = documentType.toLowerCase().strip();
            switch (type) {
                case "наказ"        -> buildNakazBody(doc, documentText);
                case "план-конспект" -> buildPlanKonspektBody(doc, documentText);
                default             -> buildDefaultBody(doc, documentText);
            }

            // Підпис
            addEmptyLine(doc);
            addEmptyLine(doc);
            addLine(doc, author, ParagraphAlignment.LEFT, false);

            try (FileOutputStream out = new FileOutputStream(fileName)) {
                doc.write(out);
            }
            log.info("Документ збережено: {}", fileName);
            return fileName;

        } catch (IOException e) {
            log.error("Помилка генерації документа: {}", e.getMessage());
            throw new RuntimeException("Не вдалося згенерувати документ", e);
        }
    }

    /**
     * Простий серверний парсер файлів: підтримує .docx, .pdf і .txt (як буфер).
     * Повертає виділений простий текст для підстановки у поле нотаток.
     */
    public String parseUploadedFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return "";
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        try (InputStream in = file.getInputStream()) {
            if (name.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(in)) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph p : doc.getParagraphs()) {
                        String t = p.getText();
                        if (t != null && !t.isBlank()) sb.append(t.strip()).append("\n\n");
                    }
                    return sb.toString().trim();
                }
            } else if (name.endsWith(".pdf")) {
                try (PDDocument pd = PDDocument.load(in)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String txt = stripper.getText(pd);
                    return txt == null ? "" : txt.trim();
                }
            } else {
                // fallback: treat as text
                return new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            }
        } catch (IOException e) {
            log.error("Помилка парсингу файлу {}: {}", name, e.getMessage());
            return "";
        }
    }

    // ── Стандартне тіло (рапорт, звіт, доповідна, методична розробка тощо) ──
    private void buildDefaultBody(XWPFDocument doc, String text) {
        for (String block : text.split("\\n\\n+")) {
            String trimmed = block.strip();
            if (!trimmed.isBlank()) {
                addBodyBlock(doc, trimmed);
            }
        }
    }

    // ── Наказ: НАКАЗУЮ: жирний + нумеровані пункти з відступом ──
    private void buildNakazBody(XWPFDocument doc, String text) {
        for (String line : text.split("\\n")) {
            String t = line.strip();
            if (t.isEmpty()) continue;

            String upper = t.toUpperCase();
            if (upper.startsWith("НАКАЗУЮ")) {
                // "НАКАЗУЮ:" — жирний, ліворуч, без абзацного відступу
                addLine(doc, t, ParagraphAlignment.LEFT, true);
            } else if (t.matches("^\\d+\\..*")) {
                // Нумерований пункт — відступ зліва 1.25 см
                addNumberedItem(doc, t);
            } else {
                addBodyBlock(doc, t);
            }
        }
    }

    // ── План-конспект: текст + таблиця "Хід заняття" ──
    private void buildPlanKonspektBody(XWPFDocument doc, String text) {
        // Спочатку виводимо весь згенерований текст (Тема, Мета, Час…)
        buildDefaultBody(doc, text);

        // Потім таблиця ходу заняття
        addEmptyLine(doc);
        addLine(doc, "ХІД ЗАНЯТТЯ", ParagraphAlignment.CENTER, true);
        addEmptyLine(doc);

        XWPFTable table = doc.createTable(4, 3);
        setTableWidth(table, COL_TIME + COL_CONTENT + COL_METHOD);

        // Заголовок
        fillTableRow(table, 0,
                List.of("Час (хв)", "Зміст заняття", "Метод навчання"),
                true);
        setRowWidths(table, 0, COL_TIME, COL_CONTENT, COL_METHOD);

        // Вступна частина
        fillTableRow(table, 1,
                List.of("5–10",
                        "І. Вступна частина.\nПривітання, перевірка присутніх, " +
                        "оголошення теми та мети заняття.",
                        "Організаційний"),
                false);
        setRowWidths(table, 1, COL_TIME, COL_CONTENT, COL_METHOD);

        // Основна частина — вставляємо тему з тексту
        String mainContent = extractFirstSentence(text);
        fillTableRow(table, 2,
                List.of("70–75",
                        "ІІ. Основна частина.\n" + mainContent,
                        "Лекція / демонстрація"),
                false);
        setRowWidths(table, 2, COL_TIME, COL_CONTENT, COL_METHOD);

        // Заключна частина
        fillTableRow(table, 3,
                List.of("5–10",
                        "ІІІ. Заключна частина.\nПідведення підсумків, " +
                        "відповіді на запитання, завдання для самопідготовки.",
                        "Фронтальне опитування"),
                false);
        setRowWidths(table, 3, COL_TIME, COL_CONTENT, COL_METHOD);

        applyTableBorders(table);
    }

    // ── Нумерований пункт наказу ──
    private void addNumberedItem(XWPFDocument doc, String text) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(ParagraphAlignment.BOTH);
        applySpacing(para);

        CTPPr pPr = para.getCTP().isSetPPr()
                ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setLeft(BigInteger.valueOf(FIRST_LINE_INDENT));

        XWPFRun run = para.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(FONT_SIZE);
        run.setText(text);
    }

    // ── Таблиця: заповнення рядка ──
    private void fillTableRow(XWPFTable table, int row,
                               List<String> cells, boolean bold) {
        for (int col = 0; col < cells.size(); col++) {
            XWPFTableCell cell = table.getRow(row).getCell(col);
            // Очищаємо дефолтний порожній абзац
            while (!cell.getParagraphs().isEmpty()) {
                cell.removeParagraph(0);
            }
            XWPFParagraph para = cell.addParagraph();
            para.setAlignment(ParagraphAlignment.LEFT);
            applySpacing(para);
            XWPFRun run = para.createRun();
            run.setFontFamily(FONT);
            run.setFontSize(FONT_SIZE_TABLE);
            run.setBold(bold);
            run.setText(cells.get(col));
        }
    }

    // ── Таблиця: ширини колонок ──
    private void setRowWidths(XWPFTable table, int row,
                               int w0, int w1, int w2) {
        int[] widths = {w0, w1, w2};
        for (int col = 0; col < 3; col++) {
            XWPFTableCell cell = table.getRow(row).getCell(col);
            CTTcPr tcPr = cell.getCTTc().isSetTcPr()
                    ? cell.getCTTc().getTcPr()
                    : cell.getCTTc().addNewTcPr();
            CTTblWidth cellWidth = tcPr.isSetTcW()
                    ? tcPr.getTcW() : tcPr.addNewTcW();
            cellWidth.setType(STTblWidth.DXA);
            cellWidth.setW(BigInteger.valueOf(widths[col]));
        }
    }

    // ── Таблиця: загальна ширина ──
    private void setTableWidth(XWPFTable table, int totalWidth) {
        CTTbl    tbl    = table.getCTTbl();
        CTTblPr  tblPr  = tbl.getTblPr() != null  ? tbl.getTblPr()  : tbl.addNewTblPr();
        CTTblWidth tblW = tblPr.getTblW() != null  ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.DXA);
        tblW.setW(BigInteger.valueOf(totalWidth));
    }

    // ── Таблиця: рамки ──
    private void applyTableBorders(XWPFTable table) {
        CTTbl       tbl     = table.getCTTbl();
        CTTblPr     tblPr   = tbl.getTblPr()         != null ? tbl.getTblPr()         : tbl.addNewTblPr();
        CTTblBorders borders = tblPr.getTblBorders()  != null ? tblPr.getTblBorders()  : tblPr.addNewTblBorders();
        setBorder(borders.getTop()     != null ? borders.getTop()     : borders.addNewTop());
        setBorder(borders.getBottom()  != null ? borders.getBottom()  : borders.addNewBottom());
        setBorder(borders.getLeft()    != null ? borders.getLeft()    : borders.addNewLeft());
        setBorder(borders.getRight()   != null ? borders.getRight()   : borders.addNewRight());
        setBorder(borders.getInsideH() != null ? borders.getInsideH() : borders.addNewInsideH());
        setBorder(borders.getInsideV() != null ? borders.getInsideV() : borders.addNewInsideV());
    }

    private void setBorder(CTBorder b) {
        b.setVal(STBorder.SINGLE);
        b.setSz(BigInteger.valueOf(4));
        b.setColor("000000");
    }

    // ── Допоміжна: перше речення тексту для таблиці ──
    private String extractFirstSentence(String text) {
        if (text == null || text.isBlank()) return "";
        String flat = text.replace('\n', ' ').strip();
        int dot = flat.indexOf('.');
        return (dot > 0 && dot < flat.length() - 1)
                ? flat.substring(0, dot + 1)
                : (flat.length() > 120 ? flat.substring(0, 120) + "…" : flat);
    }

    // ── Базові методи ──
    private void setPageMargins(XWPFDocument doc) {
        CTBody body = doc.getDocument().getBody();
        CTSectPr sectPr = body.isSetSectPr() ? body.getSectPr() : body.addNewSectPr();
        CTPageMar pgMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        pgMar.setLeft(BigInteger.valueOf(MARGIN_LEFT));
        pgMar.setRight(BigInteger.valueOf(MARGIN_RIGHT));
        pgMar.setTop(BigInteger.valueOf(MARGIN_TOP));
        pgMar.setBottom(BigInteger.valueOf(MARGIN_BOTTOM));
    }

    private void addLine(XWPFDocument doc, String text,
                         ParagraphAlignment align, boolean bold) {
        XWPFParagraph para = doc.createParagraph();
        para.setAlignment(align);
        applySpacing(para);
        XWPFRun run = para.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(FONT_SIZE);
        run.setBold(bold);
        run.setText(text);
    }

    private void addBodyBlock(XWPFDocument doc, String text) {
        for (String line : text.split("\\n")) {
            String t = line.strip();
            if (t.isEmpty()) continue;

            boolean isList = t.startsWith("-") || t.startsWith("•");
            XWPFParagraph para = doc.createParagraph();
            para.setAlignment(isList ? ParagraphAlignment.LEFT : ParagraphAlignment.BOTH);
            applySpacing(para);

            if (!isList) {
                CTPPr pPr = para.getCTP().isSetPPr()
                        ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
                CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
                ind.setFirstLine(BigInteger.valueOf(FIRST_LINE_INDENT));
            }

            XWPFRun run = para.createRun();
            run.setFontFamily(FONT);
            run.setFontSize(FONT_SIZE);
            run.setText(t);
        }
    }

    private void addEmptyLine(XWPFDocument doc) {
        XWPFParagraph para = doc.createParagraph();
        applySpacing(para);
        XWPFRun run = para.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(FONT_SIZE);
    }

    private void applySpacing(XWPFParagraph para) {
        CTPPr pPr = para.getCTP().isSetPPr()
                ? para.getCTP().getPPr() : para.getCTP().addNewPPr();
        CTSpacing spacing = pPr.isSetSpacing()
                ? pPr.getSpacing() : pPr.addNewSpacing();
        spacing.setLine(BigInteger.valueOf(LINE_SPACING));
        spacing.setLineRule(STLineSpacingRule.AUTO);
    }
}
