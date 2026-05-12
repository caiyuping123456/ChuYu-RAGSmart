my_python_project/          # 项目根目录
│
├── src/                    # 源代码目录 (推荐，避免导入混淆)
│   └── my_package/         # 你的主包名 (建议与项目名一致)
│       ├── __init__.py     # 标识这是一个包
│       ├── core.py         # 核心逻辑模块
│       ├── utils.py        # 工具函数模块
│       ├── models.py       # 数据模型
│       └── config.py       # 配置相关
│
├── tests/                  # 测试目录
│   ├── __init__.py
│   ├── test_core.py
│   └── test_utils.py
│
├── docs/                   # 项目文档
│   ├── README.md
│   └── ... (Sphinx 或 MkDocs 源文件)
│
├── scripts/                # 辅助脚本 (运维、数据分析等)
│   ├── setup_database.py
│   └── data_clean.py
│
├── requirements/           # 依赖管理 (如果不使用 Poetry)
│   ├── base.txt           # 通用依赖
│   ├── dev.txt            # 开发依赖 (pytest, black 等)
│   └── prod.txt           # 生产环境依赖
│
├── .gitignore              # Git 忽略文件
├── .env.example            # 环境变量模板
├── pyproject.toml          # 项目元数据与构建配置 (Poetry 或 PDM 必备)
├── README.md               # 项目说明文档
├── LICENSE                 # 开源许可证 (如 MIT, Apache-2.0)
├── Makefile                # (可选) 常用命令快捷方式
├── Dockerfile              # (可选) 容器化配置
└── .pre-commit-config.yaml # (可选) 代码检查钩子