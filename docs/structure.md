# Структура проекта AiAdvent1

## Корень репозитория

| Путь             | Назначение          |
|------------------|---------------------|
| `app/`           | Android-модуль      |
| `gradle/`         | Версии зависимостей |
| `REDME.md`       | Краткое описание    |
| `docs/`           | Документация        |
| `local.properties`| Локальные секреты    |

## Модуль `app`

| Путь              | Назначение               |
|-------------------|-------------------------|
| `app/build.gradle.kts` | Зависимости: Compose, OkHttp, Groq API key из BuildConfig |
| `app/src/main/AndroidManifest.xml` | Точка входа MainActivity |
| `app/src/main/java/ru/sergei1057/aiadvent1/` | Исходный код Kotlin      |

## Пакет `ru.sergei1057.aiadvent1`

| Файл                 | Роль                     |
|----------------------|--------------------------|
| `MainActivity.kt`    | Экран чата с Groq API, настройки модели и промпта |
| `ui/theme/Theme.kt`  | Material3 тема AiAdvent1Theme |
| `ui/theme/Color.kt`, `Type.kt` | Цвета и типографика |

## Экраны приложения

1. **Main (`GroqChatScreen`)** — ввод запроса, история пар «запрос / ответ», кнопка «Настройки».
2. **Settings (`SettingsScreen`)** — модель Groq, max tokens, системный промпт, JSON-формат ответа, температура.

## Сборка

- Стек: **Android**, **Kotlin**, **Jetpack Compose**, **Material3**.
- Запуск: Android Studio или `./gradlew :app:installDebug`.
- API-ключ: `GROQ_API_KEY` в `local.properties` (см. `docs/api-groq.md`).

## Связь с llm_agent

| Инструмент   | Назначение                                                                 |
|--------------|----------------------------------------------------------------------------|
| Dev assistant (`/help`) | RAG по `REDME.md` и `docs/`                                                |
| File Assistant | Поиск usages, обновление docs по коду (MCP + LLM)                           |
| AI PR Review  | Авто-ревью pull request                                                    |

См. также [file-assistant-agent.md](file-assistant-agent.md).
