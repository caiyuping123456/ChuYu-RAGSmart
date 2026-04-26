
from pydantic import BaseModel
from typing import Optional

class ChatRequest(BaseModel):
    # 对应 Java 的 Long，Python 中统一用 int
    agentId: int
    userId: int
    question: str