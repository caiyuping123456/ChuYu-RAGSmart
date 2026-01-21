<script setup lang="tsx">
import { NButton, NPopconfirm } from 'naive-ui';
import OrgTagOperateDialog from './modules/org-tag-operate-dialog.vue';

const appStore = useAppStore();

const { columns, columnChecks, data, loading, getData } = useTable({
  apiFn: fetchGetOrgTagList,
  columns: () => [
    {
      key: 'name',
      title: '标签名称',
      width: 300,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'description',
      title: '描述',
      minWidth: 200,
      ellipsis: {
        tooltip: true
      }
    },
    {
      key: 'operate',
      title: '操作',
      width: 240,
      render: row => (
        <div class="flex gap-2">
          <NButton type="success" ghost size="small" onClick={() => addChild(row)}>
            新增下级
          </NButton>
          <NButton type="primary" ghost size="small" onClick={() => edit(row)}>
            编辑
          </NButton>
          <NPopconfirm onPositiveClick={() => handleDelete(row.tagId!)}>
            {{
              default: () => '确认删除当前标签吗？',
              trigger: () => (
                <NButton type="error" ghost size="small">
                  删除
                </NButton>
              )
            }}
          </NPopconfirm>
        </div>
      )
    }
  ]
});

const {
  dialogVisible,
  operateType,
  editingData,
  handleAdd,
  handleAddChild,
  handleEdit,
  onDeleted
  // closeDrawer
} = useTableOperate<Api.OrgTag.Item>(getData);

function addChild(row: Api.OrgTag.Item) {
  handleAddChild(row);
}

/** the editing row data */
function edit(row: Api.OrgTag.Item) {
  handleEdit(row);
}

async function handleDelete(tagId: string) {
  const { error } = await request({ url: `/admin/org-tags/${tagId}`, method: 'DELETE' });
  if (!error) {
    onDeleted();
  }
}
</script>

<template>
  <div class="org-tag-container flex-col-stretch gap-16px overflow-hidden <sm:overflow-auto">
    <NCard title="组织标签" :bordered="false" size="small" class="tag-card sm:flex-1-hidden">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :loading="loading" @add="handleAdd" @refresh="getData" />
      </template>
      <NDataTable
        remote
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        :pagination="false"
        :row-key="item => item.tagId"
        class="data-table sm:h-full"
      />
      <OrgTagOperateDialog
        v-model:visible="dialogVisible"
        :operate-type="operateType"
        :row-data="editingData!"
        :data="data"
        @submitted="getData"
      />
    </NCard>
  </div>
</template>

<style scoped lang="scss">
.org-tag-container {
  position: relative;
  background: linear-gradient(135deg, #fafafa 0%, #f5fff0 50%, #fff8f0 100%);
  border-radius: 24px;
  padding: 24px;
}

/* 装饰性背景 */
.org-tag-container::before {
  content: '';
  position: absolute;
  top: -60px;
  left: 50%;
  transform: translateX(-50%);
  width: 300px;
  height: 120px;
  background: linear-gradient(135deg, rgba(72, 187, 120, 0.1) 0%, rgba(56, 161, 105, 0.1) 100%);
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}

.org-tag-container::after {
  content: '';
  position: absolute;
  bottom: -40px;
  right: -40px;
  width: 160px;
  height: 160px;
  background: linear-gradient(135deg, rgba(237, 137, 54, 0.1) 0%, rgba(221, 107, 32, 0.1) 100%);
  border-radius: 50%;
  filter: blur(35px);
  pointer-events: none;
}

.tag-card {
  position: relative;
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(10px);
  border-radius: 20px !important;
  border: 1px solid rgba(255, 255, 255, 0.8) !important;
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(72, 187, 120, 0.05) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.tag-card:hover {
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.08),
    0 0 0 1px rgba(72, 187, 120, 0.1) !important;
  transform: translateY(-2px);
}

.tag-card :deep(.n-card-header) {
  background: linear-gradient(135deg, rgba(72, 187, 120, 0.04) 0%, rgba(56, 161, 105, 0.04) 100%) !important;
  border-bottom: 1px solid rgba(72, 187, 120, 0.08) !important;
  padding: 16px 20px !important;
}

.tag-card :deep(.n-card-header__title) {
  color: #2d3748 !important;
  font-weight: 700 !important;
  font-size: 17px !important;
  letter-spacing: 0.5px;
}

.data-table {
  border-radius: 12px;
  overflow: hidden;
}

.data-table :deep(.n-data-table-wrapper) {
  border-radius: 12px;
}

.data-table :deep(.n-data-table-th) {
  background: linear-gradient(135deg, #fafafa 0%, #f0fff4 100%) !important;
  color: #4a5568 !important;
  font-weight: 600 !important;
  border-bottom: 2px solid rgba(72, 187, 120, 0.1) !important;
}

.data-table :deep(.n-data-table-td) {
  color: #4a5568 !important;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04) !important;
  transition: all 0.2s ease;
}

.data-table :deep(.n-data-table-tr:hover .n-data-table-td) {
  background: linear-gradient(135deg, rgba(72, 187, 120, 0.04) 0%, rgba(56, 161, 105, 0.04) 100%) !important;
}
</style>
