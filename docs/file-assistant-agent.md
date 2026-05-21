# File Assistant (llm_agent)

Внешний агент из репозитория [llm_agent](https://github.com/Sergey1057/llm_agent) синхронизирует документацию и ищет вхождения символов в этом Android-проекте.

## Задача

После изменений в `app/src/main/java/...` файлы `docs/structure.md` и `REDME.md` быстро устаревают. Ручной поиск usages и правка README отнимает время.

## Как работает AI

1. **MCP** (без модели): git status/diff, поиск `rg`, чтение `.kt` / `.md`.
2. **LLM**: по прочитанным фрагментам предлагает обновлённый текст документации.
3. **Запись**: только с флагом `--file-apply` в llm_agent; иначе preview diff.

## Примеры целей

```bash
export LLM_AGENT_FILE_ROOT=/path/to/AiAdvent1

# Поиск (preview, без записи)
python3 -m file_assistant --goal \
  'найди все места где используется GroqChatScreen' \
  --project-root "$LLM_AGENT_FILE_ROOT" --no-llm

# Обновить структуру по MainActivity (нужен LLM)
python3 cli.py --file-assistant --file-goal \
  'обнови docs/structure.md по MainActivity.kt' \
  --project-root "$LLM_AGENT_FILE_ROOT"
```

Подробности: `llm_agent/deploy/file-assistant/README.md`.

## Автоматизация (после мержа в main)

Workflow **AI Docs Sync** (`.github/workflows/ai-docs-sync.yml`):

1. Push в `main` с изменениями в `app/**`
2. GitHub Actions вызывает [llm_agent](https://github.com/Sergey1057/llm_agent) File Assistant
3. Обновляются `REDME.md` и `docs/`, коммит `docs: AI sync после мержа [skip docs]`

Нужны те же секреты `GROQ_API_KEY` / `GIGACHAT_API_KEY`, что и для PR review.

## Связь с AI PR Review

| Инструмент | Когда |
|------------|--------|
| `.github/workflows/ai-pr-review.yml` | На каждый PR — review кода |
| `.github/workflows/ai-docs-sync.yml` | После мержа в `main` — авто-обновление `REDME.md` и `docs/` |
| File Assistant (локально) | Ручной запуск / отладка |
