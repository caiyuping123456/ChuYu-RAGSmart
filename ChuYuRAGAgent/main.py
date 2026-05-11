
from fastapi import FastAPI
from app.api import agent_api

app = FastAPI(title="SmartPAI-AI-Service")

# 像 Java 注册 Controller 一样注册路由
app.include_router(agent_api.router, prefix="/api")
app.include_router(agent_api.router, prefix="/image")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
