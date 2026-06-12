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
        String fileName = OUTPUT_DIR + makeFileName(documentType);

        try (XWPFDocument doc = new XWPFDocument()) {
            setPageMargins(doc);

            String type = documentType.toLowerCase().strip();
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));

            if (type.equals("рапорт")) {
                // Рапорт: LLM генерує повний текст включно з адресатом і РАПОРТ
                // DocxService лише форматує кожен рядок за структурою
                buildRaportDoc(doc, documentText, date);
            } else if (type.equals("методична розробка")) {
                // Методична: заголовок по центру без номера, тіло без дублювання
                addLine(doc, "МЕТОДИЧНА РОЗРОБКА", ParagraphAlignment.CENTER, true);
                addEmptyLine(doc);
                // Прибираємо перший рядок "МЕТОДИЧНА РОЗРОБКА" якщо модель його додала
                String bodyText = stripLeadingTitle(documentText, "методична розробка");
                buildDefaultBody(doc, bodyText);
            } else {
                // Інші типи (наказ, план-конспект тощо): заголовок + номер + тіло
                if (recipient != null && !recipient.isBlank()
                        && !recipient.equalsIgnoreCase("невідомо")) {
                    addLine(doc, recipient, ParagraphAlignment.RIGHT, false);
                    addEmptyLine(doc);
                }
                addLine(doc, documentType.toUpperCase(), ParagraphAlignment.CENTER, true);
                addLine(doc, "№ ___ від " + date, ParagraphAlignment.CENTER, false);
                addEmptyLine(doc);

                switch (type) {
                    case "наказ"         -> buildNakazBody(doc, documentText);
                    case "план-конспект" -> buildPlanKonspektBody(doc, documentText);
                    default              -> buildDefaultBody(doc, documentText);
                }
                addEmptyLine(doc);
                addEmptyLine(doc);
                addLine(doc, author, ParagraphAlignment.LEFT, false);
            }

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

    // ── Рапорт: розумне форматування LLM-виводу ──
    private void buildRaportDoc(XWPFDocument doc, String text, String date) {
        String[] lines = text.split("\n");

        // Знаходимо рядок РАПОРТ
        int raportIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().equalsIgnoreCase("рапорт")) {
                raportIdx = i;
                break;
            }
        }
        if (raportIdx == -1) {
            // Якщо РАПОРТ не знайдено — форматуємо як звичайний текст
            buildDefaultBody(doc, text);
            return;
        }

        // Рядки ДО "РАПОРТ" — адресат, правий край
        for (int i = 0; i < raportIdx; i++) {
            String t = lines[i].strip();
            if (!t.isEmpty()) addLine(doc, t, ParagraphAlignment.RIGHT, false);
        }
        addEmptyLine(doc);

        // РАПОРТ — по центру, жирний
        addLine(doc, "РАПОРТ", ParagraphAlignment.CENTER, true);
        addEmptyLine(doc);

        // Знаходимо де починається блок підпису — останній порожній рядок
        int signStart = lines.length;
        for (int i = lines.length - 1; i > raportIdx + 2; i--) {
            if (lines[i].strip().isEmpty()) { signStart = i + 1; break; }
        }

        // Тіло рапорту
        StringBuilder block = new StringBuilder();
        for (int i = raportIdx + 1; i < signStart; i++) {
            String t = lines[i].strip();
            if (t.isEmpty()) {
                if (block.length() > 0) {
                    addBodyBlock(doc, block.toString());
                    block = new StringBuilder();
                }
            } else {
                if (block.length() > 0) block.append("\n");
                block.append(lines[i]);
            }
        }
        if (block.length() > 0) addBodyBlock(doc, block.toString());

        // Підпис: посада (рядок 1, ліво), звання|ПІБ-КАПС (рядок 2, таблиця), дата
        addEmptyLine(doc);
        addEmptyLine(doc);

        // Збираємо непорожні рядки підпису
        java.util.List<String> sigLines = new java.util.ArrayList<>();
        for (int i = signStart; i < lines.length; i++) {
            String t = lines[i].strip();
            if (!t.isEmpty()) sigLines.add(t);
        }

        if (sigLines.size() >= 2) {
            // Рядок 0 — посада автора (ліворуч)
            addLine(doc, sigLines.get(0), ParagraphAlignment.LEFT, false);
            // Рядок 1 — "звання Прізвище Ім'я": таблиця звання зліва | ПІБ КАПС справа
            formatRankNameRow(doc, sigLines.get(1));
            // Решта рядків (якщо є) — ліворуч
            for (int i = 2; i < sigLines.size(); i++) {
                addLine(doc, sigLines.get(i), ParagraphAlignment.LEFT, false);
            }
        } else if (sigLines.size() == 1) {
            // Тільки звання+ПІБ — таблиця без посади
            formatRankNameRow(doc, sigLines.get(0));
        }
        addLine(doc, date, ParagraphAlignment.LEFT, false);
    }

    // Складані військові звання (2 слова)
    private static final java.util.List<String> COMPOUND_RANKS = java.util.List.of(
        "молодший лейтенант", "старший лейтенант",
        "молодший сержант",   "старший сержант",
        "головний сержант",   "майстер-сержант",
        "старший солдат",     "старший прапорщик",
        "молодший прапорщик", "головний корабельний старшина"
    );

    // ── Підпис: "звання Ім'я ПРІЗВИЩЕ" → таблиця: звання зліва | Ім'я ПРІЗВИЩЕ справа ──
    private void formatRankNameRow(XWPFDocument doc, String line) {
        String lower = line.toLowerCase();
        String rank = null;
        String name = null;
        for (String cr : COMPOUND_RANKS) {
            if (lower.startsWith(cr + " ") || lower.equals(cr)) {
                rank = line.substring(0, cr.length());
                name = line.substring(cr.length()).strip();
                break;
            }
        }
        if (rank == null) {
            int spaceIdx = line.indexOf(' ');
            rank = spaceIdx > 0 ? line.substring(0, spaceIdx) : line;
            name = spaceIdx > 0 ? line.substring(spaceIdx + 1).strip() : "";
        }
        // Ім'я і ПРІЗВИЩЕ — модель вже форматує правильно (Ім'я ПРІЗВИЩЕ)
        addSignatureRow(doc, rank, name);
    }

    // ── Підпис: таблиця 2 колонки без рамок — звання зліва, ПРІЗВИЩЕ справа ──
    private void addSignatureRow(XWPFDocument doc, String left, String right) {
        XWPFTable table = doc.createTable(1, 2);

        CTTbl    tbl   = table.getCTTbl();
        CTTblPr  tblPr = tbl.getTblPr() != null ? tbl.getTblPr() : tbl.addNewTblPr();

        // Прибираємо рамки таблиці
        CTTblBorders borders = tblPr.getTblBorders() != null
                ? tblPr.getTblBorders() : tblPr.addNewTblBorders();
        setNoBorder(borders.getTop()     != null ? borders.getTop()     : borders.addNewTop());
        setNoBorder(borders.getBottom()  != null ? borders.getBottom()  : borders.addNewBottom());
        setNoBorder(borders.getLeft()    != null ? borders.getLeft()    : borders.addNewLeft());
        setNoBorder(borders.getRight()   != null ? borders.getRight()   : borders.addNewRight());
        setNoBorder(borders.getInsideH() != null ? borders.getInsideH() : borders.addNewInsideH());
        setNoBorder(borders.getInsideV() != null ? borders.getInsideV() : borders.addNewInsideV());

        // Ширина таблиці
        CTTblWidth tblW = tblPr.getTblW() != null ? tblPr.getTblW() : tblPr.addNewTblW();
        tblW.setType(STTblWidth.DXA);
        tblW.setW(BigInteger.valueOf(9354));

        fillSigCell(table.getRow(0).getCell(0), left,  ParagraphAlignment.LEFT,  4677, false);
        fillSigCell(table.getRow(0).getCell(1), right, ParagraphAlignment.RIGHT, 4677, true);
    }

    private void setNoBorder(CTBorder b) {
        b.setVal(STBorder.NONE);
    }

    private void fillSigCell(XWPFTableCell cell, String text,
                              ParagraphAlignment align, int widthTwips, boolean bold) {
        while (!cell.getParagraphs().isEmpty()) cell.removeParagraph(0);
        XWPFParagraph para = cell.addParagraph();
        para.setAlignment(align);
        applySpacing(para);
        XWPFRun run = para.createRun();
        run.setFontFamily(FONT);
        run.setFontSize(FONT_SIZE);
        run.setBold(bold);
        run.setText(text);

        CTTcPr tcPr = cell.getCTTc().getTcPr() != null
                ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
        CTTblWidth w = tcPr.getTcW() != null ? tcPr.getTcW() : tcPr.addNewTcW();
        w.setType(STTblWidth.DXA);
        w.setW(BigInteger.valueOf(widthTwips));

        CTTcBorders cb = tcPr.getTcBorders() != null
                ? tcPr.getTcBorders() : tcPr.addNewTcBorders();
        setNoBorder(cb.getTop()    != null ? cb.getTop()    : cb.addNewTop());
        setNoBorder(cb.getBottom() != null ? cb.getBottom() : cb.addNewBottom());
        setNoBorder(cb.getLeft()   != null ? cb.getLeft()   : cb.addNewLeft());
        setNoBorder(cb.getRight()  != null ? cb.getRight()  : cb.addNewRight());
    }

    private String makeFileName(String documentType) {
        String prefix = switch (documentType == null ? "" : documentType.toLowerCase().strip()) {
            case "рапорт"             -> "raport";
            case "методична розробка" -> "metodychna";
            case "наказ"              -> "nakaz";
            case "план-конспект"      -> "plan_konspekt";
            case "доповідна записка"  -> "dopovid";
            default                   -> "document";
        };
        String ts = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        return prefix + "_" + ts + ".docx";
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

    // ── Видаляє перший рядок якщо він повторює заголовок документа ──
    private String stripLeadingTitle(String text, String titleKeyword) {
        if (text == null) return "";
        String[] lines = text.split("\n", -1);
        int start = 0;
        for (int i = 0; i < Math.min(3, lines.length); i++) {
            String t = lines[i].strip().toLowerCase();
            if (t.equals(titleKeyword) || t.startsWith(titleKeyword)) {
                start = i + 1;
                break;
            }
        }
        // Пропускаємо порожні рядки після заголовка
        while (start < lines.length && lines[start].strip().isEmpty()) start++;
        if (start == 0) return text;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) {
            sb.append(lines[i]);
            if (i < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
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
