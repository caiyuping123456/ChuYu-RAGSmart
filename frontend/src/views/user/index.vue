<script setup lang="tsx">
import { NButton, NTag } from 'naive-ui';
import UserSearch from './modules/user-search.vue';
import OrgTagSettingDialog from './modules/org-tag-setting-dialog.vue';

const appStore = useAppStore();

function apiFn(params: Api.User.SearchParams) {
  return request<Api.User.List>({ url: '/admin/users/list', params });
}

const { columns, columnChecks, data, getData, loading, mobilePagination, searchParams, resetSearchParams } = useTable({
  apiFn,
  apiParams: {
    keyword: null,
    orgTag: null,
    status: null
  },
  columns: () => [
    {
      key: 'index',
      title: '序号',
      width: 64
    },
    {
      key: 'username',
      title: '用户名',
      minWidth: 100
    },
    {
      key: 'orgTags',
      title: '标签',
      render: row => (
        <div class="flex flex-wrap gap-2">
          {row.orgTags.map(tag => (
            <NTag key={tag.tagId} type={tag.tagId === row.primaryOrg ? 'primary' : 'default'}>
              {tag.name}
            </NTag>
          ))}
        </div>
      )
    },
    {
      key: 'email',
      title: '邮箱',
      width: 200
    },
    {
      key: 'status',
      title: '是否启用',
      width: 100,
      render: row => <NTag type={row.status ? 'success' : 'warning'}>{row.status ? '已启用' : '已禁用'}</NTag>
    },
    {
      key: 'createTime',
      title: '创建时间',
      width: 200,
      render: row => dayjs(row.createTime).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'lastLoginTime',
      title: '最后登录时间',
      width: 200,
      render: row => dayjs(row.lastLoginTime).format('YYYY-MM-DD HH:mm:ss')
    },
    {
      key: 'operate',
      title: '操作',
      width: 130,
      render: row => (
        <NButton type="primary" ghost size="small" onClick={() => handleOrgTag(row)}>
          分配组织标签
        </NButton>
      )
    }
  ]
});

const visible = ref(false);
const editingData = ref<Api.User.Item | null>(null);
function handleOrgTag(row: Api.User.Item) {
  editingData.value = row;
  visible.value = true;
}

// async function setPrimaryOrgTag(userId: string, primaryOrg: string) {
//   loading.value = true;
//   const { error } = await request({ url: 'users/primary-org', method: 'PUT', data: { primaryOrg, userId } });
//   if (!error) {
//     window.$message?.success('操作成功');
//     await getData();
//   }
//   loading.value = false;
// }
</script>

<template>
  <div class="user-container min-h-500px flex-col-stretch gap-16px overflow-hidden lt-sm:overflow-auto">
    <Teleport defer to="#header-extra">
      <UserSearch v-model:model="searchParams" @reset="resetSearchParams" @search="getData" />
    </Teleport>
    <NCard title="用户列表" :bordered="false" size="small" class="user-card sm:flex-1-hidden">
      <template #header-extra>
        <TableHeaderOperation v-model:columns="columnChecks" :addable="false" :loading="loading" @refresh="getData" />
      </template>
      <NDataTable
        :columns="columns"
        :data="data"
        size="small"
        :flex-height="!appStore.isMobile"
        :scroll-x="962"
        :loading="loading"
        remote
        :row-key="row => row.id"
        :pagination="mobilePagination"
        class="data-table sm:h-full"
      />
    </NCard>
    <OrgTagSettingDialog v-model:visible="visible" :row-data="editingData!" @submitted="getData" />
  </div>
</template>

<style scoped lang="scss">
.user-container {
  position: relative;
  background: linear-gradient(135deg, #fafafa 0%, #f0f7ff 50%, #fff5f8 100%);
  border-radius: 24px;
  padding: 24px;
}

/* 装饰性背景 */
.user-container::before {
  content: '';
  position: absolute;
  top: -40px;
  right: -40px;
  width: 180px;
  height: 180px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.12) 0%, rgba(118, 75, 162, 0.12) 100%);
  border-radius: 50%;
  filter: blur(35px);
  pointer-events: none;
}

.user-container::after {
  content: '';
  position: absolute;
  bottom: -50px;
  left: 20%;
  width: 250px;
  height: 100px;
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.08) 0%, rgba(240, 147, 251, 0.08) 100%);
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}

.user-card {
  position: relative;
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(10px);
  border-radius: 20px !important;
  border: 1px solid rgba(255, 255, 255, 0.8) !important;
  box-shadow:
    0 4px 24px rgba(0, 0, 0, 0.04),
    0 0 0 1px rgba(102, 126, 234, 0.05) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.user-card:hover {
  box-shadow:
    0 8px 40px rgba(0, 0, 0, 0.08),
    0 0 0 1px rgba(102, 126, 234, 0.1) !important;
  transform: translateY(-2px);
}

.user-card :deep(.n-card-header) {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.04) 0%, rgba(118, 75, 162, 0.04) 100%) !important;
  border-bottom: 1px solid rgba(102, 126, 234, 0.08) !important;
  padding: 16px 20px !important;
}

.user-card :deep(.n-card-header__title) {
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
  background: linear-gradient(135deg, #fafafa 0%, #f0f4ff 100%) !important;
  color: #4a5568 !important;
  font-weight: 600 !important;
  border-bottom: 2px solid rgba(102, 126, 234, 0.1) !important;
}

.data-table :deep(.n-data-table-td) {
  color: #4a5568 !important;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04) !important;
  transition: all 0.2s ease;
}

.data-table :deep(.n-data-table-tr:hover .n-data-table-td) {
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.04) 0%, rgba(118, 75, 162, 0.04) 100%) !important;
}
</style>
