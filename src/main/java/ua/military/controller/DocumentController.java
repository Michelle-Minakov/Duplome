package ua.military.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import ua.military.service.OrchestratorService;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final OrchestratorService orchestratorService;

    @PostMapping("/generate")
    public String generate(@RequestBody String rawNotes) {
        return orchestratorService.generateDocument(rawNotes);
    }
}