from langchain_anthropic import ChatAnthropic
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI

LLM_TIMEOUT = 600  # LLM 请求超时 600 秒

def get_LLM_Model(base_url,api_key,model,provider):
    if provider == 'openai':
        return get_openai_model(base_url,api_key,model)
    elif provider == 'anthropic':
        return get_anthropic_model(base_url,api_key,model)
    elif provider == 'google':
        return get_google_model(base_url,api_key,model)
    return None

# 这个是获取OpenAi模型
def get_openai_model(base_url,api_key,model):
    return ChatOpenAI(
        base_url = base_url,
        openai_api_key =api_key,
        model = model,
        streaming=True,
        request_timeout=LLM_TIMEOUT,
        model_kwargs={
            "stream_options": {"include_usage": True}
        }
    )

# 获取anthropic协议模型
def get_anthropic_model(base_url,api_key,model):
    return ChatAnthropic(
        base_url=base_url,
        model=model,
        api_key=api_key,
        streaming=True,
        timeout=LLM_TIMEOUT,
    )

# 获取google协议模型
def get_google_model(base_url,api_key,model):
    return ChatGoogleGenerativeAI(
        base_url=base_url,
        model = model,
        api_key = api_key,
        streaming=True,
        timeout=LLM_TIMEOUT,
    )
