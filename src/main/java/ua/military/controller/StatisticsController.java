package ua.military.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ua.military.service.StatisticsService;

import java.util.Map;

@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping(produces = "application/json;charset=UTF-8")
    public Map<String, Object> getStatistics() {
        return Map.of(
                "counts", statisticsService.getStats(),
                "total",  statisticsService.getTotal()
        );
    }
}
