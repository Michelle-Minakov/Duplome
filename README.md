# Система автоматичної генерації звітної та методичної документації з використанням AI-асистентів

Бакалаврська робота. Мультиагентна система на основі великих мовних моделей (LLM) для автоматичної генерації офіційних документів у форматі `.docx` відповідно до **ДСТУ 4163:2020**.

---

## Технічний стек

| Компонент | Версія |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.5 |
| LangChain4j | 0.31.0 |
| Ollama (llama3.2:3b) | локально |
| Apache POI | 5.2.5 |
| Apache PDFBox | 3.0.0 |
| Maven | 3.x |

---

## Опис рішення

Система має веб-інтерфейс та REST API для наступних сценаріїв:

- генерація офіційних документів `.docx` з вхідних нотаток
- збереження результатів у папці `generated/`
- перегляд історії генерацій
- завантаження згенерованого файлу
- попередній перегляд документа у HTML
- парсинг завантажених `.docx`, `.pdf` та `.txt` для повторного використання тексту
- drag&drop для зручного завантаження файлів
- режим порівняння (compare mode) для генерації двох версій документу

---

## Архітектура

```
Користувач (веб-інтерфейс)
         │
         ▼
DocumentController  ←  POST /api/documents/generate
           │     ├─ GET /api/documents/history
           │     ├─ DELETE /api/documents/history
           │     ├─ GET /api/documents/download
           │     ├─ GET /api/documents/preview
           │     └─ POST /api/documents/parse
         ▼
OrchestratorService
         │
         ├─▶ AnalystAgent   (LLM) → структурований JSON
         │        ↓
         ├─▶ RagService     (Embedding) → пошук релевантних зразків
         │        ↓
         ├─▶ EditorAgent    (LLM) → офіційний текст (ДСТУ 4163:2020)
         │        ↓
         └─▶ DocxService    (POI) → .docx файл
```

---

## Підтримувані типи документів

| Тип | Початок тексту |
|---|---|
| Звіт | "Звітую про виконану роботу..." |
| Методична розробка | "Методична розробка присвячена..." |
| План-конспект | "Тема заняття:..." |
| Навчальна програма | "Навчальна програма з дисципліни..." |
| Пояснювальна записка | "Пояснювальна записка до..." |
| Рапорт | "Доповідаю, що прошу..." |
| Доповідна | "Доповідаю, що..." |
| Наказ | "Наказую:..." |

---

## Структура проєкту

```
src/main/java/ua/military/
├── DocGeneratorApplication.java   ← точка входу, порт 8080
├── config/
│   └── AiConfig.java              ← підключення до Ollama
├── agent/
│   ├── AnalystAgent.java          ← аналіз тексту → JSON
│   └── EditorAgent.java           ← JSON → офіційний текст
├── service/
│   ├── OrchestratorService.java   ← конвеєр з 4 кроків
│   ├── DocumentResult.java        ← DTO результату
│   ├── DocxService.java           ← генерація .docx і парсинг файлів
│   └── RagService.java            ← векторна база зразків
└── controller/
    └── DocumentController.java    ← REST API

src/main/resources/
├── application.properties
└── static/
    └── index.html                 ← веб-інтерфейс з drag&drop
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
# 1. Запустити Ollama (якщо не запущено як системний сервіс)
ollama serve

# 2. Зібрати та запустити додаток
cd Duplome
mvn spring-boot:run
```

Веб-інтерфейс: `http://localhost:8080`

---

## Docker

Проєкт підтримує контейнерне розгортання через `Dockerfile` та `docker-compose.yml`.

### Варіант 1: Docker Compose

```bash
cd Duplome
docker compose up --build
```

Це створить контейнер `doc-generator`, пробросить порт `8080`, підключить локальні папки `./data` та `./generated`, і налаштує доступ до локального Ollama за адресою `http://host.docker.internal:11434`.

### Варіант 2: вручну з Docker

```bash
cd Duplome
docker build -t doc-generator .
docker run -p 8080:8080 \
  -v "%cd%/data:/app/data" \
  -v "%cd%/generated:/app/generated" \
  -e OLLAMA_BASE_URL=http://host.docker.internal:11434 \
  doc-generator
```

> Якщо Ollama працює на хост-машині, контейнер використовує `host.docker.internal` для доступу до нього.

### Перевірка контейнера

Після запуску контейнера можна перевірити роботу так:

```bash
curl -v http://localhost:8080/api/documents/history
```

Якщо все правильно, сервер відповість `200 OK` і поверне JSON з історією. Також можна відкрити у браузері `http://localhost:8080`.

---

## REST API

### Згенерувати документ

```
POST /api/documents/generate
Content-Type: text/plain;charset=UTF-8

Звіт доцента Іваненка І.І. за І семестр 2024/2025...
```

### Отримати історію генерацій

```
GET /api/documents/history
```

### Очистити історію

```
DELETE /api/documents/history
```

### Завантажити .docx

```
GET /api/documents/download?file=document_1234567890.docx
```

### Переглянути документ у HTML

```
GET /api/documents/preview?file=document_1234567890.docx
```

### Парсинг завантаженого файлу

```
POST /api/documents/parse
Content-Type: multipart/form-data
Form field: file
```

Підтримувані формати: `.docx`, `.pdf`, `.txt`.

### Приклад curl для генерації

```bash
curl -X POST http://localhost:8080/api/documents/generate \
  -H "Content-Type: text/plain;charset=UTF-8" \
  -d "Звіт доцента Іваненка за семестр. Проведено 14 лекцій, опубліковано 2 статті."
```

### Приклад curl для парсингу файлу

```bash
curl -X POST http://localhost:8080/api/documents/parse \
  -F "file=@./sample.docx"
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
| Вид документа | по центру, великими, жирний |
