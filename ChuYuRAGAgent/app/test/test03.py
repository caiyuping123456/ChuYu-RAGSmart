import asyncio
from langchain_openai import ChatOpenAI
from langchain_core.messages import SystemMessage, HumanMessage

async def test():
    llm = ChatOpenAI(
        base_url="https://ruoli.dev/v1",
        openai_api_key="sk-noLAByir2hWGrFRaYQ164E9zABhQNs3uHIUrXlRvoGWmkrIA",
        model="glm-5.1",
    )

    messages = [
        SystemMessage(content="你是一个助手，简短回答"),
        HumanMessage(content="你好")
    ]

    print("开始流式调用...")
    async for chunk in llm.astream(messages):
        if chunk.content:
            print(chunk.content, end="", flush=True)
    print("\n调用完成")

if __name__ == "__main__":
    asyncio.run(test())
