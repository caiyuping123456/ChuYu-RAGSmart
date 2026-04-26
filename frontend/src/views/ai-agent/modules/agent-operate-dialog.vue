<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { fetchAgentDetail, fetchCreateAgent, fetchUpdateAgent } from '@/service/api/agent';

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

const mcpTypeOptions = [
  { label: '本地 MCP', value: 'local' },
  { label: '远程 MCP', value: 'remote' }
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
    customApiKey: '',
    mcpServices: []
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

watch(visible, async () => {
  if (visible.value) {
    if (props.operateType === 'edit' && props.rowData) {
      const { data } = await fetchAgentDetail(props.rowData.id);
      const detail = data || props.rowData;

      model.value = {
        name: detail.name,
        description: detail.description || '',
        systemPrompt: detail.systemPrompt,
        modelType: detail.modelType,
        modelName: detail.modelName,
        provider: detail.provider || 'openai',
        customApiUrl: detail.customApiUrl || '',
        customApiKey: detail.customApiKey || '',
        mcpServices: detail.mcpServices ? [...detail.mcpServices] : []
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

function addMcpService() {
  if (!model.value.mcpServices) model.value.mcpServices = [];
  model.value.mcpServices.push({
    name: '',
    type: 'remote',
    endpoint: '',
    apiKey: '',
    enabled: true
  });
}

function removeMcpService(index: number) {
  model.value.mcpServices?.splice(index, 1);
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
  <NModal v-model:show="visible" preset="dialog" :title="title" :show-icon="false" :mask-closable="false" style="width: 760px;">
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

      <NDivider style="margin: 12px 0;">MCP 服务</NDivider>

      <NSpace vertical :size="12">
        <NSpace justify="space-between" align="center">
          <div style="color: var(--n-text-color-3); font-size: 12px;">每个智能体可配置多个 MCP 服务（仅该智能体可见）。</div>
          <NButton size="small" @click="addMcpService">添加 MCP</NButton>
        </NSpace>

        <NCard v-for="(mcp, idx) in model.mcpServices" :key="idx" size="small" embedded>
          <NSpace vertical :size="10">
            <NSpace :size="12" align="center" justify="space-between">
              <div style="font-weight: 600;">MCP #{{ idx + 1 }}</div>
              <NSpace :size="8" align="center">
                <NCheckbox v-model:checked="mcp.enabled">启用</NCheckbox>
                <NButton size="tiny" tertiary type="error" @click="removeMcpService(idx)">删除</NButton>
              </NSpace>
            </NSpace>

            <NGrid :cols="24" :x-gap="12">
              <NFormItemGi :span="12" label="名称">
                <NInput v-model:value="mcp.name" placeholder="例如：本地文件系统 MCP" />
              </NFormItemGi>
              <NFormItemGi :span="12" label="类型">
                <NSelect v-model:value="mcp.type" :options="mcpTypeOptions" />
              </NFormItemGi>
              <NFormItemGi :span="16" label="Endpoint">
                <NInput v-model:value="mcp.endpoint" placeholder="例如：http://127.0.0.1:8000 或 https://mcp.example.com" />
              </NFormItemGi>
              <NFormItemGi :span="8" label="API Key">
                <NInput v-model:value="mcp.apiKey" type="password" show-password-on="click" placeholder="可选" />
              </NFormItemGi>
            </NGrid>
          </NSpace>
        </NCard>
      </NSpace>
    </NForm>
    <template #action>
      <NSpace :size="16">
        <NButton @click="close">取消</NButton>
        <NButton type="primary" :loading="loading" @click="handleSubmit">保存</NButton>
      </NSpace>
    </template>
  </NModal>
</template>
