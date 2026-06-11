package ua.military.service;

import java.util.List;

public record GenerationResponse(
        String  documentType,
        String  text,
        String  fileName,
        long    processingTimeMs,
        String  textWithRag,
        String  textWithoutRag,
        String  fileNameWithoutRag,
        String  ragContext,
        int     ragFragmentsUsed,
        String  detectedDocumentType,
        boolean ragContextFound,
        List<GrammarCheckService.GrammarWarning> grammarWarnings
) {
    public GenerationResponse(String documentType, String text,
                               String fileName, long processingTimeMs,
                               String textWithRag, String textWithoutRag,
                               String fileNameWithoutRag, String ragContext,
                               int ragFragmentsUsed, String detectedDocumentType,
                               boolean ragContextFound) {
        this(documentType, text, fileName, processingTimeMs,
             textWithRag, textWithoutRag, fileNameWithoutRag, ragContext,
             ragFragmentsUsed, detectedDocumentType, ragContextFound, List.of());
    }
}
