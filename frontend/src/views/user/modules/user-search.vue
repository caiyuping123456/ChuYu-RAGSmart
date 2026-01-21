<script setup lang="ts">
import { enableStatusOptions } from '@/constants/common';

defineOptions({
  name: 'UserSearch'
});

const emit = defineEmits<{
  search: [];
}>();

const { formRef } = useNaiveForm();

const model = defineModel<Api.User.SearchParams>('model', { required: true });

watchEffect(() => {
  search();
});
async function search() {
  emit('search');
}
</script>

<template>
  <NCard :bordered="false" size="small" class="search-card">
    <NForm ref="formRef" :model="model" label-placement="left" :show-feedback="false" inline>
      <NFormItem label="关键词" path="keyword">
        <NInput v-model:value="model.keyword" placeholder="请输入关键词" clearable />
      </NFormItem>
      <NFormItem label="组织标签" path="userGender">
        <OrgTagCascader v-model:value="model.orgTag" clearable class="org-tag-select" />
      </NFormItem>
      <NFormItem label="启用状态" path="status">
        <NSelect
          v-model:value="model.status"
          placeholder="请选择启用状态"
          :options="enableStatusOptions"
          clearable
          class="status-select"
        />
      </NFormItem>
    </NForm>
  </NCard>
</template>

<style scoped lang="scss">
.search-card {
  background: #ffffff !important;
  border-radius: 12px !important;
  border: 1px solid #e2e8f0 !important;
  padding: 0 24px !important;

  :deep(.n-card__content) {
    padding: 12px 0 !important;
  }
}

.org-tag-select {
  min-width: 200px;
}

.status-select {
  min-width: 200px;

  :deep(.n-base-selection) {
    background: #ffffff !important;
    border: 1px solid #e2e8f0 !important;
    border-radius: 8px !important;
  }

  :deep(.n-base-selection:hover) {
    border-color: #f5576c !important;
  }
}
</style>
