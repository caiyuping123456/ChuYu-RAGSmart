import { request } from '../request';
import { getAuthorization } from '../request/shared';

/**
 * 获取智能体列表
 */
export function fetchAgentList() {
  return request<Api.AiAgent.Item[]>({
    url: '/ai-agents',
    method: 'get'
  });
}

/**
 * 获取智能体详情
 * @param id 智能体ID
 */
export function fetchAgentDetail(id: number) {
  return request<Api.AiAgent.Item>({
    url: `/ai-agents/${id}`,
    method: 'get'
  });
}

/**
 * 创建智能体
 * @param data 智能体表单数据
 */
export function fetchCreateAgent(data: Api.AiAgent.Form) {
  return request({
    url: '/ai-agents',
    method: 'post',
    data
  });
}

/**
 * 更新智能体
 * @param id 智能体ID
 * @param data 智能体表单数据
 */
export function fetchUpdateAgent(id: number, data: Api.AiAgent.Form) {
  return request({
    url: `/ai-agents/${id}`,
    method: 'put',
    data
  });
}

/**
 * 删除智能体
 * @param id 智能体ID
 */
export function fetchDeleteAgent(id: number) {
  return request({
    url: `/ai-agents/${id}`,
    method: 'delete'
  });
}

/**
 * 用户对话 - SSE 流式接口
 * 返回 Response 对象，由调用方读取流
 * @param data 请求参数
 */
export async function AgentStream(data: Api.AiAgent.Stream): Promise<Response> {
  const Authorization = getAuthorization();
  const baseUrl = import.meta.env.DEV ? '/proxy-default' : '';

  console.log('AgentStream Authorization:', Authorization ? 'exists' : 'empty');
  console.log('AgentStream request:', `${baseUrl}/ai-agents/stream`);

  const response = await fetch(`${baseUrl}/ai-agents/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: Authorization || ''
    },
    body: JSON.stringify(data)
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response;
}
