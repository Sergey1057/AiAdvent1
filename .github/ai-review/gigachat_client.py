"""
Минимальный клиент GigaChat API для PR review (OAuth + chat/completions).
"""

from __future__ import annotations

import json
import os
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any

import certifi

GIGACHAT_OAUTH_URL = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
GIGACHAT_API_V1_BASE = "https://gigachat.devices.sberbank.ru/api/v1"
DEFAULT_MODEL = "GigaChat"
DEFAULT_SCOPE = "GIGACHAT_API_PERS"

_token_cache: tuple[str, int] | None = None


def _env_flag_false(name: str, default: str = "1") -> bool:
    v = (os.environ.get(name) or default).strip().lower()
    return v in ("0", "false", "no", "off")


def _ssl_verify_disabled() -> bool:
    v = (os.environ.get("GIGACHAT_SSL_VERIFY") or "").strip().lower()
    return v in ("0", "false", "no", "off")


def _ssl_ca_bundle_path() -> str | None:
    for key in ("GIGACHAT_CA_BUNDLE", "SSL_CERT_FILE", "REQUESTS_CA_BUNDLE"):
        raw = (os.environ.get(key) or "").strip()
        if not raw:
            continue
        p = Path(raw).expanduser()
        if p.is_file():
            return str(p.resolve())
    return None


def ssl_context_for_url(url: str) -> ssl.SSLContext | None:
    if not url.startswith("https:"):
        return None
    if _ssl_verify_disabled():
        return ssl._create_unverified_context()
    ca = _ssl_ca_bundle_path()
    if ca:
        return ssl.create_default_context(cafile=ca)
    if not _env_flag_false("GIGACHAT_USE_TRUSTSTORE", default="1"):
        try:
            import truststore

            return truststore.SSLContext()
        except ImportError:
            pass
    return ssl.create_default_context(cafile=certifi.where())


def _http_request(
    url: str,
    *,
    method: str = "GET",
    headers: dict[str, str] | None = None,
    data: bytes | None = None,
    timeout: float = 120.0,
) -> bytes:
    req = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    ctx = ssl_context_for_url(url)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=ctx) as resp:
            return resp.read()
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        hint = ""
        if "certificate" in f"{e} {body}".lower():
            hint = (
                " SSL: установите truststore (pip install truststore) или "
                "GIGACHAT_SSL_VERIFY=0 только для отладки."
            )
        raise RuntimeError(f"HTTP {e.code} {url}: {body[:600]}{hint}") from e


def fetch_access_token(
    authorization_key: str,
    *,
    oauth_url: str = GIGACHAT_OAUTH_URL,
    scope: str = DEFAULT_SCOPE,
    timeout_sec: float = 60.0,
) -> str:
    global _token_cache
    now_ms = int(time.time() * 1000)
    if _token_cache is not None:
        tok, exp_ms = _token_cache
        if now_ms < exp_ms - 60_000:
            return tok

    body = urllib.parse.urlencode({"scope": scope}).encode("utf-8")
    headers = {
        "Content-Type": "application/x-www-form-urlencoded",
        "Accept": "application/json",
        "RqUID": str(uuid.uuid4()),
        "Authorization": f"Basic {authorization_key.strip()}",
        "User-Agent": "aiadvent1-pr-review",
    }
    raw = _http_request(oauth_url, method="POST", headers=headers, data=body, timeout=timeout_sec)
    data = json.loads(raw.decode("utf-8"))
    token = data.get("access_token")
    if not isinstance(token, str) or not token:
        raise RuntimeError(f"OAuth: нет access_token: {raw.decode()[:400]}")
    expires_at = data.get("expires_at")
    if isinstance(expires_at, (int, float)):
        exp_ms = int(expires_at)
    else:
        exp_ms = now_ms + 25 * 60 * 1000
    _token_cache = (token, exp_ms)
    return token


def chat_completion(
    user_message: str,
    *,
    system_message: str | None = None,
    model: str | None = None,
    temperature: float = 0.2,
    max_tokens: int = 4096,
) -> str:

    auth_key = ""
    for name in (
        "GIGACHAT_API_KEY",
        "GIGACHAT_AUTH_KEY",
        "GIGACHAT_CREDENTIALS",
        "GIGACHAT_AUTHORIZATION_KEY",
    ):
        auth_key = (os.environ.get(name) or "").strip()
        if auth_key:
            break
    if not auth_key:
        raise RuntimeError(
            "GIGACHAT_API_KEY не задан в окружении runner.\n"
            "GitHub: Settings → Secrets and variables → Actions → New repository secret\n"
            "Имя: GIGACHAT_API_KEY (именно Secrets, не Variables)\n"
            "Значение: Authorization key из кабинета GigaChat API (Base64).\n"
            "CLI: gh secret set GIGACHAT_API_KEY --repo OWNER/REPO"
        )

    oauth_url = (os.environ.get("GIGACHAT_OAUTH_URL") or GIGACHAT_OAUTH_URL).strip()
    api_base = (os.environ.get("GIGACHAT_API_BASE") or GIGACHAT_API_V1_BASE).rstrip("/")
    scope = (os.environ.get("GIGACHAT_SCOPE") or DEFAULT_SCOPE).strip()
    resolved_model = (model or os.environ.get("GIGACHAT_MODEL") or DEFAULT_MODEL).strip()

    token = fetch_access_token(auth_key, oauth_url=oauth_url, scope=scope)
    messages: list[dict[str, str]] = []
    if system_message:
        messages.append({"role": "system", "content": system_message})
    messages.append({"role": "user", "content": user_message})

    payload: dict[str, Any] = {
        "model": resolved_model,
        "messages": messages,
        "stream": False,
        "temperature": temperature,
        "max_tokens": max_tokens,
    }
    url = f"{api_base}/chat/completions"
    headers = {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": f"Bearer {token}",
        "User-Agent": "aiadvent1-pr-review",
    }
    print(f"GigaChat: model={resolved_model}, prompt_chars={len(user_message)}", flush=True)
    raw = _http_request(
        url,
        method="POST",
        headers=headers,
        data=json.dumps(payload).encode("utf-8"),
    )
    response_json = json.loads(raw.decode("utf-8"))
    choice = (response_json.get("choices") or [{}])[0]
    msg = choice.get("message") or {}
    content = msg.get("content")
    if isinstance(content, str) and content.strip():
        return content.strip()
    reasoning = msg.get("reasoning_content")
    if isinstance(reasoning, str) and reasoning.strip():
        return reasoning.strip()
    raise RuntimeError(f"GigaChat: пустой ответ: {json.dumps(response_json)[:500]}")
