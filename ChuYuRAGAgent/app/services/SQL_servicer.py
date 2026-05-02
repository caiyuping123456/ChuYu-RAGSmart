import json
import os

from dotenv import load_dotenv
import pymysql

from app.mapper.RedisLite import get_redis_client
from app.mapper.SQLLite import get_conn

load_dotenv()

def get_agent_info(id, user_id):
    conn = None
    default_result = {
        'custom_api_url': os.getenv('BASE_URL'),
        'model_name': os.getenv('MODEL_NAME'),
        'custom_api_key': os.getenv('API_KEY'),
        'system_prompt': """
            你是一个智能体，请就真实依据回答用户的问题。
        """,
        'provider':'openai'
    }
    ## 先查redis
    redis_client = get_redis_client()
    redis_key = f"{id}:{user_id}:"
    redis_result = redis_client.get(redis_key)
    if redis_result is not None:
        if isinstance(redis_result, (bytes, bytearray)):
            redis_result = redis_result.decode('utf-8')
        data = json.loads(redis_result)
        # 兼容历史脏数据：如果解出来还是字符串，再解一次
        if isinstance(data, str):
            data = json.loads(data)

        if not data['custom_api_url'] or not data['custom_api_key']:
            data['custom_api_url'] = default_result['custom_api_url']
            data['custom_api_key'] = default_result['custom_api_key']
        print("Redis data:", data)
        return data
    try:
        conn = get_conn()
        with conn.cursor(pymysql.cursors.DictCursor) as cursor:
            sql = "SELECT custom_api_key,custom_api_url,model_name,system_prompt ,model_type ,provider FROM ai_agent WHERE id= %s AND user_id = %s "
            cursor.execute(sql, (id, user_id,))
            result = cursor.fetchone()
            print("result:", result)
            if result['model_type'] == 'PRESET':
                default_result['model_name'] = result['model_name']
                default_result['system_prompt'] = result['system_prompt']
                default_result['provider'] = result.get('provider', 'openai')
                if result:
                    redis_client.set(redis_key, json.dumps(default_result), ex=3600, nx=True)
                return default_result
            if result:
                redis_client.set(redis_key, json.dumps(result), ex=3600, nx=True)
            return result if result is not None else default_result
    except Exception as e:
        print(f"数据库查询失败：{e}")
        return default_result
    finally:
        if conn:
            conn.close()
