from langchain_mcp_adapters.client import MultiServerMCPClient

from app.mcp.config.get_mcp_info import get_mcp_info


async def get_mcp_tools(agent_id,user_id):
    "这个是获取到mcp的tool工具"
    mcp_info = await get_mcp_info(agent_id, user_id)
    try:
        client = MultiServerMCPClient(mcp_info)
        # 获取所有工具
        tools = await client.get_tools()
        print("获取到的MCP工具:", [t.name for t in tools])
        return tools
    except Exception as e:
        print(f"MCP连接失败: {e}")
        import traceback
        traceback.print_exc()
        return []

