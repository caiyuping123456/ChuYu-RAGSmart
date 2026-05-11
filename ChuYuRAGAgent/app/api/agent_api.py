from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.model.chat_request import ChatRequest
from app.services.ai_service import AIService

router = APIRouter()

@router.post("/v1/agent")
async def chat_agent(request: ChatRequest, authorization: str = Header(None)):
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing Token")

    return StreamingResponse(
        AIService.get_streaming_response(request),
        media_type="text/plain",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
        }
    )