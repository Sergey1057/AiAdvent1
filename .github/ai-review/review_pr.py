#!/usr/bin/env python3
"""
Self-contained AI PR review для AiAdvent1 (без репозитория llm_agent на GitHub).

Зависимости: certifi (SSL). GitHub API + Groq API через stdlib urllib.
RAG: лексический поиск по REDME.md и docs/.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import ssl
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import certifi

_SCRIPT_DIR = Path(__file__).resolve().parent
_PROMPT_FILE = _SCRIPT_DIR / "prompt.txt"
_CODE_SUFFIXES = frozenset({".kt", ".java", ".kts", ".xml", ".gradle", ".properties", ".md"})
_STOPWORDS = frozenset(
    "и в на с по для от к из а о что это как не при или же бы ли уже их".split()
)


@dataclass
class PrFileChange:
    filename: str
    status: str
    patch: str
    additions: int
    deletions: int


@dataclass
class PullRequestContext:
    owner: str
    repo: str
    number: int
    title: str
    body: str
    base_ref: str
    head_ref: str
    head_sha: str
    files: list[PrFileChange]
    unified_diff: str


@dataclass
class DocChunk:
    source: str
    section: str
    text: str


def _github_headers(token: str) -> dict[str, str]:
    return {
        "Accept": "application/vnd.github+json",
        "Authorization": f"Bearer {token}",
        "User-Agent": "aiadvent1-pr-review",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def _http_get(url: str, token: str, *, accept: str | None = None) -> bytes:
    headers = _github_headers(token)
    if accept:
        headers["Accept"] = accept
    req = urllib.request.Request(url, headers=headers, method="GET")
    ctx = ssl.create_default_context(cafile=certifi.where())
    with urllib.request.urlopen(req, timeout=90.0, context=ctx) as resp:
        return resp.read()


def _http_post_json(url: str, headers: dict[str, str], payload: dict[str, Any]) -> dict[str, Any]:
    body = json.dumps(payload).encode("utf-8")
    h = dict(headers)
    h["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=body, headers=h, method="POST")
    ctx = ssl.create_default_context(cafile=certifi.where())
    with urllib.request.urlopen(req, timeout=120.0, context=ctx) as resp:
        raw = resp.read().decode("utf-8")
    data = json.loads(raw)
    return data if isinstance(data, dict) else {"raw": data}


def fetch_pull_request_context(owner: str, repo: str, pr_number: int, token: str) -> PullRequestContext:
    base = f"https://api.github.com/repos/{owner}/{repo}"
    pr_raw = _http_get(f"{base}/pulls/{pr_number}", token)
    pr = json.loads(pr_raw.decode("utf-8"))
    if not isinstance(pr, dict):
        raise RuntimeError("Некорректный ответ GitHub API для PR.")

    files: list[PrFileChange] = []
    page = 1
    while True:
        files_raw = _http_get(f"{base}/pulls/{pr_number}/files?per_page=100&page={page}", token)
        batch = json.loads(files_raw.decode("utf-8"))
        if not isinstance(batch, list) or not batch:
            break
        for item in batch:
            if not isinstance(item, dict):
                continue
            files.append(
                PrFileChange(
                    filename=str(item.get("filename") or ""),
                    status=str(item.get("status") or "modified"),
                    patch=str(item.get("patch") or ""),
                    additions=int(item.get("additions") or 0),
                    deletions=int(item.get("deletions") or 0),
                )
            )
        if len(batch) < 100:
            break
        page += 1

    try:
        unified_diff = _http_get(
            f"{base}/pulls/{pr_number}",
            token,
            accept="application/vnd.github.diff",
        ).decode("utf-8", errors="replace")
    except urllib.error.HTTPError:
        unified_diff = _build_unified_diff_from_patches(files)

    base_obj = pr.get("base") if isinstance(pr.get("base"), dict) else {}
    head_obj = pr.get("head") if isinstance(pr.get("head"), dict) else {}
    return PullRequestContext(
        owner=owner,
        repo=repo,
        number=pr_number,
        title=str(pr.get("title") or ""),
        body=str(pr.get("body") or ""),
        base_ref=str(base_obj.get("ref") or ""),
        head_ref=str(head_obj.get("ref") or ""),
        head_sha=str(head_obj.get("sha") or ""),
        files=files,
        unified_diff=unified_diff,
    )


def _build_unified_diff_from_patches(files: list[PrFileChange]) -> str:
    parts: list[str] = []
    for f in files:
        if f.patch.strip():
            parts.append(f"diff --git a/{f.filename} b/{f.filename}\n{f.patch}")
    return "\n".join(parts)


def _truncate(text: str, max_chars: int) -> str:
    text = text.strip()
    if len(text) <= max_chars:
        return text
    return text[: max_chars - 40] + "\n\n… (обрезано)"


def _tokenize(text: str) -> set[str]:
    words = re.findall(r"[a-zA-Zа-яА-ЯёЁ0-9_./-]+", text.lower())
    return {w for w in words if len(w) > 2 and w not in _STOPWORDS}


def collect_doc_paths(project_root: Path) -> list[Path]:
    root = project_root.resolve()
    found: list[Path] = []
    for name in ("README.md", "REDME.md", "readme.md"):
        p = root / name
        if p.is_file():
            found.append(p)
    docs = root / "docs"
    if docs.is_dir():
        for p in sorted(docs.rglob("*")):
            if p.is_file() and p.suffix.lower() in (".md", ".txt"):
                found.append(p)
    return found


def _chunk_markdown(
    path: Path,
    text: str,
    *,
    project_root: Path | None = None,
    max_chunk: int = 1200,
) -> list[DocChunk]:
    rel = path.name
    if project_root is not None:
        try:
            rel = str(path.resolve().relative_to(project_root.resolve()))
        except ValueError:
            rel = path.name

    chunks: list[DocChunk] = []
    parts = re.split(r"(?=^##\s)", text, flags=re.MULTILINE)
    if len(parts) <= 1:
        for i in range(0, len(text), max_chunk):
            piece = text[i : i + max_chunk].strip()
            if piece:
                chunks.append(DocChunk(rel, f"part-{i // max_chunk + 1}", piece))
        return chunks or [DocChunk(rel, "", text[:max_chunk])]

    for part in parts:
        part = part.strip()
        if not part:
            continue
        m = re.match(r"^##\s+(.+)$", part.split("\n", 1)[0])
        section = m.group(1).strip() if m else ""
        if len(part) <= max_chunk:
            chunks.append(DocChunk(rel, section, part))
        else:
            for i in range(0, len(part), max_chunk):
                piece = part[i : i + max_chunk].strip()
                if piece:
                    chunks.append(DocChunk(rel, section or f"part-{i // max_chunk + 1}", piece))
    return chunks


def build_doc_chunks(project_root: Path) -> list[DocChunk]:
    all_chunks: list[DocChunk] = []
    root = project_root.resolve()
    for path in collect_doc_paths(root):
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        all_chunks.extend(_chunk_markdown(path, text, project_root=root))
    return all_chunks


def retrieve_doc_excerpts(query: str, chunks: list[DocChunk], *, top_k: int = 5) -> str:
    if not chunks:
        return "(Документация не найдена: добавьте REDME.md или docs/)"
    q_tokens = _tokenize(query)
    if not q_tokens:
        q_tokens = _tokenize("android kotlin groq compose")

    scored: list[tuple[float, DocChunk]] = []
    for ch in chunks:
        t_tokens = _tokenize(ch.text)
        if not t_tokens:
            continue
        overlap = len(q_tokens & t_tokens)
        bonus = 2.0 if any(w in ch.source.lower() for w in ("api", "groq", "structure")) else 0.0
        score = overlap + bonus
        if score > 0:
            scored.append((score, ch))

    scored.sort(key=lambda x: -x[0])
    top = [c for _, c in scored[:top_k]] if scored else chunks[:top_k]

    lines = ["## Выдержки из документации (RAG)"]
    for ch in top:
        header = f"### [{ch.source}]"
        if ch.section:
            header += f" — {ch.section}"
        lines.append(header)
        lines.append(ch.text.strip())
    return "\n".join(lines)


def format_changed_files_list(files: list[PrFileChange]) -> str:
    return (
        "\n".join(
            f"- `{f.filename}` ({f.status}, +{f.additions}/-{f.deletions})" for f in files
        )
        or "(нет файлов)"
    )


def format_diff_for_prompt(ctx: PullRequestContext, *, max_chars: int = 24_000) -> str:
    diff = ctx.unified_diff.strip()
    if not diff:
        parts = []
        for f in ctx.files:
            if f.patch.strip():
                parts.append(f"### {f.filename}\n```diff\n{f.patch}\n```")
        diff = "\n\n".join(parts)
    return _truncate(diff, max_chars)


def read_code_snippets(project_root: Path, filenames: list[str], *, max_files: int = 8) -> str:
    root = project_root.resolve()
    parts: list[str] = []
    n = 0
    for name in filenames:
        if n >= max_files:
            parts.append("… (остальные файлы опущены)")
            break
        p = root / name
        if not p.is_file():
            continue
        if p.suffix.lower() not in _CODE_SUFFIXES and p.name not in ("REDME.md",):
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        parts.append(f"### Исходник: `{name}`\n```\n{_truncate(text, 2500)}\n```")
        n += 1
    return "\n\n".join(parts)


def load_prompt_template() -> str:
    if _PROMPT_FILE.is_file():
        return _PROMPT_FILE.read_text(encoding="utf-8")
    raise FileNotFoundError(f"prompt.txt не найден: {_PROMPT_FILE}")


def build_user_message(
    ctx: PullRequestContext,
    *,
    rag_block: str,
    code_snippets: str,
    project_root: Path,
) -> str:
    tpl = load_prompt_template()
    pr_meta = (
        f"Репозиторий: {ctx.owner}/{ctx.repo}\n"
        f"PR #{ctx.number}: {ctx.title}\n"
        f"Ветки: {ctx.head_ref} → {ctx.base_ref}\n"
        f"HEAD: {ctx.head_sha[:12] if ctx.head_sha else '—'}\n"
        f"Корень проекта: {project_root}"
    )
    if ctx.body.strip():
        pr_meta += f"\nОписание PR:\n{ctx.body.strip()[:1500]}"

    rag_full = rag_block.strip()
    if code_snippets.strip():
        rag_full = f"{rag_full}\n\n## Фрагменты изменённых файлов\n{code_snippets}".strip()

    msg = tpl.replace("{{PR_META}}", pr_meta)
    msg = msg.replace("{{CHANGED_FILES}}", format_changed_files_list(ctx.files))
    msg = msg.replace("{{DIFF}}", format_diff_for_prompt(ctx))
    msg = msg.replace("{{RAG_EXCERPTS}}", rag_full or "(нет)")
    return msg.strip()


def call_groq_chat(user_message: str) -> str:
    api_key = (os.environ.get("GROQ_API_KEY") or "").strip()
    if not api_key:
        raise RuntimeError("Задайте secret GROQ_API_KEY в настройках репозитория.")

    base = (os.environ.get("GROQ_API_BASE") or "https://api.groq.com/openai/v1").rstrip("/")
    model = (os.environ.get("PR_REVIEW_MODEL") or "llama-3.3-70b-versatile").strip()
    url = f"{base}/chat/completions"
    system = (
        "Ты опытный ревьюер Kotlin/Android. Отвечай по-русски, структурированно, "
        "только по предоставленному контексту."
    )
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": system},
            {"role": "user", "content": user_message},
        ],
        "temperature": 0.2,
        "max_tokens": 4096,
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    try:
        data = _http_post_json(url, headers, payload)
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Groq API HTTP {e.code}: {err[:800]}") from e

    choices = data.get("choices")
    if not isinstance(choices, list) or not choices:
        raise RuntimeError(f"Groq API: нет choices в ответе: {json.dumps(data)[:500]}")
    msg = choices[0].get("message") if isinstance(choices[0], dict) else {}
    content = msg.get("content") if isinstance(msg, dict) else ""
    if not str(content).strip():
        raise RuntimeError("Groq API вернул пустой ответ.")
    return str(content).strip()


def post_pr_comment(owner: str, repo: str, pr_number: int, body: str, token: str) -> None:
    url = f"https://api.github.com/repos/{owner}/{repo}/issues/{pr_number}/comments"
    full = "## 🤖 AI Code Review\n\n" + body.strip()
    if len(full) > 65_000:
        full = full[:65_000] + "\n\n… (обрезано)"
    try:
        _http_post_json(url, _github_headers(token), {"body": full})
    except urllib.error.HTTPError as e:
        err = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Комментарий PR: HTTP {e.code}: {err[:500]}") from e


def _env_pr_number() -> int | None:
    event_path = (os.environ.get("GITHUB_EVENT_PATH") or "").strip()
    if event_path and Path(event_path).is_file():
        try:
            ev = json.loads(Path(event_path).read_text(encoding="utf-8"))
            num = ev.get("pull_request", {}).get("number")
            if isinstance(num, int):
                return num
        except (json.JSONDecodeError, OSError):
            pass
    raw = (os.environ.get("PR_NUMBER") or "").strip()
    return int(raw) if raw.isdigit() else None


def _env_repo_parts() -> tuple[str, str] | None:
    repo_full = (os.environ.get("GITHUB_REPOSITORY") or "").strip()
    if "/" in repo_full:
        o, r = repo_full.split("/", 1)
        return o, r
    return None


def run_review(
    *,
    owner: str,
    repo: str,
    pr_number: int,
    token: str,
    project_root: Path,
    dry_run: bool = False,
) -> str:
    ctx = fetch_pull_request_context(owner, repo, pr_number, token)
    query = " ".join(
        p
        for p in (
            ctx.title,
            ctx.body[:400] if ctx.body else "",
            " ".join(f.filename for f in ctx.files[:15]),
            "android kotlin groq review",
        )
        if p
    )
    chunks = build_doc_chunks(project_root)
    rag = retrieve_doc_excerpts(query, chunks)
    code = read_code_snippets(project_root, [f.filename for f in ctx.files])
    user_msg = build_user_message(ctx, rag_block=rag, code_snippets=code, project_root=project_root)
    review = call_groq_chat(user_msg)
    if dry_run:
        print(review)
    else:
        post_pr_comment(owner, repo, pr_number, review, token)
    return review


def main() -> int:
    p = argparse.ArgumentParser(description="AI PR review (self-contained)")
    p.add_argument("--owner")
    p.add_argument("--repo")
    p.add_argument("--pr", type=int)
    p.add_argument("--project-root", type=Path, default=None)
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    token = (os.environ.get("GITHUB_TOKEN") or "").strip()
    if not token:
        print("GITHUB_TOKEN не задан.", file=sys.stderr)
        return 1

    owner = (args.owner or "").strip()
    repo = (args.repo or "").strip()
    if not owner or not repo:
        parts = _env_repo_parts()
        if not parts:
            print("Укажите --owner/--repo или GITHUB_REPOSITORY.", file=sys.stderr)
            return 1
        owner, repo = parts

    pr_number = args.pr if args.pr is not None else _env_pr_number()
    if pr_number is None:
        print("Укажите --pr или запустите из pull_request workflow.", file=sys.stderr)
        return 1

    root = args.project_root
    if root is None:
        ws = (os.environ.get("GITHUB_WORKSPACE") or "").strip()
        root = Path(ws) if ws else Path.cwd()

    try:
        run_review(
            owner=owner,
            repo=repo,
            pr_number=pr_number,
            token=token,
            project_root=root.resolve(),
            dry_run=args.dry_run,
        )
    except Exception as e:
        print(f"Ошибка: {e}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
