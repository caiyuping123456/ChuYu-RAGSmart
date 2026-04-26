import requests
import json


def test_raw_stream(url,key,model):
    # url = "https://ruoli.dev/v1/chat/completions"  # 确保路径包含 /v1
    # url = "https://api.siliconflow.cn/v1/chat/completions"
    headers = {
        "Authorization": "Bearer "+key,
        "Content-Type": "application/json"
    }
    data = {
        # "model": "gpt-5.2",
        # "model" : "deepseek-ai/DeepSeek-V3.2",
        "model": model,
        "messages": [{"role": "user", "content": "你好"}],
        "stream": True
    }

    response = requests.post(url, headers=headers, json=data, stream=True)

    for line in response.iter_lines():
        if line:
            decoded_line = line.decode('utf-8')
            print(f"原始数据: {decoded_line}")  # 打印每一行原始数据

            if decoded_line.startswith("data: "):
                json_str = decoded_line[6:]
                if json_str.strip() == "[DONE]":
                    break
                try:
                    chunk = json.loads(json_str)
                    # 打印具体内容，看是否有 delta
                    print(f"内容片段: {chunk}")
                except Exception as e:
                    print(f"JSON解析失败: {e}")


# 运行这个函数看看输出
if __name__ == "__main__":
    test_raw_stream()