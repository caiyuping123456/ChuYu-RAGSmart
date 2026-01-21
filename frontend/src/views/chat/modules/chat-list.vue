<script setup lang="ts">
import { NScrollbar } from 'naive-ui';
import { VueMarkdownItProvider } from 'vue-markdown-shiki';
import ChatMessage from './chat-message.vue';

defineOptions({
  name: 'ChatList'
});

const chatStore = useChatStore();
const { list } = storeToRefs(chatStore);

const loading = ref(false);
const scrollbarRef = ref<InstanceType<typeof NScrollbar>>();

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

const params = computed(() => {
  return {
    start_date: dayjs(range.value[0]).format('YYYY-MM-DD'),
    end_date: dayjs(range.value[1]).format('YYYY-MM-DD')
  };
});

watchEffect(() => {
  getList();
});

async function getList() {
  loading.value = true;
  const { error, data } = await request<Api.Chat.Message[]>({
    url: 'users/conversation',
    params: params.value
  });
  if (!error) {
    list.value = data;
  }
  loading.value = false;
}

onMounted(() => {
  chatStore.scrollToBottom = scrollToBottom;
});
</script>

<template>
  <Suspense>
    <NScrollbar ref="scrollbarRef" class="chat-scrollbar">
      <Teleport defer to="#header-extra">
        <div class="date-filter-wrapper">
          <NForm :model="params" label-placement="left" :show-feedback="false" inline>
            <NFormItem label="时间">
              <NDatePicker v-model:value="range" type="daterange" class="date-picker" />
            </NFormItem>
          </NForm>
        </div>
      </Teleport>
      <NSpin :show="loading" class="chat-spin">
        <VueMarkdownItProvider>
          <ChatMessage v-for="(item, index) in list" :key="index" :msg="item" />
        </VueMarkdownItProvider>
      </NSpin>
    </NScrollbar>
  </Suspense>
</template>

<style scoped lang="scss">
.chat-scrollbar {
  height: 0;
  flex: auto;
}

.date-filter-wrapper {
  padding: 0 40px;
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
    border-color: rgba(245, 87, 108, 0.3) !important;
    box-shadow: 0 4px 12px rgba(245, 87, 108, 0.08) !important;
  }

  :deep(.n-input .n-input__input-el) {
    color: #2d3748 !important;
  }

  :deep(.n-input .n-input__placeholder) {
    color: #a0aec0 !important;
  }

  :deep(.n-input:focus-within) {
    border-color: rgba(245, 87, 108, 0.4) !important;
    box-shadow: 0 0 0 2px rgba(245, 87, 108, 0.1) !important;
  }
}

.chat-spin {
  :deep(.n-spin-container) {
    padding: 16px;
  }
}
</style>
