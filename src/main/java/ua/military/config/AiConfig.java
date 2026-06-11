package ua.military.config;

import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ua.military.agent.AnalystAgent;
import ua.military.agent.RaportEditorAgent;
import ua.military.agent.MethodychnaEditorAgent;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${ollama.base.url}")
    private String baseUrl;

    private OllamaChatModel buildModel() {
        return OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName("qwen2.5:7b")
                //.modelName("mistral")
                //.modelName("llama3.1:8b")
                //.modelName("llama3.2:3b")
                .timeout(Duration.ofSeconds(180))
                .temperature(0.2)
                .numCtx(8192)
                .build();
    }

    @Bean
    public AnalystAgent analystAgent() {
        return AiServices.builder(AnalystAgent.class)
                .chatLanguageModel(buildModel())
                .build();
    }

    @Bean
    public RaportEditorAgent raportEditorAgent() {
        return AiServices.builder(RaportEditorAgent.class)
                .chatLanguageModel(buildModel())
                .build();
    }

    @Bean
    public MethodychnaEditorAgent methodychnaEditorAgent() {
        return AiServices.builder(MethodychnaEditorAgent.class)
                .chatLanguageModel(buildModel())
                .build();
    }
}