from app.mapper.SQLLite import get_conn
import pymysql
import json


async def get_mcp_info(agent_id,user_id):
    """通过 userId 和 agentId 获取对应的 MCP 服务配置，返回 MultiServerMCPClient 需要的格式"""
    conn = get_conn()
    with conn.cursor(pymysql.cursors.DictCursor) as cursor:
        cursor.execute(
            "SELECT id, name, transport, url, headers_json, timeout_ms, enabled "
            "FROM ai_agent_mcp WHERE agent_id = %s AND user_id = %s AND enabled = 1 ORDER BY id ASC",
            (agent_id, user_id)
        )
        mcp_info = cursor.fetchall()
    conn.close()

    # 封装成 MultiServerMCPClient 需要的格式
    servers_config = {}
    for row in mcp_info:
        service_name = row["name"]
        config = {
            "transport": row["transport"],
            "url": row["url"]
        }
        if row["headers_json"]:
            try:
                config["headers"] = json.loads(row["headers_json"])
            except json.JSONDecodeError:
                print(f"Warning: Invalid JSON in headers_json for service {service_name}")
        servers_config[service_name] = config
    return servers_config