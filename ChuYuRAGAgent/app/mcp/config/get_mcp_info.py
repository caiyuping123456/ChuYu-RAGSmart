from app.mapper.SQLLite import get_conn
import pymysql
import json

from app.utils.aes_crypt import decrypt_java_aes


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
        transport = row["transport"]
        # 数据库存的是 http，MCP 客户端需要 streamable_http
        if transport == "http":
            transport = "streamable_http"
        config = {
            "transport": transport,
            "url": row["url"],
        }
        if row["headers_json"]:
            try:
                decrypted = decrypt_java_aes(row["headers_json"]).strip()
                if decrypted:
                    # 兼容数据库中存储的 JSON 片段（如 "headers": {...}），补全为合法 JSON
                    if not decrypted.startswith("{"):
                        decrypted = "{" + decrypted + "}"
                    parsed = json.loads(decrypted)
                    # 支持 {"headers": {...}} 或直接 {...} 两种格式
                    config["headers"] = parsed.get("headers", parsed)
            except (json.JSONDecodeError, Exception) as e:
                print(f"Warning: Invalid headers_json for service {service_name}: {e}")
        servers_config[service_name] = config
    print(servers_config)
    return servers_config