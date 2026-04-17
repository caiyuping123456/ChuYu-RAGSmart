<script setup lang="tsx">
import { NButton, NPopconfirm, NTag } from 'naive-ui';
import AgentOperateDialog from './modules/agent-operate-dialog.vue';
import AgentChat from './modules/agent-chat.vue';
import { fetchAgentList, fetchDeleteAgent } from '@/service/api/agent';

defineOptions({
  name: 'AiAgent'
});

const appStore = useAppStore();

const loading = ref(false);
const dialogVisible = ref(false);
const operateType = ref<'add' | 'edit'>('add');
const editingData = ref<Api.AiAgent.Item | null>(null);
const agentList = ref<Api.AiAgent.Item[]>([]);

// 智能体对话状态
const activeAgent = ref<Api.AiAgent.Item | null>(null);

const columns = [
  {
    key: 'index',
    title: '序号',
    width: 64,
    render: (_row: Api.AiAgent.Item, index: number) => index + 1
  },
  {
    key: 'name',
    title: '名称',
    minWidth: 120,
    ellipsis: { tooltip: true }
  },
  {
    key: 'description',
    title: '描述',
    minWidth: 180,
    ellipsis: { tooltip: true },
    render: (row: Api.AiAgent.Item) => row.description || '-'
  },
  {
    key: 'modelName',
    title: '模型',
    width: 200,
    render: (row: Api.AiAgent.Item) => (
      <NTag type={row.modelType === 'PRESET' ? 'success' : 'warning'} size="small">
        {row.modelName}
      </NTag>
    )
  },
  {
    key: 'createdAt',
    title: '创建时间',
    width: 180,
    render: (row: Api.AiAgent.Item) => dayjs(row.createdAt).format('YYYY-MM-DD HH:mm:ss')
  },
  {
    key: 'operate',
    title: '操作',
    width: 220,
    render: (row: Api.AiAgent.Item) => (
      <div class="flex gap-2">
        <NButton type="success" ghost size="small" onClick={() => handleUse(row)}>使用</NButton>
        <NButton type="primary" ghost size="small" onClick={() => handleEdit(row)}>编辑</NButton>
        <NPopconfirm onPositiveClick={() => handleDelete(row.id)}>
          {{
            default: () => '确认删除该智能体？',
            trigger: () => <NButton type="error" ghost size="small">删除</NButton>
          }}
        </NPopconfirm>
      </div>
    )
  }
];

function handleAdd() {
  operateType.value = 'add';
  editingData.value = null;
  dialogVisible.value = true;
}

function handleEdit(row: Api.AiAgent.Item) {
  operateType.value = 'edit';
  editingData.value = row;
  dialogVisible.value = true;
}

function handleUse(row: Api.AiAgent.Item) {
  activeAgent.value = row;
}

function handleBack() {
  activeAgent.value = null;
}

async function handleDelete(id: number) {
  const { error } = await fetchDeleteAgent(id);
  if (!error) {
    window.$message?.success('删除成功');
    getData();
  }
}

async function getData() {
  loading.value = true;
  const { data, error } = await fetchAgentList();
  if (!error && data) {
    agentList.value = data;
  }
  loading.value = false;
}

// 初始化加载数据
onMounted(() => {
  getData();
});
</script>

<template>
  <div class="ai-agent-container flex-col-stretch gap-16px overflow-hidden <sm:overflow-auto">
    <!-- 智能体对话界面 -->
    <AgentChat v-if="activeAgent" :agent="activeAgent" @back="handleBack" />

    <!-- 智能体管理列表 -->
    <NCard v-else title="智能体管理" :bordered="false" size="small" class="sm:flex-1-hidden">
      <template #header-extra>
        <TableHeaderOperation :loading="loading" @add="handleAdd" @refresh="getData" />
      </template>
      <NDataTable
        :columns="columns"
        :data="agentList"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="900"
        :loading="loading"
        :row-key="(row: Api.AiAgent.Item) => row.id"
        :pagination="false"
        class="data-table sm:h-full"
      />
      <AgentOperateDialog
        v-model:visible="dialogVisible"
        :operate-type="operateType"
        :row-data="editingData"
        @submitted="getData"
      />
    </NCard>
  </div>
</template>

<style scoped></style>
