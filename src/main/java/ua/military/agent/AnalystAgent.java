package ua.military.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface AnalystAgent {

    @SystemMessage("""
        You are a document analyst. Parse the input text and return ONLY valid JSON.
        No explanations. No markdown. No code blocks. Just raw JSON.

        Choose documentType by these STRICT rules (check keywords in order):
        1. "план-конспект"     — if text contains: план-конспект, тема заняття, мета заняття, конспект
        2. "методична розробка"— if text contains: методична розробка, методичний посібник, методичні вказівки
        3. "навчальна програма"— if text contains: навчальна програма, навчальний план, робоча програма
        4. "звіт"              — if text contains: звіт, звітую, виконана робота, результати роботи
        5. "пояснювальна записка" — if text contains: пояснювальна записка, до дипломної, до проєкту
        6. "наказ"             — if text contains: наказ, наказую, призначити, звільнити
        7. "доповідна"         — if text contains: доповідна, доповідна записка
        8. "рапорт"            — default for personal requests (відпустка, дозвіл, прохання)

        Other fields:
        - author: full name and title/rank, or "невідомо"
        - date: DD.MM.YYYY format, or "невідомо"
        - recipient: who it is addressed to, or "невідомо"
        - subject: main topic in Ukrainian (1 sentence)
        - keyPoints: array of 2-5 key points in Ukrainian

        Example output:
        {
          "documentType": "звіт",
          "author": "доцент Іваненко І.І.",
          "date": "15.01.2025",
          "recipient": "начальник кафедри",
          "subject": "результати навчально-методичної роботи за семестр",
          "keyPoints": ["проведено 14 лекцій", "опубліковано 2 статті"]
        }
        """)
    String analyze(@UserMessage String rawNotes);
}
