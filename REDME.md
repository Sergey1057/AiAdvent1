# AiAdvent1

Проект для челленджа [AI Advent](https://github.com/Sergey1057/AiAdvent1): Android-приложение с чатом на **Groq API** (Jetpack Compose, Kotlin).

## Приложение

- Чат с LLM, настройки модели и системного промпта
- Ключ: `GROQ_API_KEY` в `local.properties` (см. [docs/api-groq.md](docs/api-groq.md))
- Структура кода: [docs/structure.md](docs/structure.md)

## AI-инструменты (llm_agent)

| Инструмент   | Назначение                                                                  |
|--------------|--------------------------------------------------------------------------------|
| **Приложение** (этот репо) | Чат с Groq на устройстве                                                     |
| [AI PR Review](.github/workflows/ai-pr-review.yml) | Авто-ревью pull request (GigaChat / Groq)                                    |
| [File Assistant](docs/file-assistant-agent.md) | Синхронизация `docs/` с кодом после мержа (MCP + LLM)                         |

File Assistant — вторая «реальная задача» курса: после PR документация не отстаёт от кода. Подробности: [docs/file-assistant-agent.md](docs/file-assistant-agent.md).

## Документация

- [docs/structure.md](docs/structure.md) — структура репозитория
- [docs/api-groq.md](docs/api-groq.md) — API Groq
- [docs/file-assistant-agent.md](docs/file-assistant-agent.md) — ассистент файлов из [llm_agent](https://github.com/Sergey1057/llm_agent)

## Сборка

- Стек технологий: **Android**, **Kotlin**, **Jetpack Compose**, **Material3**.
- Запуск: Android Studio или `./gradlew :app:installDebug`.
- API-ключ: `GROQ_API_KEY` в `local.properties` (см. `docs/api-groq.md`).

## Связь с llm_agent

| Инструмент   | Назначение                                                                 |
|--------------|----------------------------------------------------------------------------|
| Dev assistant (`/help`) | RAG по `REDME.md` и `docs/`                                                |
| File Assistant | Поиск usages, обновление docs по коду (MCP + LLM)                           |
| AI PR Review  | Авто-ревью pull request                                                    |

См. также [file-assistant-agent.md](file-assistant-agent.md).
