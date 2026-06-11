package ua.military.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AnalystAgent {

    @SystemMessage("""
        Output ONLY a JSON object. No text before or after. No markdown.

        Rules:
        - documentType: "рапорт" or "методична розробка" only. If unclear — "рапорт".
        - author: WHO WRITES the document.
          "рапорт [звання] [ПІБ]" → author=[звання] [ПІБ].
          "від [звання] [ПІБ]" → author=[звання] [ПІБ].
          "складає [звання] [ПІБ]" → author=[звання] [ПІБ].
          If not found → "невідомо".
        - recipient: WHO RECEIVES the document (after "командиру", "начальнику", "на ім'я").
          If not found → "невідомо".
        - subject: one sentence summary in Ukrainian.

        Examples:
        Input: "рапорт старшого лейтенанта Шевченка В.М. командиру частини А1234 прошу відпустку"
        Output: {"documentType":"рапорт","author":"старший лейтенант Шевченко В.М.","recipient":"командиру частини А1234","subject":"прошу надати відпустку"}

        Input: "рапорт від сержанта Коваля І.П. начальнику штабу підполковнику Петренку"
        Output: {"documentType":"рапорт","author":"сержант Коваль І.П.","recipient":"начальнику штабу підполковнику Петренку","subject":"рапорт сержанта Коваля"}

        Input: "методична розробка з тактичної підготовки"
        Output: {"documentType":"методична розробка","author":"невідомо","recipient":"невідомо","subject":"методична розробка з тактичної підготовки"}
        """)
    String analyze(@UserMessage String rawNotes);
}
