<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { useAuthStore } from '@/store/modules/auth';
import { AgentStream } from '@/service/api/agent';
import MarkdownIt from 'markdown-it';
import hljs from 'highlight.js';
import katex from '@traptitech/markdown-it-katex';
import linkAttributes from 'markdown-it-link-attributes';

// 导入代码高亮样式
import 'highlight.js/styles/github-dark.css';

defineOptions({
  name: 'AgentChat'
});

// 初始化 Markdown 解析器
const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true,
  breaks: true,
  highlight: (str, lang) => {
    if (lang && hljs.getLanguage(lang)) {
      try {
        return `<pre class="code-block"><code class="hljs language-${lang}">${hljs.highlight(str, { language: lang, ignoreIllegals: true }).value}</code></pre>`;
      } catch {
        // ignore
      }
    }
    return `<pre class="code-block"><code class="hljs">${md.utils.escapeHtml(str)}</code></pre>`;
  }
});

// 启用数学公式和链接属性
md.use(katex, { throwOnError: false, output: 'html' });
md.use(linkAttributes, { attrs: { target: '_blank', rel: 'noopener' } });

interface Props {
  agent: Api.AiAgent.Item;
}

const props = defineProps<Props>();
const emit = defineEmits<{ back: [] }>();
const authStore = useAuthStore();

interface Message {
  role: 'user' | 'assistant';
  content: string;
  status?: 'pending' | 'loading' | 'finished' | 'error';
  images?: string[]; // base64 图片
  timestamp?: string;
}

const inputValue = ref('');
const messages = ref<Message[]>([]);
const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();
const fileInputRef = ref<HTMLInputElement>();
const pendingImages = ref<string[]>([]); // 待发送的图片

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({ top: 999999999, behavior: 'smooth' });
  }, 100);
}

// 图片上传处理
function handleImageButtonClick() {
  fileInputRef.value?.click();
}

function handleFileChange(event: Event) {
  const target = event.target as HTMLInputElement;
  const files = target.files;
  if (!files) return;

  Array.from(files).forEach(file => {
    if (!file.type.startsWith('image/')) {
      window.$message?.warning('只支持图片文件');
      return;
    }

    const reader = new FileReader();
    reader.onload = e => {
      const base64 = e.target?.result as string;
      pendingImages.value.push(base64);
    };
    reader.readAsDataURL(file);
  });

  target.value = ''; // 重置以允许重复选择相同文件
}

function removePendingImage(index: number) {
  pendingImages.value.splice(index, 1);
}

// 清空对话
function handleClearChat() {
  messages.value = [];
  pendingImages.value = [];
}

// 复制消息
function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

// 重新生成
function handleRegenerate(index: number) {
  // 找到上一条用户消息
  for (let i = index - 1; i >= 0; i--) {
    if (messages.value[i].role === 'user') {
      // 删除当前助手回复及之后所有消息
      messages.value.splice(index);
      // 重新发送
      sendToAI(messages.value[i].content, messages.value[i].images || []);
      break;
    }
  }
}

async function handleSend() {
  const text = inputValue.value.trim();
  const images = [...pendingImages.value];

  if ((!text && images.length === 0) || loading.value) return;

  // 用户消息
  messages.value.push({
    role: 'user',
    content: text,
    images: images.length > 0 ? images : undefined,
    timestamp: new Date().toISOString()
  });

  inputValue.value = '';
  pendingImages.value = [];
  scrollToBottom();

  // 发送到 AI
  await sendToAI(text, images);
}

async function sendToAI(text: string, images: string[]) {
  loading.value = true;
  messages.value.push({ role: 'assistant', content: '', status: 'pending' });
  scrollToBottom();

  const assistant = messages.value[messages.value.length - 1];
  assistant.status = 'loading';

  try {
    // 构建请求体
    const requestData: Api.AiAgent.Stream = {
      agentId: props.agent.id,
      userId: authStore.userInfo.id,
      question: text
    };

    // 调用 SSE 流式接口
    const response = await AgentStream(requestData);

    // 使用 ReadableStream 解析 SSE
    const reader = response.body?.getReader();
    const decoder = new TextDecoder();

    if (!reader) {
      throw new Error('No reader available');
    }

    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // 按双换行分割 SSE 事件边界
      const events = buffer.split('\n\n');
      buffer = events.pop() || '';

      for (const event of events) {
        // 提取事件中所有 data: 行，用换行符拼接
        const dataLines: string[] = [];
        for (const line of event.split('\n')) {
          if (line.startsWith('data:')) {
            dataLines.push(line.slice(5));
          }
        }
        const data = dataLines.join('\n');
        if (data) {
          assistant.content += data;
          scrollToBottom();
        }
      }
    }

    assistant.status = 'finished';
    assistant.timestamp = new Date().toISOString();
  } catch (error) {
    assistant.status = 'error';
    assistant.content = '请求失败，请重试';
    window.$message?.error('发送失败');
  } finally {
    loading.value = false;
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault();
    handleSend();
  }
}

// 停止生成
function handleStop() {
  if (loading.value) {
    const lastMsg = messages.value[messages.value.length - 1];
    if (lastMsg.role === 'assistant' && lastMsg.status !== 'finished') {
      lastMsg.status = 'finished';
      if (!lastMsg.content) {
        messages.value.pop();
      }
    }
    loading.value = false;
  }
}

// 检查是否可以发送
const canSend = computed(() => {
  return (inputValue.value.trim() || pendingImages.value.length > 0) && !loading.value;
});

// 渲染 Markdown
function renderMarkdown(content: string) {
  return md.render(content);
}
</script>

<template>
  <div class="agent-chat">
    <!-- 顶部栏 -->
    <div class="chat-header">
      <NButton quaternary @click="emit('back')">
        <template #icon>
          <icon-ant-design:arrow-left-outlined />
        </template>
        返回列表
      </NButton>
      <div class="header-center">
        <span class="agent-name">{{ agent.name }}</span>
        <NTag :type="agent.modelType === 'PRESET' ? 'success' : 'warning'" size="small">
          {{ agent.modelName }}
        </NTag>
      </div>
      <NButton quaternary :disabled="messages.length === 0" @click="handleClearChat">
        <template #icon>
          <icon-ant-design:delete-outlined />
        </template>
        清空
      </NButton>
    </div>

    <!-- 消息区 -->
    <NScrollbar ref="scrollbarRef" class="chat-messages">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon-wrapper">
          <icon-ant-design:robot-outlined class="empty-icon" />
        </div>
        <h3>{{ agent.name }}</h3>
        <p class="empty-desc">{{ agent.description || '开始与这个智能体对话吧' }}</p>
        <div class="prompt-hint">
          <NText depth="3">系统提示词：{{ agent.systemPrompt.slice(0, 80) }}{{ agent.systemPrompt.length > 80 ? '...' : '' }}</NText>
        </div>
        <div class="quick-actions">
          <div class="action-card" @click="inputValue = '请介绍一下你自己'; handleSend()">
            <icon-ant-design:robot-outlined class="action-icon" />
            <span>自我介绍</span>
          </div>
          <div class="action-card" @click="inputValue = '你能帮我做什么？'; handleSend()">
            <icon-ant-design:bulb-outlined class="action-icon" />
            <span>功能介绍</span>
          </div>
        </div>
      </div>

      <div v-else class="message-list">
        <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role]">
          <div class="message-avatar">
            <icon-ant-design:user-outlined v-if="msg.role === 'user'" />
            <icon-ant-design:robot-outlined v-else />
          </div>
          <div class="message-content-wrapper">
            <!-- 用户图片 -->
            <div v-if="msg.role === 'user' && msg.images?.length" class="message-images">
              <img v-for="(img, imgIndex) in msg.images" :key="imgIndex" :src="img" class="message-image" alt="上传的图片">
            </div>
            <!-- 消息内容 -->
            <div class="message-body">
              <div v-if="msg.role === 'assistant' && msg.status === 'pending'" class="loading-dots">
                <span /><span /><span />
              </div>
              <template v-else>
                <!-- 用户消息：纯文本 -->
                <div v-if="msg.content && msg.role === 'user'" class="message-text user">{{ msg.content }}</div>
                <!-- AI 消息：Markdown 渲染 -->
                <div v-if="msg.content && msg.role === 'assistant'" class="message-text assistant" v-html="renderMarkdown(msg.content)"></div>
                <div v-if="msg.role === 'assistant' && msg.status === 'finished'" class="message-actions">
                  <NButton quaternary size="tiny" @click="handleCopy(msg.content)">
                    <template #icon>
                      <icon-ant-design:copy-outlined />
                    </template>
                    复制
                  </NButton>
                  <NButton quaternary size="tiny" @click="handleRegenerate(index)">
                    <template #icon>
                      <icon-ant-design:reload-outlined />
                    </template>
                    重新生成
                  </NButton>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </NScrollbar>

    <!-- 输入区 -->
    <div class="chat-input-area">
      <!-- 待发送图片预览 -->
      <div v-if="pendingImages.length > 0" class="pending-images">
        <div v-for="(img, index) in pendingImages" :key="index" class="pending-image-wrapper">
          <img :src="img" class="pending-image" alt="待发送图片">
          <button class="remove-image-btn" @click="removePendingImage(index)">
            <icon-ant-design:close-outlined />
          </button>
        </div>
      </div>

      <div class="input-row">
        <!-- 工具栏 -->
        <div class="input-toolbar">
          <NButton quaternary size="small" title="上传图片" @click="handleImageButtonClick">
            <template #icon>
              <icon-ant-design:picture-outlined />
            </template>
          </NButton>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            multiple
            hidden
            @change="handleFileChange"
          >
        </div>

        <!-- 输入框 -->
        <NInput
          v-model:value="inputValue"
          type="textarea"
          :autosize="{ minRows: 1, maxRows: 6 }"
          placeholder="输入消息，支持发送图片..."
          :disabled="loading"
          @keydown="handleKeydown"
        />

        <!-- 发送/停止按钮 -->
        <NButton v-if="loading" type="error" @click="handleStop">
          <template #icon>
            <icon-ant-design:stop-outlined />
          </template>
          停止
        </NButton>
        <NButton v-else type="primary" :disabled="!canSend" @click="handleSend">
          <template #icon>
            <icon-ant-design:send-outlined />
          </template>
          发送
        </NButton>
      </div>

      <div class="input-hint">
        <NText depth="3" style="font-size: 12px">
          Enter 发送 · Shift+Enter 换行 · 支持图片上传
        </NText>
      </div>
    </div>
  </div>
</template>

<style scoped>
.agent-chat {
  display: flex;
  flex-direction: column;
  height: 100%;
  border-radius: 16px;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--n-border-color);
  flex-shrink: 0;
}

.header-center {
  display: flex;
  align-items: center;
  gap: 8px;
}

.agent-name {
  font-size: 16px;
  font-weight: 600;
}

.chat-messages {
  flex: 1;
  min-height: 0;
  padding: 20px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 8px;
}

.empty-icon-wrapper {
  width: 72px;
  height: 72px;
  border-radius: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  margin-bottom: 8px;
}

.empty-icon {
  font-size: 36px !important;
  color: #fff !important;
}

.empty-state h3 {
  font-size: 20px;
  margin: 0;
}

.empty-desc {
  color: var(--n-text-color-3);
  margin: 0;
}

.prompt-hint {
  margin-top: 12px;
  padding: 8px 16px;
  border-radius: 8px;
  background: var(--n-color);
  border: 1px solid var(--n-border-color);
  max-width: 400px;
  text-align: center;
}

.quick-actions {
  display: flex;
  gap: 12px;
  margin-top: 20px;
}

.action-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 20px;
  border-radius: 12px;
  border: 1px solid var(--n-border-color);
  cursor: pointer;
  transition: all 0.2s;
}

.action-card:hover {
  border-color: var(--n-primary-color);
  background: var(--n-color-hover);
}

.action-icon {
  font-size: 24px !important;
}

/* 消息列表 */
.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-item {
  display: flex;
  gap: 12px;
  max-width: 85%;
}

.message-item.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 18px;
}

.message-item.user .message-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.message-item.assistant .message-avatar {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%);
  color: #fff;
}

.message-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-width: 100%;
}

/* 消息图片 */
.message-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 300px;
}

.message-image {
  max-width: 150px;
  max-height: 150px;
  border-radius: 8px;
  object-fit: cover;
  cursor: pointer;
}

.message-body {
  padding: 10px 14px;
  border-radius: 14px;
  line-height: 1.6;
  font-size: 14px;
}

.message-item.user .message-body {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.message-item.assistant .message-body {
  background: var(--n-color);
  border: 1px solid var(--n-border-color);
}

.message-text {
  word-break: break-word;
}

/* 用户消息保持原样 */
.message-text.user {
  white-space: pre-wrap;
}

.message-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}

.message-item.assistant:hover .message-actions {
  opacity: 1;
}

/* Loading dots */
.loading-dots {
  display: flex;
  gap: 4px;
  padding: 4px 0;
}

.loading-dots span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--n-text-color-3);
  animation: dotPulse 1.2s infinite;
}

.loading-dots span:nth-child(2) { animation-delay: 0.2s; }
.loading-dots span:nth-child(3) { animation-delay: 0.4s; }

@keyframes dotPulse {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1); }
}

/* 输入区 */
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px 16px;
  border-top: 1px solid var(--n-border-color);
  flex-shrink: 0;
}

/* 待发送图片 */
.pending-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pending-image-wrapper {
  position: relative;
}

.pending-image {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  object-fit: cover;
  border: 1px solid var(--n-border-color);
}

.remove-image-btn {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: none;
  background: #e84749;
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.remove-image-btn:hover {
  background: #c93537;
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-toolbar {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.chat-input-area :deep(.n-input) {
  flex: 1;
}

.input-hint {
  text-align: center;
}

/* Markdown 样式 */
.message-text.assistant {
  line-height: 1.8;
}

.message-text.assistant :deep(h1),
.message-text.assistant :deep(h2),
.message-text.assistant :deep(h3),
.message-text.assistant :deep(h4) {
  margin: 16px 0 8px;
  font-weight: 600;
  line-height: 1.4;
}

.message-text.assistant :deep(h1) { font-size: 1.5em; }
.message-text.assistant :deep(h2) { font-size: 1.3em; }
.message-text.assistant :deep(h3) { font-size: 1.1em; }
.message-text.assistant :deep(h4) { font-size: 1em; }

.message-text.assistant :deep(p) {
  margin: 8px 0;
}

.message-text.assistant :deep(p:first-child) {
  margin-top: 0;
}

.message-text.assistant :deep(p:last-child) {
  margin-bottom: 0;
}

.message-text.assistant :deep(ul),
.message-text.assistant :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.message-text.assistant :deep(li) {
  margin: 4px 0;
}

.message-text.assistant :deep(code:not(.hljs)) {
  background: rgba(0, 0, 0, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 0.9em;
  font-family: 'Consolas', 'Monaco', monospace;
}

.message-text.assistant :deep(.code-block) {
  margin: 12px 0;
  border-radius: 8px;
  overflow: hidden;
  background: #1e1e1e;
}

.message-text.assistant :deep(.code-block code) {
  display: block;
  padding: 16px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.5;
  font-family: 'Consolas', 'Monaco', monospace;
}

.message-text.assistant :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 4px solid var(--n-primary-color);
  background: rgba(0, 0, 0, 0.04);
  border-radius: 0 8px 8px 0;
}

.message-text.assistant :deep(table) {
  margin: 12px 0;
  border-collapse: collapse;
  width: 100%;
}

.message-text.assistant :deep(th),
.message-text.assistant :deep(td) {
  border: 1px solid var(--n-border-color);
  padding: 8px 12px;
  text-align: left;
}

.message-text.assistant :deep(th) {
  background: rgba(0, 0, 0, 0.04);
  font-weight: 600;
}

.message-text.assistant :deep(a) {
  color: var(--n-primary-color);
  text-decoration: none;
}

.message-text.assistant :deep(a:hover) {
  text-decoration: underline;
}

.message-text.assistant :deep(img) {
  max-width: 100%;
  border-radius: 8px;
  margin: 8px 0;
}

/* KaTeX 数学公式样式 */
.message-text.assistant :deep(.katex) {
  font-size: 1.1em;
}

.message-text.assistant :deep(.katex-display) {
  margin: 16px 0;
  overflow-x: auto;
}
</style>
