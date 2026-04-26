from typing import TypedDict, Sequence, Annotated

from langchain_core.messages import BaseMessage
from langgraph.graph import add_messages


class ChatState(TypedDict):
    # 这个是历史对话
    messages: Annotated[Sequence[BaseMessage], add_messages]
