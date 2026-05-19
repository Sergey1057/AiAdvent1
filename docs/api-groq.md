# API Groq (чат) — описание для AiAdvent1

Приложение вызывает **OpenAI-совместимый** endpoint Groq из `MainActivity.kt`.

## Endpoint

```
POST https://api.groq.com/openai/v1/chat/completions
```

Заголовки:

- `Authorization: Bearer <GROQ_API_KEY>`
- `Content-Type: application/json`

Ключ задаётся в `local.properties`:

```properties
GROQ_API_KEY=gsk_...
```

и попадает в `BuildConfig.GROQ_API_KEY` при сборке.

## Тело запроса (JSON)

| Поле | Тип | Описание |
|------|-----|----------|
| `model` | string | ID модели Groq (см. список ниже) |
| `max_tokens` | int | Лимит токенов ответа (1–8192 в UI) |
| `messages` | array | Роли `system` / `user` |
| `temperature` | number | Опционально, 0.0–2.0 |
| `response_format` | object | `{ "type": "json_object" }` при включённом JSON-режиме |

### Сообщения

1. Пользовательский **system** — из настроек («Системный промпт»), если включено «Применить системный промпт».
2. Дополнительный **system** — инструкция отвечать только валидным JSON (если включён чекбокс «Ответ в json формате»).
3. **user** — текст запроса из поля ввода.

## Поддерживаемые модели (UI)

| Отображаемое имя | `model` (apiId) |
|------------------|-----------------|
| GPT OSS 120B | `openai/gpt-oss-120b` |
| Llama 3.3 70B Versatile | `llama-3.3-70b-versatile` (по умолчанию) |
| Qwen 3 32B | `qwen/qwen3-32b` |
| GPT OSS 20B | `openai/gpt-oss-20b` |

## Ответ

Парсится JSON:

- `choices[0].message.content` — текст ответа;
- `usage.total_tokens` — отображается под сообщением.

При ошибке HTTP читается `error.message` из тела ответа.

## Прокси (эмулятор)

`OkHttpClient` настроен с HTTP-прокси `10.0.2.2:12334` (хост-машина из Android-эмулятора) — для отладки через локальный прокси.

## SharedPreferences

Имя: `ai_advent_prefs`

| Ключ | Назначение |
|------|------------|
| `system_prompt` | Текст системного промпта |
| `apply_system_prompt` | bool |
| `temperature_enabled` | bool |
| `temperature` | float (bits) |
| `selected_model` | apiId модели |
