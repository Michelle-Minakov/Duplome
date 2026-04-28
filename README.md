# Генератор військових документів

Мультиагентна система автоматичної генерації офіційних військових документів на основі неструктурованих нотаток. Формат виводу відповідає вимогам **ДСТУ 4163:2020**.

---

## Технічний стек

| Компонент | Версія |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| LangChain4j | 0.31.0 |
| Ollama (llama3.2:3b) | локально |
| Apache POI | 5.2.5 |
| Maven | 3.x |

---

## Архітектура

```
Користувач
    │
    ▼
DocumentController  ←  POST /api/documents/generate
                        POST /api/documents/generate/docx
    │
    ▼
OrchestratorService
    │
    ├─▶ AnalystAgent      (LLM) → структурований JSON
    │        ↓
    ├─▶ EditorAgent       (LLM) → офіційний текст (ДСТУ 4163:2020)
    │        ↓
    └─▶ DocxService       (POI) → .docx файл
```

### Агенти

**`AnalystAgent`** — отримує сирий текст, повертає JSON:
```json
{
  "documentType": "рапорт|доповідна|наказ|звіт",
  "author":    "Коваленко О.В.",
  "date":      "23.04.2026",
  "recipient": "командиру в/ч А0000",
  "subject":   "тема документа",
  "keyPoints": ["пункт 1", "пункт 2"]
}
```

**`EditorAgent`** — перетворює JSON на офіційно-діловий текст за ДСТУ 4163:2020.

---

## Структура проєкту

```
src/main/java/ua/military/
├── DocGeneratorApplication.java   ← точка входу, порт 8080
├── config/
│   └── AiConfig.java              ← підключення до Ollama
├── agent/
│   ├── AnalystAgent.java          ← агент-аналітик
│   └── EditorAgent.java           ← агент-редактор
├── service/
│   ├── OrchestratorService.java   ← конвеєр агентів
│   ├── DocumentResult.java        ← DTO: текст + байти .docx
│   ├── DocxService.java           ← генерація .docx (Apache POI)
│   └── RagService.java            ← заглушка RAG
└── controller/
    └── DocumentController.java    ← REST API

src/main/resources/
├── application.properties
└── static/
    └── index.html                 ← веб-інтерфейс
```

---

## Вимоги

- **Java 17+**
- **Maven 3.x**
- **Ollama** запущений локально: `http://localhost:11434`
- Модель завантажена: `ollama pull llama3.2:3b`

---

## Запуск

```bash
# 1. Переконатися, що Ollama запущено
ollama serve

# 2. Зібрати та запустити
mvn spring-boot:run
```

Сервер запускається на `http://localhost:8080`.

---

## API

### Отримати текст документа

```
POST /api/documents/generate
Content-Type: text/plain;charset=UTF-8

рапорт від підполковника Коваленко О.В., прошу відпустку 3 доби...
```

**Відповідь:** `text/plain;charset=UTF-8` — готовий текст документа.

---

### Завантажити .docx

```
POST /api/documents/generate/docx
Content-Type: text/plain;charset=UTF-8

рапорт від підполковника Коваленко О.В., прошу відпустку 3 доби...
```

**Відповідь:** файл `document_YYYYMMDD.docx`.

---

### Приклад через curl

```bash
# Переглянути текст
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: text/plain;charset=UTF-8" \
  -d "рапорт від Коваленко О.В., прошу відпустку 3 доби з 25.04.2026, сімейні обставини"

# Завантажити .docx
curl -X POST http://localhost:8080/api/documents/generate/docx \
  -H "Content-Type: text/plain;charset=UTF-8" \
  -d "рапорт від Коваленко О.В., прошу відпустку 3 доби з 25.04.2026" \
  --output document.docx
```

---

## Веб-інтерфейс

Відкрити у браузері: `http://localhost:8080`

- Введення вільного тексту (нотатки, тези)
- Кнопка **"Переглянути текст"** — показує результат у браузері
- Кнопка **"Завантажити .docx"** — зберігає файл
- Вбудовані приклади: рапорт, наказ, звіт

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
| Вид документа | по центру, великими, жирний |

---

## Що планується

- [ ] `RagService` — векторна база знань з прикладами ДСТУ та глосарієм
- [ ] Підтримка шаблонів документів (.docx заготовки)
- [ ] Нумерація документів / реєстраційний номер
- [ ] Тести (MockMvc + Testcontainers для Ollama)

---

## Налаштування

`src/main/resources/application.properties`:

```properties
spring.application.name=doc-generator
server.port=8080
ollama.base.url=http://localhost:11434

# Рівень логування LangChain4j (DEBUG для відлагодження)
logging.level.dev.langchain4j=WARN
```

Таймаут моделі та температура — в `AiConfig.java` (за замовчуванням 120 с, 0.3).
