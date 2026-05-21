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

## Связь с AI PR Review

| Инструмент | Когда |
|------------|--------|
| `.github/workflows/ai-pr-review.yml` | На каждый PR — review кода |
| File Assistant | После мержа — актуализировать `docs/` |
