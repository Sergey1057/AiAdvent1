#!/usr/bin/env python3
"""
Self-contained AI docs sync для AiAdvent1 (без репозитория llm_agent на GitHub).

После push в main с изменениями app/** обновляет REDME.md и docs/ через LLM.
"""

from __future__ import annotations

import difflib
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

_BUNDLE = Path(__file__).resolve().parent
_AI_REVIEW = _BUNDLE.parent / "ai-review"
if str(_AI_REVIEW) not in sys.path:
    sys.path.insert(0, str(_AI_REVIEW))

from gigachat_client import chat_completion as gigachat_chat_completion  # noqa: E402

_FILE_BLOCK_RE = re.compile(
    r"```file:([^\n`]+)\s*\n(.*?)```",
    re.DOTALL | re.IGNORECASE,
)
_CODE_SUFFIXES = frozenset({".kt", ".java", ".kts"})
_TEXT_SUFFIXES = frozenset({".kt", ".java", ".md", ".txt", ".kts", ".xml", ".gradle", ".properties"})
_SKIP_DIRS = frozenset({".git", ".gradle", "build", ".idea", "node_modules"})
_MAX_READ = 24_000


def _repo_root() -> Path:
    raw = (
        os.environ.get("DOCS_SYNC_PROJECT_ROOT")
        or os.environ.get("GITHUB_WORKSPACE")
        or "."
    ).strip()
    return Path(raw).expanduser().resolve()


def _git(args: list[str], cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *args],
        cwd=str(cwd),
        capture_output=True,
        text=True,
        check=False,
    )


def _skip_reason() -> str | None:
    msg = (os.environ.get("DOCS_SYNC_COMMIT_MSG") or "").strip()
    if not msg:
        ev = os.environ.get("GITHUB_EVENT_PATH", "").strip()
        if ev and Path(ev).is_file():
            try:
                head = json.loads(Path(ev).read_text(encoding="utf-8")).get("head_commit") or {}
                msg = (head.get("message") or "").strip()
            except (OSError, json.JSONDecodeError):
                pass
    low = msg.lower()
    if "[skip docs]" in low or "docs: ai sync" in low:
        return msg[:120]
    return None


def changed_sources(repo: Path) -> list[str]:
    r = _git(["rev-parse", "HEAD~1"], repo)
    if r.returncode != 0:
        show = _git(["show", "--name-only", "--pretty=format:", "HEAD"], repo)
    else:
        show = _git(["diff", "--name-only", "HEAD~1", "HEAD"], repo)
    out: list[str] = []
    for line in (show.stdout or "").splitlines():
        p = line.strip()
        if not p or p in out:
            continue
        if any(x in Path(p).parts for x in _SKIP_DIRS):
            continue
        if Path(p).suffix.lower() in _CODE_SUFFIXES:
            out.append(p)
    return out


def _is_text_path(rel: str) -> bool:
    if any(p in _SKIP_DIRS for p in Path(rel).parts):
        return False
    return Path(rel).suffix.lower() in _TEXT_SUFFIXES or rel in ("REDME.md", "README.md")


def _search_rg(repo: Path, pattern: str, limit: int = 30) -> list[dict[str, str]]:
    try:
        proc = subprocess.run(
            ["rg", "--json", "-e", pattern, "--max-count", str(limit), "."],
            cwd=str(repo),
            capture_output=True,
            text=True,
            check=False,
            timeout=45,
        )
    except (FileNotFoundError, subprocess.TimeoutExpired):
        return []
    if proc.returncode not in (0, 1):
        return []
    matches: list[dict[str, str]] = []
    for line in proc.stdout.splitlines():
        try:
            row = json.loads(line)
        except json.JSONDecodeError:
            continue
        if row.get("type") != "match":
            continue
        data = row.get("data") or {}
        rel = (data.get("path") or {}).get("text") or ""
        if rel and _is_text_path(rel):
            matches.append({"file": rel, "line": str(data.get("line_number", "")), "text": ""})
        if len(matches) >= limit:
            break
    return matches


def _read_file(repo: Path, rel: str) -> str:
    target = (repo / rel).resolve()
    if not str(target).startswith(str(repo.resolve())):
        return ""
    if not target.is_file():
        return ""
    try:
        raw = target.read_bytes()[:_MAX_READ]
    except OSError:
        return ""
    return raw.decode("utf-8", errors="replace")


def _write_file(repo: Path, rel: str, content: str, *, apply: bool) -> dict[str, Any]:
    rel = rel.strip().lstrip("/")
    target = (repo / rel).resolve()
    if not str(target).startswith(str(repo.resolve())):
        return {"status": "error", "error": "path outside repo"}
    old = ""
    if target.is_file():
        try:
            old = target.read_text(encoding="utf-8")
        except OSError:
            pass
    new = content if content.endswith("\n") else content + "\n"
    diff = "".join(
        difflib.unified_diff(
            old.splitlines(keepends=True),
            new.splitlines(keepends=True),
            fromfile=f"a/{rel}",
            tofile=f"b/{rel}",
        )
    )
    if apply:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(new, encoding="utf-8")
    return {"status": "ok", "relative_path": rel, "dry_run": not apply, "diff": diff}


def _git_context(repo: Path) -> str:
    branch = _git(["rev-parse", "--abbrev-ref", "HEAD"], repo)
    st = _git(["status", "-sb"], repo)
    diff = _git(["diff", "HEAD~1", "HEAD", "--"], repo)
    lines = ["## Git", f"branch: {(branch.stdout or '').strip()}"]
    if st.stdout:
        lines.append((st.stdout or "").strip()[:500])
    if diff.returncode == 0 and (diff.stdout or "").strip():
        lines.append("### Diff HEAD~1..HEAD\n" + (diff.stdout or "")[:4000])
    return "\n".join(lines)


def _collect_paths(repo: Path, sources: list[str]) -> list[str]:
    paths: list[str] = []
    for t in ("REDME.md", "docs/structure.md"):
        if (repo / t).is_file() and t not in paths:
            paths.append(t)
    for s in sources:
        if s not in paths:
            paths.append(s)
    for s in sources:
        stem = Path(s).stem
        for m in _search_rg(repo, stem, limit=15):
            f = m["file"]
            if f.endswith(".kt") and f not in paths:
                paths.insert(0, f)
            if len(paths) >= 6:
                break
    return paths[:6]


def _build_prompt(goal: str, repo: Path, paths: list[str]) -> str:
    tpl = (_BUNDLE / "prompt.txt").read_text(encoding="utf-8")
    parts = ["## Прочитанные файлы"]
    for rel in paths:
        body = _read_file(repo, rel)
        parts.append(f"\n### {rel}\n{body[:12000]}")
    msg = tpl.replace("{{GIT_CONTEXT}}", _git_context(repo))
    msg = msg.replace("{{FILES_CONTEXT}}", "\n".join(parts))
    msg = msg.replace("{{GOAL}}", goal)
    return msg.strip()


def _parse_blocks(text: str) -> list[tuple[str, str]]:
    out: list[tuple[str, str]] = []
    for m in _FILE_BLOCK_RE.finditer(text or ""):
        p = m.group(1).strip().lstrip("/")
        if p:
            out.append((p, m.group(2).rstrip("\n") + "\n"))
    return out


def _http_post_json(url: str, headers: dict[str, str], payload: dict[str, Any]) -> dict[str, Any]:
    data = json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, headers={**headers, "Content-Type": "application/json"}, method="POST"
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.loads(resp.read().decode("utf-8"))


def _call_llm(user_message: str) -> str:
    backend = (os.environ.get("DOCS_SYNC_LLM") or os.environ.get("PR_REVIEW_LLM") or "").strip().lower()
    if not backend:
        backend = "groq" if (os.environ.get("GROQ_API_KEY") or "").strip() else "gigachat"

    system = (
        "Ты помощник по документации Android/Kotlin. "
        "Обновляй REDME.md и docs/ только по фактам из контекста."
    )
    if len(user_message) > 28_000:
        user_message = user_message[:27_920] + "\n… (обрезано)"

    if backend in ("groq", "local"):
        key = (os.environ.get("GROQ_API_KEY") or "").strip()
        if not key:
            raise RuntimeError("GROQ_API_KEY не задан")
        base = (os.environ.get("GROQ_API_BASE") or "https://api.groq.com/openai/v1").rstrip("/")
        model = (os.environ.get("DOCS_SYNC_MODEL") or os.environ.get("PR_REVIEW_MODEL") or "llama-3.3-70b-versatile").strip()
        print(f"Groq: model={model}", flush=True)
        data = _http_post_json(
            f"{base}/chat/completions",
            {"Authorization": f"Bearer {key}"},
            {
                "model": model,
                "messages": [
                    {"role": "system", "content": system},
                    {"role": "user", "content": user_message},
                ],
                "temperature": 0.2,
                "max_tokens": 4096,
            },
        )
        return str((data.get("choices") or [{}])[0].get("message", {}).get("content") or "").strip()

    model = (os.environ.get("GIGACHAT_MODEL") or "GigaChat").strip()
    return gigachat_chat_completion(
        user_message, system_message=system, model=model, temperature=0.2, max_tokens=4096
    )


def build_goal(sources: list[str]) -> str:
    custom = (os.environ.get("DOCS_SYNC_GOAL") or "").strip()
    if custom:
        return custom
    if not sources:
        return "обнови REDME.md и docs/structure.md по коду в app/src/main/java"
    names = ", ".join(Path(p).name for p in sources[:4])
    return f"обнови REDME.md и docs/structure.md по изменениям в {names}"


def main() -> int:
    repo = _repo_root()
    skip = _skip_reason()
    if skip:
        print(f"Пропуск: {skip}")
        return 0

    sources = changed_sources(repo)
    force = os.environ.get("DOCS_SYNC_FORCE", "").strip() == "1"
    print(f"repo={repo} sources={sources}", flush=True)

    if not sources and not force:
        print("Нет .kt/.java в последнем коммите — skip (DOCS_SYNC_FORCE=1 для ручного запуска)")
        return 0

    goal = build_goal(sources)
    paths = _collect_paths(repo, sources)
    print(f"goal={goal!r} read={paths}", flush=True)

    user_msg = _build_prompt(goal, repo, paths)
    llm_text = _call_llm(user_msg)
    blocks = _parse_blocks(llm_text)
    if not blocks:
        print("::warning::LLM не вернул блоки ```file:...```")
        return 0

    written: list[str] = []
    for rel, content in blocks:
        if rel not in ("REDME.md", "docs/structure.md") and not rel.startswith("docs/"):
            continue
        res = _write_file(repo, rel, content, apply=True)
        if res.get("status") == "ok":
            written.append(rel)
            print(f"written: {rel}", flush=True)

    if written:
        print("OK:", ", ".join(written))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
