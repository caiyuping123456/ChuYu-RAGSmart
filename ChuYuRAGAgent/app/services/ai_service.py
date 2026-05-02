import json

import httpx
from langchain.agents import create_agent
from langchain_core.messages import HumanMessage, SystemMessage

from app.agent.LLMBot import get_LLM_Model
from app.agent.memory_saver import load_memory_messages, save_turn
from app.mcp.get_mcp_tools import get_mcp_tools
from app.model.chat_request import ChatRequest
from app.services.SQL_servicer import get_agent_info


class AIService:
    @staticmethod
    async def get_streaming_response(request: ChatRequest):
        agent_info = get_agent_info(request.agentId, request.userId)
        system_prompt = agent_info["system_prompt"]
        question = request.question

        memory_messages = load_memory_messages(request.userId, request.agentId)

        messages = [
            SystemMessage(content=system_prompt),
            *memory_messages,
            HumanMessage(content=question),
        ]

        assistant_full_text = ""

        try:
            llm = get_LLM_Model(
                agent_info["custom_api_url"],
                agent_info["custom_api_key"],
                agent_info["model_name"],
                agent_info["provider"],
            )
            # tools = await get_mcp_tools(request.agentId, request.userId)
            # agent = create_agent(llm, tools)
            agent = create_agent(llm)
            assistant_full_text = ""

            # Agent 自动处理工具调用的流式输出
            async for chunk in agent.astream(messages):
                # 处理不同类型的 chunk
                if "messages" in chunk:
                    msg = chunk["messages"][0]
                    if hasattr(msg, 'content') and msg.content:
                        assistant_full_text += msg.content
                        yield msg.content

            # 这个是持久化
            save_turn(
                user_id=request.userId,
                agent_id=request.agentId,
                user_text=question,
                assistant_text=assistant_full_text,
            )

        except Exception:
            print("log: agent降级回复")
            payload = {
                "model": agent_info["model_name"],
                "messages": [
                    {"role": "system", "content": system_prompt},
                    *[
                        {
                            "role": "user" if m.type == "human" else "assistant",
                            "content": m.content,
                        }
                        for m in memory_messages
                        if getattr(m, "content", None)
                    ],
                    {"role": "user", "content": question},
                ],
                "stream": True,
            }

            headers = {
                "Authorization": f"Bearer {agent_info['custom_api_key']}",
                "Content-Type": "application/json",
            }

            url = f"{agent_info['custom_api_url'].rstrip('/')}/chat/completions"

            try:
                timeout = httpx.Timeout(30.0, read=None)
                async with httpx.AsyncClient(timeout=timeout) as client:
                    async with client.stream(
                        "POST", url, json=payload, headers=headers
                    ) as resp:
                        if resp.status_code != 200:
                            yield f"API Error: {resp.status_code}"
                            return

                        async for line in resp.aiter_lines():
                            line = line.strip()
                            if not line or line == "data: [DONE]":
                                continue

                            if line.startswith("data: "):
                                try:
                                    data = json.loads(line[6:])
                                    content = (
                                        data["choices"][0]
                                        .get("delta", {})
                                        .get("content", "")
                                    )
                                    if content:
                                        assistant_full_text += content
                                        yield content
                                except (json.JSONDecodeError, KeyError, IndexError):
                                    continue

                save_turn(
                    user_id=request.userId,
                    agent_id=request.agentId,
                    user_text=question,
                    assistant_text=assistant_full_text,
                )

            except Exception as e_final:
                yield f"\n[System Error]: {str(e_final)}"
