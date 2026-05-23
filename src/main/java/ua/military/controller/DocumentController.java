package ua.military.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.military.model.DocumentRecord;
import ua.military.repository.DocumentRepository;
import ua.military.service.GenerationResponse;
import ua.military.service.OrchestratorService;
import ua.military.service.DocxService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final OrchestratorService orchestratorService;
    private final DocumentRepository  documentRepository;
    private final DocxService docxService;

    @PostMapping(value = "/generate",
                 consumes = "text/plain;charset=UTF-8",
                 produces = "application/json;charset=UTF-8")
    public GenerationResponse generate(
            @RequestBody String rawNotes,
            @RequestParam(defaultValue = "normal") String mode) {

        if ("compare".equals(mode)) {
            return orchestratorService.generateDocumentCompare(rawNotes);
        }
        return orchestratorService.generateDocument(rawNotes);
    }

    @GetMapping(value = "/history", produces = "application/json;charset=UTF-8")
    public List<Map<String, Object>> getHistory() {
        return documentRepository.findTop20ByOrderByCreatedAtDesc().stream()
                .map(r -> Map.<String, Object>of(
                        "id",               r.getId(),
                        "documentType",     r.getDocumentType(),
                        "fileName",         r.getFileName(),
                        "createdAt",        r.getCreatedAt().toString(),
                        "processingTimeMs", r.getProcessingTimeMs(),
                        "ragFragmentsUsed", r.getRagFragmentsUsed(),
                        "compareMode",      r.isCompareMode()
                ))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/history")
    public ResponseEntity<Void> clearHistory() {
        documentRepository.deleteAll();
        log.info("Історія генерацій очищена");
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam String file) {
        String safeName = new File(file).getName();
        File docx = new File("generated/" + safeName);

        if (!docx.exists() || !safeName.endsWith(".docx")) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + safeName + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(new FileSystemResource(docx));
    }

    @GetMapping(value = "/preview", produces = "text/html;charset=UTF-8")
    public ResponseEntity<String> preview(@RequestParam String file) {
        String safeName = new File(file).getName();
        File docx = new File("generated/" + safeName);

        if (!docx.exists() || !safeName.endsWith(".docx")) {
            return ResponseEntity.notFound().build();
        }

        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(docx))) {
            String html = buildPreviewHtml(doc, safeName);
            return ResponseEntity.ok(html);
        } catch (IOException e) {
            log.error("Помилка перегляду документа {}: {}", safeName, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("<p style='color:red'>Помилка читання документа.</p>");
        }
    }

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = "text/plain;charset=UTF-8")
    public ResponseEntity<String> parseFile(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) return ResponseEntity.badRequest().body("");
        String text = docxService.parseUploadedFile(file);
        return ResponseEntity.ok(text == null ? "" : text);
    }

    private String buildPreviewHtml(XWPFDocument doc, String fileName) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                <html><head><meta charset="UTF-8">
                <style>
                  body { font-family: 'Times New Roman', Times, serif; font-size: 14pt;
                         line-height: 1.5; color: #111; background: #fff;
                         padding: 40px 60px; max-width: 800px; margin: 0 auto; }
                  p    { margin: 0 0 4px; }
                  .indent { text-indent: 1.25cm; }
                  .fn  { font-size: 10pt; color: #888; border-top: 1px solid #ddd;
                         margin-top: 40px; padding-top: 8px; }
                </style></head><body>
                """);

        for (XWPFParagraph para : doc.getParagraphs()) {
            String text = para.getText().strip();

            if (text.isBlank()) {
                sb.append("<p>&nbsp;</p>");
                continue;
            }

            boolean bold   = para.getRuns().stream()
                    .anyMatch(r -> Boolean.TRUE.equals(r.isBold()));
            String  align  = toHtmlAlign(para.getAlignment());
            boolean indent = para.getAlignment() == ParagraphAlignment.BOTH
                    || para.getAlignment() == ParagraphAlignment.LEFT
                       && !bold
                       && para.getIndentationFirstLine() > 0;

            sb.append("<p style=\"text-align:").append(align).append(";");
            if (bold)   sb.append("font-weight:bold;");
            if (indent) sb.append("text-indent:1.25cm;");
            sb.append("\">").append(escHtml(text)).append("</p>\n");
        }

        sb.append("<div class='fn'>📄 ").append(escHtml(fileName)).append("</div>");
        sb.append("</body></html>");
        return sb.toString();
    }

    private String toHtmlAlign(ParagraphAlignment a) {
        if (a == null) return "left";
        return switch (a) {
            case CENTER -> "center";
            case RIGHT  -> "right";
            case BOTH   -> "justify";
            default     -> "left";
        };
    }

    private String escHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
