<script setup lang="ts">
defineOptions({
  name: 'SearchDialog'
});

const loading = ref(false);
const visible = defineModel<boolean>('visible', { default: false });

const { formRef, restoreValidation } = useNaiveForm();

const store = useAuthStore();
const model = ref<Api.KnowledgeBase.SearchParams>(createDefaultModel());

function createDefaultModel(): Api.KnowledgeBase.SearchParams {
  return {
    userId: `${store.userInfo.id}`,
    query: '',
    topK: 10
  };
}

const list = ref<Api.KnowledgeBase.SearchResult[]>([]);

const patterns = ref<string[]>([]);
function highlight(text: string) {
  if (!model.value.query) return false;
  if (text.includes(model.value.query)) return true;
  return false;
}

async function search() {
  loading.value = true;
  const { error, data } = await request<Api.KnowledgeBase.SearchResult[]>({
    url: '/search/hybrid',
    params: model.value,
    baseURL: '/proxy-api'
  });
  if (!error) {
    list.value = data;
    patterns.value = [model.value.query];
  }
  loading.value = false;
}

function reset() {
  model.value = createDefaultModel();
  patterns.value = [];
  list.value = [];
  restoreValidation();
}
watch(visible, () => {
  if (visible.value) {
    reset();
  }
});
</script>

<template>
  <NModal
    v-model:show="visible"
    preset="dialog"
    title="知识库检索"
    :show-icon="false"
    :mask-closable="false"
    class="search-modal"
  >
    <NForm
      ref="formRef"
      :model="model"
      label-placement="left"
      :label-width="60"
      inline
      class="search-form"
      :show-feedback="false"
    >
      <NGrid>
        <NFormItemGi label="topK" path="topK" class="form-item" span="6">
          <NInputNumber
            v-model:value="model.topK"
            placeholder="请输入topK"
            clearable
            :min="1"
            :precision="0"
            :step="10"
          />
        </NFormItemGi>
        <NFormItemGi label="关键字" path="query" class="form-item" span="12">
          <NInput v-model:value="model.query" placeholder="请输入关键字" clearable />
        </NFormItemGi>
        <NFormItemGi span="6">
          <NSpace class="w-full" justify="end">
            <NButton class="reset-btn" @click="reset">
              <template #icon>
                <icon-ic-round-refresh class="btn-icon" />
              </template>
              重置
            </NButton>
            <NButton type="primary" class="search-btn" @click="search">
              <template #icon>
                <icon-ic-round-search class="btn-icon" />
              </template>
              搜索
            </NButton>
          </NSpace>
        </NFormItemGi>
      </NGrid>
    </NForm>
    <NSpin :show="loading">
      <NEmpty v-if="list.length === 0" description="暂无数据" class="empty-state" />
      <NScrollbar v-else class="result-scrollbar">
        <NCard
          v-for="(item, index) in list"
          :key="index"
          class="result-card"
          embedded
          :segmented="{
            content: true,
            footer: 'soft'
          }"
        >
          <div class="result-content">
            <NHighlight
              v-if="highlight(item.textContent)"
              highlight-class="highlight-text"
              :text="item.textContent"
              :patterns="patterns"
            />
            <span v-else class="content-text">{{ item.textContent }}</span>
            <NTag
              :bordered="false"
              draggable
              class="score-tag"
            >
              Score: {{ item.score }}
            </NTag>
          </div>
          <template #footer>
            <span class="source-text">来源：{{ item.fileName }}</span>
          </template>
        </NCard>
      </NScrollbar>
    </NSpin>
  </NModal>
</template>

<style scoped lang="scss">
.search-modal {
  width: 1000px !important;

  :deep(.n-dialog) {
    background: #ffffff !important;
    border-radius: 16px !important;
    border: 1px solid #e2e8f0 !important;
  }

  :deep(.n-dialog__title) {
    color: #2d3748 !important;
    font-weight: 600 !important;
  }
}

.search-form {
  padding-bottom: 16px;
}

.form-item {
  padding-right: 24px;
}

.reset-btn {
  border-radius: 8px !important;
}

.search-btn {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border: none !important;
  border-radius: 8px !important;
}

.btn-icon {
  font-size: 16px;
}

.empty-state {
  padding: 100px 0;
}

.result-scrollbar {
  max-height: 500px;
}

.result-card {
  margin: 16px 0;
  background: #f7fafc !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 12px !important;

  :deep(.n-card__footer) {
    background: #edf2f7 !important;
  }
}

.result-content {
  position: relative;
}

.content-text {
  color: #4a5568;
}

.highlight-text {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  color: #ffffff !important;
  padding: 2px 8px;
  margin: 0 4px;
  border-radius: 4px;
}

.score-tag {
  position: absolute;
  right: 0;
  top: 0;
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  color: #ffffff !important;
}

.source-text {
  color: #718096;
  font-size: 13px;
}
</style>
