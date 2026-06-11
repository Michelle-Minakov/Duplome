package ua.military.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.languagetool.JLanguageTool;
import org.languagetool.language.Ukrainian;
import org.languagetool.rules.RuleMatch;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GrammarCheckService {

    private volatile JLanguageTool langTool;

    @PostConstruct
    public void init() {
        // Ініціалізація у фоні — LanguageTool завантажує словники ~3-5 сек
        CompletableFuture.runAsync(() -> {
            try {
                log.info("Ініціалізація LanguageTool (українська мова)...");
                langTool = new JLanguageTool(new Ukrainian());
                // Вимикаємо правила стилістики — залишаємо тільки граматику та орфографію
                langTool.disableCategory(org.languagetool.rules.Categories.STYLE.getId());
                langTool.disableCategory(org.languagetool.rules.Categories.TYPOGRAPHY.getId());
                log.info("LanguageTool готовий.");
            } catch (Exception e) {
                log.warn("LanguageTool не ініціалізовано: {}", e.getMessage());
            }
        });
    }

    public List<GrammarWarning> check(String text) {
        if (langTool == null || text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        try {
            List<RuleMatch> matches = langTool.check(text);
            return matches.stream()
                    .filter(m -> m.getToPos() <= text.length())
                    .map(m -> {
                        String fragment = text.substring(m.getFromPos(),
                                Math.min(m.getToPos(), text.length()));
                        List<String> suggestions = m.getSuggestedReplacements()
                                .stream().limit(3).collect(Collectors.toList());
                        return new GrammarWarning(m.getMessage(), fragment, suggestions);
                    })
                    .limit(10) // не більше 10 попереджень
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Помилка граматичної перевірки: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public boolean isReady() {
        return langTool != null;
    }

    public record GrammarWarning(
            String message,
            String fragment,
            List<String> suggestions
    ) {}
}
