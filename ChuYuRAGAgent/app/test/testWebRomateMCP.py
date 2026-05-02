import asyncio
from langchain_mcp_adapters.client import MultiServerMCPClient
from langchain.agents import create_agent



from app.agent.LLMBot import get_openai_model


async def main():
    # 配置远程 MCP 服务器
    client = MultiServerMCPClient(
        {
            "Parallel Search MCP": {
                "transport": "http",
                "url": "https://search.parallel.ai/mcp"
            }
        }
    )

    # 获取所有工具
    tools = await client.get_tools()
    BASE_URL = "https://api.siliconflow.cn/v1"
    API_KEY = "sk-eejpytshnxihqbeedqsgfuixodazjxjxjetbounsqbzzygtr"
    MODEL_NAME = "deepseek-ai/DeepSeek-V3.2"

    # 创建 Agent
    llm = get_openai_model(BASE_URL,API_KEY,MODEL_NAME)
    agent = create_agent(llm, tools)

    # 执行任务
    response = await agent.ainvoke({
        "messages": [{"role": "user", "content": "查询北京的天气"}]
    })
    print(response)

    # 关闭客户端
    await client.close()


if __name__ == "__main__":
    asyncio.run(main())