package ua.military.config;  // ✅ package правильний, залишаємо

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.military.agent.AnalystAgent;
import ua.military.agent.EditorAgent;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${ollama.base.url}")
    private String baseUrl;

    // ✅ приватний метод — не дублюємо код
    private OllamaChatModel buildModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                //.modelName("mistral")
                //.modelName("llama3")
                .modelName("llama3.2:3b")
                .timeout(Duration.ofSeconds(120))
                .temperature(0.3)  // ✅ точніші офіційні тексти
                //.numCtx(4096)         // ✅ додаємо для кращого контексту
                .build();
    }

    @Bean
    public AnalystAgent analystAgent() {
        return AiServices.builder(AnalystAgent.class)
                .chatLanguageModel(buildModel())
                .build();
    }

    @Bean
    public EditorAgent editorAgent() {
        return AiServices.builder(EditorAgent.class)
                .chatLanguageModel(buildModel())
                .build();
    }
}