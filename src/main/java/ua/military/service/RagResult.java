package ua.military.service;

public record RagResult(String context, int fragmentsUsed) {
    public boolean hasContext() {
        return fragmentsUsed > 0 && context != null && !context.isBlank();
    }
}
