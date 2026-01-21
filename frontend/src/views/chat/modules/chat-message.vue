<script setup lang="ts">
// eslint-disable-next-line @typescript-eslint/no-unused-vars
import { nextTick } from 'vue';
import { VueMarkdownIt } from 'vue-markdown-shiki';
import { formatDate } from '@/utils/common';
defineOptions({ name: 'ChatMessage' });

const props = defineProps<{ msg: Api.Chat.Message }>();

const authStore = useAuthStore();

function handleCopy(content: string) {
  navigator.clipboard.writeText(content);
  window.$message?.success('已复制');
}

const chatStore = useChatStore();

// 存储文件名和对应的事件处理
const sourceFiles = ref<Array<{fileName: string, id: string}>>([]);

// 处理来源文件链接的函数
function processSourceLinks(text: string): string {
  // 匹配 (来源#数字: 文件名) 的正则表达式
  const sourcePattern = /\(来源#(\d+):\s*([^)]+)\)/g;

  return text.replace(sourcePattern, (_match, sourceNum, fileName) => {
    // 为文件名创建可点击的链接
    const linkClass = 'source-file-link';
    const encodedFileName = encodeURIComponent(fileName.trim());
    const fileId = `source-file-${sourceFiles.value.length}`;

    // 存储文件信息
    sourceFiles.value.push({
      fileName: encodedFileName,
      id: fileId
    });

    return `(来源#${sourceNum}: <span class="${linkClass}" data-file-id="${fileId}">${fileName}</span>)`;
  });
}

const content = computed(() => {
  chatStore.scrollToBottom?.();
  const rawContent = props.msg.content ?? '';

  // 只对助手消息处理来源链接
  if (props.msg.role === 'assistant') {
    return processSourceLinks(rawContent);
  }

  return rawContent;
});

// 处理内容点击事件（事件委托）
function handleContentClick(event: MouseEvent) {
  const target = event.target as HTMLElement;

  // 检查点击的是否是文件链接
  if (target.classList.contains('source-file-link')) {
    const fileId = target.getAttribute('data-file-id');
    if (fileId) {
      const file = sourceFiles.value.find(f => f.id === fileId);
      if (file) {
        handleSourceFileClick(file.fileName);
      }
    }
  }
}

// 处理来源文件点击事件
async function handleSourceFileClick(fileName: string) {
  const decodedFileName = decodeURIComponent(fileName);
  console.log('点击了来源文件:', decodedFileName);

  try {
    window.$message?.loading(`正在获取文件下载链接: ${decodedFileName}`, {
      duration: 0,
      closable: false
    });

    // 调用文件下载接口
    const { error, data } = await request<Api.Document.DownloadResponse>({
      url: 'documents/download',
      params: {
        fileName: decodedFileName,
        token: authStore.token
      },
      baseURL: '/proxy-api'
    });

    window.$message?.destroyAll();

    if (error) {
      window.$message?.error(`文件下载失败: ${error.response?.data?.message || '未知错误'}`);
      return;
    }

    if (data?.downloadUrl) {
      // 在新窗口打开下载链接
      window.open(data.downloadUrl, '_blank');
      window.$message?.success(`文件下载链接已打开: ${decodedFileName}`);
    } else {
      window.$message?.error('未能获取到下载链接');
    }
  } catch (err) {
    window.$message?.destroyAll();
    console.error('文件下载失败:', err);
    window.$message?.error(`文件下载失败: ${decodedFileName}`);
  }
}
</script>

<template>
  <div class="message-container">
    <div v-if="msg.role === 'user'" class="user-header">
      <NAvatar class="user-avatar">
        <SvgIcon icon="ph:user-circle" class="avatar-icon" />
      </NAvatar>
      <div class="user-info">
        <NText class="username">{{ authStore.userInfo.username }}</NText>
        <NText class="timestamp">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <div v-else class="assistant-header">
      <NAvatar class="assistant-avatar">
        <SystemLogo class="avatar-logo" />
      </NAvatar>
      <div class="assistant-info">
        <NText class="assistant-name">派聪明</NText>
        <NText class="timestamp">{{ formatDate(msg.timestamp) }}</NText>
      </div>
    </div>
    <NText v-if="msg.status === 'pending'" class="loading-indicator">
      <icon-eos-icons:three-dots-loading class="loading-icon" />
    </NText>
    <NText v-else-if="msg.status === 'error'" class="error-text">服务器繁忙，请稍后再试</NText>
    <div v-else-if="msg.role === 'assistant'" class="assistant-content" @click="handleContentClick">
      <VueMarkdownIt :content="content" />
    </div>
    <NText v-else-if="msg.role === 'user'" class="user-content">{{ content }}</NText>
    <NDivider class="content-divider" />
    <div class="action-buttons">
      <NButton quaternary class="copy-btn" @click="handleCopy(msg.content)">
        <template #icon>
          <icon-mynaui:copy />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped lang="scss">
.message-container {
  margin-bottom: 32px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.user-header,
.assistant-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-avatar {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%) !important;
  border-radius: 14px !important;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.25) !important;
}

.assistant-avatar {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border-radius: 14px !important;
  box-shadow: 0 4px 12px rgba(245, 87, 108, 0.25) !important;
}

.avatar-icon {
  font-size: 26px;
  color: #ffffff;
}

.avatar-logo {
  font-size: 26px;
  color: #ffffff;
}

.user-info,
.assistant-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.username,
.assistant-name {
  font-size: 16px;
  font-weight: 600;
  color: #2d3748;
}

.timestamp {
  font-size: 12px;
  color: #a0aec0;
}

.loading-indicator {
  margin-left: 48px;
  margin-top: 8px;
}

.loading-icon {
  font-size: 32px;
  color: #f5576c;
}

.error-text {
  margin-left: 48px;
  margin-top: 8px;
  font-style: italic;
  color: #e53e3e;
}

.assistant-content {
  margin-top: 8px;
  margin-left: 48px;
  color: #4a5568;
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.04) 0%, rgba(240, 147, 251, 0.04) 100%);
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(245, 87, 108, 0.08);
}

.user-content {
  margin-left: 48px;
  margin-top: 8px;
  font-size: 16px;
  color: #2d3748;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.04) 0%, rgba(118, 75, 162, 0.04) 100%);
  border-radius: 16px;
  padding: 16px;
  border: 1px solid rgba(102, 126, 234, 0.08);
}

.content-divider {
  margin-left: 48px;
  width: calc(100% - 48px);
  margin-bottom: 0 !important;
  margin-top: 8px !important;
}

:deep(.n-divider__line) {
  background: linear-gradient(90deg, rgba(245, 87, 108, 0.1) 0%, rgba(240, 147, 251, 0.1) 100%) !important;
}

.action-buttons {
  margin-left: 48px;
  display: flex;
  gap: 16px;
}

.copy-btn {
  color: #a0aec0 !important;
  border-radius: 10px !important;
  transition: all 0.3s ease;
}

.copy-btn:hover {
  color: #f5576c !important;
  background: rgba(245, 87, 108, 0.05) !important;
}

:deep(.source-file-link) {
  color: #f5576c;
  cursor: pointer;
  text-decoration: underline;
  transition: color 0.2s;

  &:hover {
    color: #667eea;
    text-decoration: none;
  }

  &:active {
    color: #764ba2;
  }
}
</style>
