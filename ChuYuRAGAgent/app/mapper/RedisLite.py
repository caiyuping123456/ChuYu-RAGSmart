import redis

# 1. 创建连接池 (全局单例)
pool = redis.ConnectionPool(
    host='127.0.0.1',
    port=6379,
    db=0,
    password='123456',
    max_connections=10,      # 最大连接数
    decode_responses=True    # 自动解码
)

# 2. 使用连接池获取连接
def get_redis_client():
    return redis.Redis(connection_pool=pool)

