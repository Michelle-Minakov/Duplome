package ua.military.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AnalystAgent {

    @SystemMessage("""
        Output ONLY a JSON object. No text before or after. No markdown.

        Rules:
        - documentType: "рапорт" or "методична розробка" only. If unclear — "рапорт".
        - author: rank + full name of WHO WRITES the document (звання + ПІБ).
          "рапорт [звання] [ПІБ]" → author=[звання] [ПІБ].
          "від [звання] [ПІБ]" → author=[звання] [ПІБ].
          If not found → "невідомо".
        - authorPosition: the author's job title / position (посада) — e.g. "Курсант 421 навчальної групи", "Командир взводу", "Начальник штабу". Different from rank! If not found → "".
        - recipient: WHO RECEIVES the document (after "командиру", "начальнику", "на ім'я"). If not found → "невідомо".
        - subject: one sentence summary in Ukrainian.

        Examples:
        Input: "рапорт курсанта 421 навчальної групи солдата Пастуха Дмитра Юрійовича командиру 421 навчальної групи прошу звільнення"
        Output: {"documentType":"рапорт","author":"солдат Пастух Дмитро Юрійович","authorPosition":"Курсант 421 навчальної групи","recipient":"Командиру 421 навчальної групи","subject":"прошу звільнення з розташування"}

        Input: "рапорт старшого лейтенанта Шевченка В.М. командиру частини А1234 прошу відпустку"
        Output: {"documentType":"рапорт","author":"старший лейтенант Шевченко В.М.","authorPosition":"","recipient":"Командиру частини А1234","subject":"прошу надати відпустку"}

        Input: "методична розробка з тактичної підготовки старшого викладача Петренка О.В."
        Output: {"documentType":"методична розробка","author":"Петренко О.В.","authorPosition":"старший викладач","recipient":"невідомо","subject":"методична розробка з тактичної підготовки"}
        """)
    String analyze(@UserMessage String rawNotes);
}
