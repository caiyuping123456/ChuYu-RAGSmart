<script setup lang="ts">
import type { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from '../chat/modules/chat-message.vue';

defineOptions({
  name: 'ChatHistory'
});

const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

const list = ref<Api.Chat.Message[]>([]);
const loading = ref(false);

const store = useAuthStore();

watch(() => [...list.value], scrollToBottom);

function scrollToBottom() {
  setTimeout(() => {
    scrollbarRef.value?.scrollBy({
      top: 999999999999999,
      behavior: 'auto'
    });
  }, 100);
}

const range = ref<[number, number]>([dayjs().subtract(7, 'day').valueOf(), dayjs().add(1, 'day').valueOf()]);
const userId = ref<number>(store.userInfo.id);

const params = computed(() => {
  return {
    userid: userId.value,
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  if (!params.value.userid) return;
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'admin/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
    scrollToBottom();
  }
  loading.value = false;
}
</script>

<template>
  <div class="history-container">
    <Teleport defer to="#header-extra">
      <div class="filter-wrapper">
        <NForm :model="params" label-placement="left" :show-feedback="false" inline>
          <NFormItem label="用户">
            <TheSelect
              v-model:value="userId"
              url="admin/users/list"
              :params="{ page: 1, size: 999, orgTag: store.userInfo.primaryOrg }"
              key-field="content"
              value-field="userId"
              label-field="username"
              class="user-select"
              :clearable="false"
            />
          </NFormItem>
          <NFormItem label="时间">
            <NDatePicker v-model:value="range" type="daterange" class="date-picker" />
          </NFormItem>
        </NForm>
      </div>
    </Teleport>
    <NScrollbar class="history-scrollbar">
      <NSpin :show="loading" class="history-spin">
        <VueMarkdownItProvider>
          <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" />
        </VueMarkdownItProvider>
        <NEmpty v-if="!list.length" description="暂无数据" class="empty-state" />
      </NSpin>
    </NScrollbar>
  </div>
</template>

<style scoped lang="scss">
.history-container {
  height: 100%;
  position: relative;
  background: linear-gradient(135deg, #fafafa 0%, #f0f7ff 50%, #fff5f8 100%);
  border-radius: 24px;
  padding: 20px;
}

/* 装饰性背景 */
.history-container::before {
  content: '';
  position: absolute;
  top: -50px;
  right: -30px;
  width: 180px;
  height: 180px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.1) 0%, rgba(118, 75, 162, 0.1) 100%);
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}

.filter-wrapper {
  padding: 0 24px;
}

.user-select {
  min-width: 200px;

  :deep(.n-base-selection) {
    background: rgba(255, 255, 255, 0.9) !important;
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.8) !important;
    border-radius: 12px !important;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04) !important;
    transition: all 0.3s ease;
  }

  :deep(.n-base-selection:hover) {
    border-color: rgba(102, 126, 234, 0.4) !important;
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1) !important;
  }
}

.date-picker {
  :deep(.n-input) {
    background: rgba(255, 255, 255, 0.9) !important;
    backdrop-filter: blur(10px);
    border: 1px solid rgba(255, 255, 255, 0.8) !important;
    border-radius: 12px !important;
    box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04) !important;
    transition: all 0.3s ease;
  }

  :deep(.n-input:hover) {
    border-color: rgba(102, 126, 234, 0.4) !important;
    box-shadow: 0 4px 12px rgba(102, 126, 234, 0.1) !important;
  }
}

.history-scrollbar {
  height: 100%;
}

.history-spin {
  padding: 16px;
}

.empty-state {
  margin-top: 120px;
  :deep(.n-empty__description) {
    color: #a0aec0;
  }
}
</style>
