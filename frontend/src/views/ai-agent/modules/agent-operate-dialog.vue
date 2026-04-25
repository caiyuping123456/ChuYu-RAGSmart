<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { fetchCreateAgent, fetchUpdateAgent } from '@/service/api/agent';

defineOptions({
  name: 'AgentOperateDialog'
});

interface Props {
  operateType: 'add' | 'edit';
  rowData?: Api.AiAgent.Item | null;
}

const props = withDefaults(defineProps<Props>(), {
  operateType: 'add',
  rowData: null
});

const emit = defineEmits<{
  submitted: [];
}>();

const visible = defineModel<boolean>('visible');

const { formRef, validate, restoreValidation } = useNaiveForm();
const { defaultRequiredRule } = useFormRules();

const loading = ref(false);

// TODO 这里可以进行模型的设置
// 这个表示预设的一个模型
const presetModels = [
  { label: 'DeepSeek-V3.2', value: 'deepseek-ai/DeepSeek-V3.2' },
  { label: 'DeepSeek-V3.2 Pro', value: 'Pro/deepseek-ai/DeepSeek-V3.2' },
  { label: 'Qwen3.5', value: 'Qwen/Qwen3.5-397B-A17B' },
  { label: 'Qwen3.6', value: 'Qwen/Qwen3.6-35B-A3B' },
  { label: 'GLM-5', value: 'Pro/zai-org/GLM-5' },
  { label: 'Kimi-K2.6', value: 'Pro/moonshotai/Kimi-K2.6' }
];

// API 格式/提供商选项
const providerOptions = [
  { label: 'OpenAI 兼容格式', value: 'openai' },
  { label: 'Anthropic (Claude)', value: 'anthropic' },
  { label: 'Google (Gemini)', value: 'google' }
];

function createDefaultModel(): Api.AiAgent.Form {
  return {
    name: '',
    description: '',
    systemPrompt: '',
    modelType: 'PRESET',
    modelName: 'deepseek-ai/DeepSeek-V3.2',
    provider: 'openai',
    customApiUrl: '',
    customApiKey: ''
  };
}

const model = ref<Api.AiAgent.Form>(createDefaultModel());

const title = computed(() => {
  const titles: Record<string, string> = { add: '新建智能体', edit: '编辑智能体' };
  return titles[props.operateType];
});

const rules = computed(() => ({
  name: defaultRequiredRule,
  systemPrompt: defaultRequiredRule,
  modelType: defaultRequiredRule,
  modelName: defaultRequiredRule,
  customApiUrl: {
    required: model.value.modelType === 'CUSTOM',
    message: '请输入 API URL',
    trigger: 'blur'
  },
  customApiKey: {
    required: model.value.modelType === 'CUSTOM',
    message: '请输入 API Key',
    trigger: 'blur'
  },
  provider: {
    required: model.value.modelType === 'CUSTOM',
    message: '请选择 API 格式',
    trigger: 'change'
  }
}));

watch(visible, () => {
  if (visible.value) {
    if (props.operateType === 'edit' && props.rowData) {
      model.value = {
        name: props.rowData.name,
        description: props.rowData.description || '',
        systemPrompt: props.rowData.systemPrompt,
        modelType: props.rowData.modelType,
        modelName: props.rowData.modelName,
        provider: props.rowData.provider || 'openai',
        customApiUrl: props.rowData.customApiUrl || '',
        customApiKey: props.rowData.customApiKey || ''
      };
    } else {
      model.value = createDefaultModel();
    }
    restoreValidation();
  }
});

function close() {
  visible.value = false;
}

async function handleSubmit() {
  await validate();
  loading.value = true;

  const isEdit = props.operateType === 'edit';
  const { error } = isEdit
    ? await fetchUpdateAgent(props.rowData!.id, model.value)
    : await fetchCreateAgent(model.value);

  if (!error) {
    window.$message?.success(isEdit ? '更新成功' : '创建成功');
    close();
    emit('submitted');
  }

  loading.value = false;
}
</script>

<template>
  <NModal v-model:show="visible" preset="dialog" :title="title" :show-icon="false" :mask-closable="false" style="width: 600px;">
    <NForm ref="formRef" :model="model" :rules="rules" label-placement="left" :label-width="100">
      <NFormItem label="智能体名称" path="name">
        <NInput v-model:value="model.name" placeholder="请输入智能体名称" />
      </NFormItem>
      <NFormItem label="描述" path="description">
        <NInput v-model:value="model.description" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="描述智能体的功能（可选）" />
      </NFormItem>
      <NFormItem label="系统提示词" path="systemPrompt">
        <NInput v-model:value="model.systemPrompt" type="textarea" :autosize="{ minRows: 4, maxRows: 8 }" placeholder="定义智能体的角色和行为，例如：你是一个专业的数据分析助手..." />
      </NFormItem>
      <NFormItem label="模型类型" path="modelType">
        <NRadioGroup v-model:value="model.modelType">
          <NRadioButton value="PRESET">预设模型</NRadioButton>
          <NRadioButton value="CUSTOM">自定义模型</NRadioButton>
        </NRadioGroup>
      </NFormItem>
      <NFormItem v-if="model.modelType === 'CUSTOM'" label="API 格式" path="provider">
        <NSelect v-model:value="model.provider" :options="providerOptions" placeholder="请选择 API 格式" />
      </NFormItem>
      <NFormItem v-if="model.modelType === 'PRESET'" label="选择模型" path="modelName">
        <NSelect v-model:value="model.modelName" :options="presetModels" placeholder="请选择模型" />
      </NFormItem>
      <NFormItem v-if="model.modelType === 'CUSTOM'" label="模型名称" path="modelName">
        <NInput v-model:value="model.modelName" placeholder="例如：my-custom-model" />
      </NFormItem>
      <NFormItem v-if="model.modelType === 'CUSTOM'" label="API URL" path="customApiUrl">
        <NInput v-model:value="model.customApiUrl" placeholder="例如：https://api.example.com/v1" />
      </NFormItem>
      <NFormItem v-if="model.modelType === 'CUSTOM'" label="API Key" path="customApiKey">
        <NInput v-model:value="model.customApiKey" type="password" show-password-on="click" placeholder="请输入 API Key" />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
