# Система автоматичної генерації військових документів із використанням AI-асистентів

Бакалаврська дипломна робота. Мультиагентна система на основі великих мовних моделей (LLM) для автоматичної генерації офіційних військових документів у форматі `.docx` відповідно до **ДСТУ 4163:2020** та **Наказу МО України № 40 від 31.01.2024**.

---

## Технологічний стек

| Компонент | Версія | Призначення |
|---|---|---|
| Java | 21 | Основна мова реалізації |
| Spring Boot | 3.2.5 | Веб-фреймворк, REST API |
| LangChain4j | 0.31.0 | Інтеграція з LLM, RAG-конвеєр |
| Ollama | локально | Сервер локальних LLM-моделей |
| qwen2.5:7b | 4.7 GB | Генеративна мовна модель |
| AllMiniLmL6V2 | вбудована | Ембеддинги для векторного пошуку |
| Apache POI | 5.2.5 | Генерація .docx документів |
| Apache PDFBox | 2.0.29 | Парсинг PDF файлів |
| LanguageTool | 6.4 | Граматична перевірка (українська) |
| H2 Database | вбудована | Файлова БД для метаданих |
| Maven | 3.x | Система збірки |

---

## Архітектура системи

Система реалізована за принципом **багатоагентного конвеєра** (Multi-Agent Pipeline) з інтеграцією RAG (Retrieval-Augmented Generation).

```
Користувач (браузер / Vanilla JS)
              │
              ▼
   DocumentController  ←→  REST API (/api/documents/*)
              │
              ▼
   OrchestratorService  ←  координує весь конвеєр
              │
    ┌─────────┼──────────┐
    │         │          │
    ▼         ▼          │
AnalystAgent  RagService  │
(LLM)    (AllMiniLM)     │
    │         │          │
    └────┬────┘          │
         │               │
         ▼               │
   EditorAgent           │
   (LLM: qwen2.5:7b)     │
         │               │
         ▼               │
   DocxService  ←────────┘
   (Apache POI)
         │
         ▼
   GrammarCheckService
   (LanguageTool)
         │
         ▼
   H2 Database + generated/*.docx
```

### Кроки конвеєра

| Крок | Компонент | Дія |
|---|---|---|
| 1 | `AnalystAgent` | Аналізує вхідні нотатки → повертає JSON `{documentType, author, recipient, subject}` |
| 2 | `RagService` | Векторний пошук релевантних зразків (score ≥ 0.45, max 2 фрагменти) |
| 3 | `EditorAgent` | Генерує офіційний текст документа на основі JSON + RAG-контексту |
| 4 | `DocxService` | Форматує текст у .docx за ДСТУ 4163:2020 |
| 5 | `GrammarCheckService` | Перевіряє граматику та орфографію (LanguageTool, uk) |

---

## Підтримувані типи документів

| Тип | Опис |
|---|---|
| **Рапорт** | Особисте звернення військовослужбовця до командира |
| **Методична розробка** | Навчально-методичний документ викладача ВВНЗ |
| Наказ | Розпорядчий документ командира |
| План-конспект | Конспект лекційного/практичного заняття |
| Доповідна записка | Службова записка до вищого начальника |

---

## Структура проєкту

```
Duplome/
├── src/main/java/ua/military/
│   ├── DocGeneratorApplication.java     ← точка входу, порт 8080
│   ├── config/
│   │   └── AiConfig.java                ← Ollama: qwen2.5:7b, temp=0.2, ctx=8192
│   ├── agent/
│   │   ├── AnalystAgent.java            ← нотатки → структурований JSON
│   │   ├── RaportEditorAgent.java       ← JSON + RAG → офіційний рапорт
│   │   └── MethodychnaEditorAgent.java  ← JSON + RAG → методична розробка
│   ├── service/
│   │   ├── OrchestratorService.java     ← конвеєр з 5 кроків
│   │   ├── RagService.java              ← векторна база + AllMiniLM ембеддинги
│   │   ├── DocxService.java             ← генерація .docx за ДСТУ 4163:2020
│   │   ├── GrammarCheckService.java     ← граматична перевірка (LanguageTool)
│   │   ├── StatisticsService.java       ← агрегація метрик з БД
│   │   └── GenerationResponse.java      ← DTO відповіді
│   ├── controller/
│   │   ├── DocumentController.java      ← REST API документів
│   │   └── StatisticsController.java    ← REST API статистики
│   ├── model/
│   │   └── DocumentRecord.java          ← JPA-сутність запису генерації
│   └── repository/
│       └── DocumentRepository.java      ← Spring Data JPA репозиторій
│
├── src/main/resources/
│   ├── application.properties           ← конфігурація Spring, H2, Ollama
│   ├── static/
│   │   └── index.html                   ← SPA веб-інтерфейс (Vanilla JS)
│   └── rag/
│       ├── raport_rules.txt             ← правила складання рапорту
│       ├── metodychna_rules.txt         ← вимоги до методичної розробки
│       ├── metodychna_examples.txt      ← зразки (оборона, медична, топографія)
│       └── metodychna_full_examples.txt ← зразки (статут, стройова, РХБ, фізична, інженерна, правова, зв'язок)
│
├── rag/
│   └── generated_samples.txt            ← накопичені зразки (feedback loop)
├── generated/                           ← згенеровані .docx файли
├── data/
│   └── docgen.mv.db                     ← H2 файлова БД
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

---

## Вимоги

- **Java 21+**
- **Maven 3.x**
- **Ollama** запущений локально: `http://localhost:11434`
- Модель завантажена: `ollama pull qwen2.5:7b`

---

## Запуск

```powershell
# 1. Запустити Ollama
ollama serve

# 2. Зібрати та запустити додаток
cd Duplome
mvn spring-boot:run
```

Веб-інтерфейс: `http://localhost:8080`

---

## Docker

```bash
# Docker Compose (рекомендовано)
cd Duplome
docker compose up --build

# Або вручну
docker build -t doc-generator .
docker run -p 8080:8080 \
  -v "./data:/app/data" \
  -v "./generated:/app/generated" \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  doc-generator
```

---

## REST API

| Метод | Endpoint | Опис |
|---|---|---|
| `POST` | `/api/documents/generate` | Генерація документа з нотаток |
| `POST` | `/api/documents/generate?mode=compare` | Генерація двох версій (з RAG і без) |
| `GET` | `/api/documents/history` | Список останніх 200 документів |
| `GET` | `/api/documents/download?file=...` | Завантажити .docx |
| `GET` | `/api/documents/preview?file=...` | HTML-перегляд документа |
| `POST` | `/api/documents/save-to-rag` | Зберегти документ як зразок |
| `POST` | `/api/documents/parse` | Витягти текст з .docx / .pdf / .txt |
| `DELETE` | `/api/documents/delete?file=...` | Видалити один документ |
| `DELETE` | `/api/documents/history` | Очистити всю історію |
| `GET` | `/api/statistics` | Статистика генерацій |

### Приклад запиту

```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: text/plain;charset=UTF-8" \
  -d "рапорт від лейтенанта Коваленко В.О. командиру роти капітану Мельнику С.П. прошу відпустку на 5 діб з 15.06.2026"
```

---

## Форматування .docx (ДСТУ 4163:2020)

| Параметр | Значення |
|---|---|
| Шрифт | Times New Roman 14pt |
| Міжрядковий інтервал | 1.5 |
| Відступ першого рядка | 1.25 см |
| Береги: ліве | 30 мм |
| Береги: праве | 15 мм |
| Береги: верхнє / нижнє | 20 мм |
| Вирівнювання тіла | по ширині |
| Заголовок | по центру, великими, жирний |
| Підпис рапорту | таблиця без рамок: звання зліва — ПРІЗВИЩЕ справа |

---

## Особливості реалізації

- **Локальна AI** — модель `qwen2.5:7b` працює повністю офлайн, без хмарних сервісів
- **RAG Feedback Loop** — кожен збережений зразок одразу потрапляє у векторну базу
- **Граматична перевірка** — LanguageTool з підтримкою української мови після кожної генерації
- **Режим порівняння** — генерує два документи (з RAG і без) для демонстрації впливу RAG
- **Фільтрація RAG** — для методичних розробок автоматично виключаються рапорт-фрагменти
- **Нормалізація типу** — система розпізнає "Raport", "raport", "rapport" → "рапорт"
