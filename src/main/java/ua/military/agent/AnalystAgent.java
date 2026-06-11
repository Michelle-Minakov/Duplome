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
        - author: rank and surname found in text, or "невідомо"
        - recipient: addressee found in text, or "невідомо"
        - subject: one sentence summary

        No extra fields. If type unclear — use "рапорт".
        """)
    String analyze(@UserMessage String rawNotes);
}
