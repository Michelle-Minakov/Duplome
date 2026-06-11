package ua.military.service;

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
        boolean ragContextFound
) {
}
