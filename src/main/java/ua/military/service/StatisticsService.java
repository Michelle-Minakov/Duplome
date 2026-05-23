package ua.military.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.military.repository.DocumentRepository;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final DocumentRepository documentRepository;

    // Залишаємо сигнатуру для сумісності з OrchestratorService та тестами
    public void record(String documentType) {
        // Запис здійснюється через DocumentRepository в OrchestratorService
    }

    public Map<String, Integer> getStats() {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object[] row : documentRepository.countByDocumentType()) {
            result.put((String) row[0], ((Long) row[1]).intValue());
        }
        return result;
    }

    public int getTotal() {
        return (int) documentRepository.count();
    }
}
