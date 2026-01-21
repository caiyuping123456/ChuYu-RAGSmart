<script setup lang="ts">
const chatStore = useChatStore();
const { input, list, wsStatus, wsData } = storeToRefs(chatStore);

const latestMessage = computed(() => {
  return list.value[list.value.length - 1] ?? {};
});

const isSending = computed(() => {
  return (
    latestMessage.value?.role === 'assistant' && ['loading', 'pending'].includes(latestMessage.value?.status || '')
  );
});

const sendable = computed(
  () => (!input.value.message && !isSending) || ['CLOSED', 'CONNECTING'].includes(wsStatus.value)
);

watch(wsData, val => {
  const data = JSON.parse(val);
  const assistant = list.value[list.value.length - 1];

  if (data.type === 'completion' && data.status === 'finished' && assistant.status !== 'error')
    assistant.status = 'finished';
  if (data.error) assistant.status = 'error';
  else if (data.chunk) {
    assistant.status = 'loading';
    assistant.content += data.chunk;
  }
});

const handleSend = async () => {
  //  判断是否正在发送, 如果发送中，则停止ai继续响应
  if (isSending.value) {
    const { error, data } = await request<Api.Chat.Token>({ url: 'chat/websocket-token', baseURL: 'proxy-api' });
    if (error) return;

    chatStore.wsSend(JSON.stringify({ type: 'stop', _internal_cmd_token: data.cmdToken }));

    list.value[list.value.length - 1].status = 'finished';
    if (!latestMessage.value.content) list.value.pop();
    return;
  }

  list.value.push({
    content: input.value.message,
    role: 'user'
  });
  chatStore.wsSend(input.value.message);
  list.value.push({
    content: '',
    role: 'assistant',
    status: 'pending'
  });
  input.value.message = '';
};

const inputRef = ref();
// 手动插入换行符（确保所有浏览器兼容）
const insertNewline = () => {
  const textarea = inputRef.value;
  const start = textarea.selectionStart;
  const end = textarea.selectionEnd;

  // 在光标位置插入换行符
  input.value.message = `${input.value.message.substring(0, start)}\n${input.value.message.substring(end)}`;

  // 更新光标位置（在插入的换行符之后）
  nextTick(() => {
    textarea.selectionStart = start + 1;
    textarea.selectionEnd = start + 1;
    textarea.focus(); // 确保保持焦点
  });
};

// ctrl + enter 换行
// enter 发送
const handShortcut = (e: KeyboardEvent) => {
  if (e.key === 'Enter') {
    e.preventDefault();

    if (!e.shiftKey && !e.ctrlKey) {
      handleSend();
    } else insertNewline();
  }
};
</script>

<template>
  <div class="input-container">
    <textarea
      ref="inputRef"
      v-model.trim="input.message"
      placeholder="给 派聪明 发送消息"
      class="input-textarea"
      @keydown="handShortcut"
    />
    <div class="input-footer">
      <div class="connection-status">
        <NText class="status-label">连接状态：</NText>
        <icon-eos-icons:loading v-if="wsStatus === 'CONNECTING'" class="status-icon status-connecting" />
        <icon-fluent:plug-connected-checkmark-20-filled v-else-if="wsStatus === 'OPEN'" class="status-icon status-open" />
        <icon-tabler:plug-connected-x v-else class="status-icon status-closed" />
      </div>
      <NButton :disabled="sendable" strong circle type="primary" class="send-btn" @click="handleSend">
        <template #icon>
          <icon-material-symbols:stop-rounded v-if="isSending" class="btn-icon" />
          <icon-guidance:send v-else class="btn-icon" />
        </template>
      </NButton>
    </div>
  </div>
</template>

<style scoped>
.input-container {
  position: relative;
  width: 100%;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 20px;
  padding: 18px;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 4px 20px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(245, 87, 108, 0.03);
}

.input-container:focus-within {
  background: rgba(255, 255, 255, 0.95);
  border-color: rgba(245, 87, 108, 0.2);
  box-shadow:
    0 8px 30px rgba(0, 0, 0, 0.06),
    0 0 0 2px rgba(245, 87, 108, 0.08),
    0 0 40px rgba(245, 87, 108, 0.05);
}

.input-textarea {
  min-height: 44px;
  width: 100%;
  resize: none;
  border: none;
  background: transparent;
  color: #2d3748;
  caret-color: #f5576c;
  outline: none;
  font-size: 15px;
  line-height: 1.6;
}

.input-textarea::placeholder {
  color: #a0aec0;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 14px;
}

.connection-status {
  display: flex;
  align-items: center;
  font-size: 18px;
}

.status-label {
  font-size: 14px;
  color: #718096;
}

.status-icon {
  font-size: 20px;
}

.status-connecting {
  color: #ecc94b;
}

.status-open {
  color: #48bb78;
}

.status-closed {
  color: #f56565;
}

.send-btn {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border: none !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 4px 15px rgba(245, 87, 108, 0.3) !important;
}

.send-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%) !important;
  transform: scale(1.1);
  box-shadow: 0 6px 20px rgba(245, 87, 108, 0.4) !important;
}

.send-btn:disabled {
  background: #e2e8f0 !important;
  opacity: 0.5;
  box-shadow: none !important;
}

.btn-icon {
  font-size: 20px;
}
</style>
