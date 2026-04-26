
"""Memory persistence for chat.

Stores a compact conversation history per (user_id, agent_id) in Redis.
Designed to be provider-agnostic and easy to inject into LangChain messages.

Key points:
- Keep only the latest N turns to control context length.
- Data is stored as plain JSON (no Python pickles).
"""

from __future__ import annotations

import json
from typing import List

from langchain_core.messages import BaseMessage, HumanMessage, AIMessage

from app.mapper.RedisLite import get_redis_client


def _redis_key(user_id: int | str, agent_id: int | str) -> str:
    return f"{user_id}:{agent_id}:memory:"

## 这个是用于数据加载
def load_memory_messages(user_id: int | str, agent_id: int | str) -> List[BaseMessage]:
    """Load memory messages from Redis.

    Returns a list of LangChain BaseMessage objects (HumanMessage/AIMessage).
    """
    redis_client = get_redis_client()
    raw = redis_client.get(_redis_key(user_id, agent_id))
    if not raw:
        return []

    try:
        items = json.loads(raw)
    except json.JSONDecodeError:
        return []

    messages: List[BaseMessage] = []
    for it in items if isinstance(items, list) else []:
        role = it.get("role")
        content = it.get("content")
        if not content:
            continue
        if role == "user":
            messages.append(HumanMessage(content=content))
        elif role == "assistant":
            messages.append(AIMessage(content=content))

    return messages

## 保存消息
def save_turn(
    *,
    user_id: int | str,
    agent_id: int | str,
    user_text: str,
    assistant_text: str,
    max_turns: int = 10,
    ttl_seconds: int = 3600,
) -> None:
    """Append one user/assistant turn and persist to Redis."""
    if not user_text and not assistant_text:
        return

    redis_client = get_redis_client()
    key = _redis_key(user_id, agent_id)

    history = []
    raw = redis_client.get(key)
    if raw:
        try:
            history = json.loads(raw)
        except json.JSONDecodeError:
            history = []

    if not isinstance(history, list):
        history = []

    if user_text:
        history.append({"role": "user", "content": user_text})
    if assistant_text:
        history.append({"role": "assistant", "content": assistant_text})

    # keep last N turns (2 messages per turn)
    keep = max(1, int(max_turns)) * 2
    history = history[-keep:]

    redis_client.set(key, json.dumps(history, ensure_ascii=False), ex=int(ttl_seconds))

## 用于消息的清理
def clear_memory(user_id: int | str, agent_id: int | str) -> None:
    redis_client = get_redis_client()
    redis_client.delete(_redis_key(user_id, agent_id))

