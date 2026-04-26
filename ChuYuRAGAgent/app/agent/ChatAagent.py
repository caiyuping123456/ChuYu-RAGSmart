from typing import Any

from langgraph.graph import StateGraph

from app.state.chat_state import ChatState


class ChatAgent:
    def __init__(self,chat_bot: Any):
        self.chat_bot = chat_bot
        graph = StateGraph(ChatState)
        graph.add_node("bot",self.chat_node)

        graph.set_entry_point("bot")
        graph.set_finish_point("bot")

        self.app = graph.compile()

    def chat_node(self,state: ChatState)->dict:
        messages = state['messages']
        if not messages:
            raise ValueError("No messages found in chat state.")

        result = self.chat_bot.invoke(messages)
        return {
            "messages": result,
        }


