from langchain_mcp_adapters.client import MultiServerMCPClient

from app.mcp.config.get_mcp_info import get_mcp_info


async def get_mcp_tools(agent_id,user_id):
    "这个是获取到mcp的tool工具"
    mcp_info = await get_mcp_info(agent_id, user_id)
    client = MultiServerMCPClient(mcp_info)
    # 获取所有工具
    return await client.get_tools()

