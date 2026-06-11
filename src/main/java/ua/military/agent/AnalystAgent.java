package ua.military.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AnalystAgent {

    @SystemMessage("""
        Output ONLY a JSON object. No text before or after. No markdown.

        Example output:
        {"documentType":"рапорт","author":"старший лейтенант Коваленко В.О.","recipient":"командиру в/ч А1234","subject":"прошу надати відпустку"}

        Extract from user input:
        - documentType: "рапорт" or "методична розробка" only
        - author: the person WHO WRITES the document (rank + surname). RULE: "рапорт [rank] [surname]" or "[rank] [surname] writes/submits" → that is the AUTHOR. Or "невідомо".
        - recipient: the person the document is ADDRESSED TO (командиру, начальнику, на ім'я). Or "невідомо".
        - subject: one sentence summary

        IMPORTANT: "рапорт старшого лейтенанта Шевченка" → author="старший лейтенант Шевченко", recipient="невідомо".
        Do NOT confuse author and recipient. No extra fields. If type unclear — use "рапорт".
        """)
    String analyze(@UserMessage String rawNotes);
}
