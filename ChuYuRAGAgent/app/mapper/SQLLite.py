from dbutils.pooled_db import PooledDB
import pymysql

# 数据库配置（实际项目中建议从环境变量或配置文件中读取）
DB_CONFIG = {
    'host': '127.0.0.1',
    'user': 'root',
    'password': '20031217',
    'database': 'paismart',
    'charset': 'utf8mb4'
}

# 创建全局连接池单例
# 这里的 pool 变量只在模块加载时创建一次，全局共享
pool = PooledDB(
    creator=pymysql,
    maxconnections=20,      # 最大连接数，根据服务器性能调整
    mincached=5,            # 启动时开启的空闲连接
    maxcached=10,           # 最多保留的空闲连接
    blocking=True,          # 连接池满时是否阻塞
    **DB_CONFIG             # 展开配置字典
)

def get_conn():
    """获取一个数据库连接"""
    return pool.connection()