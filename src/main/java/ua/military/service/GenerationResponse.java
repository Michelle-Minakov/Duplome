package ua.military.service;

public record GenerationResponse(
        String  documentType,
        String  text,
        String  fileName,
        long    processingTimeMs,
        // Порівняльний режим
        String  textWithRag,
        String  textWithoutRag,
        String  fileNameWithoutRag,
        String  ragContext,
        // Метрики
        int     ragFragmentsUsed,
        String  detectedDocumentType,
        boolean ragContextFound
) {
    // Звичайний режим (без порівняння, з метриками)
    public GenerationResponse(String documentType, String text,
                               String fileName, long processingTimeMs,
                               int ragFragmentsUsed) {
        this(documentType, text, fileName, processingTimeMs,
             null, null, null, null,
             ragFragmentsUsed, documentType, ragFragmentsUsed > 0);
    }

    // Зворотна сумісність — для тестів
    public GenerationResponse(String documentType, String text,
                               String fileName, long processingTimeMs) {
        this(documentType, text, fileName, processingTimeMs, 0);
    }
}
