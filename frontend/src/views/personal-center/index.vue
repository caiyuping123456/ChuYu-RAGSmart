<script setup lang="ts">
const { userInfo } = storeToRefs(useAuthStore());

const tags = ref<Api.OrgTag.Mine>({
  orgTags: [],
  primaryOrg: '',
  orgTagDetails: []
});

const loading = ref(false);
const getOrgTags = async () => {
  loading.value = true;
  const { error, data } = await request<Api.OrgTag.Mine>({
    url: '/users/org-tags'
  });
  if (!error) {
    tags.value = data;
  }
  loading.value = false;
};

onMounted(() => {
  getOrgTags();
});

const visible = ref(false);
const currentTagId = ref('');
const showModal = (tagId: string) => {
  if (tagId === tags.value.primaryOrg) return;
  visible.value = true;
  currentTagId.value = tagId;
};
const submitLoading = ref(false);
const setPrimaryOrg = async () => {
  submitLoading.value = true;
  const { error } = await request({
    url: '/users/primary-org',
    method: 'PUT',
    data: { primaryOrg: currentTagId.value, userId: userInfo.value.id }
  });
  if (!error) {
    visible.value = false;
    getOrgTags();
  }
  submitLoading.value = false;
};
</script>

<template>
  <NSpin :show="loading">
    <div class="personal-container">
      <NCard class="profile-card" :segmented="{ content: true, footer: 'soft' }">
        <template #header>
          <div class="profile-header">
            <NAvatar size="large" class="avatar">
              <icon-solar:user-circle-linear class="avatar-icon" />
            </NAvatar>
            <div class="username">{{ userInfo.username }}</div>
          </div>
        </template>
        <NScrollbar class="tag-scrollbar">
          <div class="tag-grid">
            <NCard
              v-for="tag in tags.orgTagDetails"
              :key="tag.tagId"
              size="small"
              embedded
              hoverable
              class="tag-item"
              :segmented="{ content: true, footer: 'soft' }"
              @click="showModal(tag.tagId)"
            >
              <div class="tag-content">
                <div class="tag-name">{{ tag.name }}</div>
                <NTag v-if="tag.tagId === tags.primaryOrg" type="primary" size="small" class="primary-tag">
                  主标签
                  <template #icon>
                    <icon-solar:verified-check-bold-duotone class="tag-icon" />
                  </template>
                </NTag>
              </div>
              <template #footer>
                <NEllipsis :line-clamp="3" class="tag-desc">{{ tag.description }}</NEllipsis>
              </template>
            </NCard>
          </div>
        </NScrollbar>
      </NCard>

      <NModal
        v-model:show="visible"
        :loading="submitLoading"
        preset="dialog"
        title="设置主标签"
        content="确定将当前标签设置为主标签吗？"
        positive-text="确认"
        negative-text="取消"
        class="confirm-modal"
        @positive-click="setPrimaryOrg"
        @negative-click="visible = false"
      />
    </div>
  </NSpin>
</template>

<style scoped lang="scss">
.personal-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100%;
  padding: 24px;
  position: relative;
  background: linear-gradient(135deg, #fafafa 0%, #f5f0ff 50%, #fff5f8 100%);
  border-radius: 24px;
}

/* 装饰性背景 */
.personal-container::before {
  content: '';
  position: absolute;
  top: -60px;
  right: -40px;
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.1) 0%, rgba(240, 147, 251, 0.1) 100%);
  border-radius: 50%;
  filter: blur(50px);
  pointer-events: none;
}

.profile-card {
  min-height: 400px;
  min-width: 600px;
  width: 50vw;
  background: rgba(255, 255, 255, 0.9) !important;
  backdrop-filter: blur(10px);
  border-radius: 24px !important;
  border: 1px solid rgba(255, 255, 255, 0.8) !important;
  box-shadow:
    0 8px 32px rgba(0, 0, 0, 0.06),
    0 0 0 1px rgba(245, 87, 108, 0.05) !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;

  :deep(.n-card-header) {
    background: linear-gradient(135deg, rgba(245, 87, 108, 0.04) 0%, rgba(240, 147, 251, 0.04) 100%) !important;
    border-bottom: 1px solid rgba(245, 87, 108, 0.08) !important;
    padding: 20px 24px !important;
  }
}

.profile-card:hover {
  box-shadow:
    0 12px 48px rgba(0, 0, 0, 0.1),
    0 0 0 1px rgba(245, 87, 108, 0.1) !important;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.avatar {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border-radius: 14px !important;
  box-shadow: 0 4px 12px rgba(245, 87, 108, 0.25) !important;
}

.avatar-icon {
  font-size: 32px;
  color: #ffffff;
}

.username {
  font-size: 20px;
  font-weight: 700;
  color: #2d3748;
  letter-spacing: 0.5px;
}

.tag-scrollbar {
  max-height: 60vh;
}

.tag-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 16px;
}

.tag-item {
  width: calc((100% - 32px) / 3);
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.9) 0%, rgba(248, 244, 255, 0.9) 100%) !important;
  border: 1px solid rgba(245, 87, 108, 0.08) !important;
  border-radius: 16px !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  cursor: pointer;
  overflow: hidden;
}

.tag-item:hover {
  border-color: rgba(245, 87, 108, 0.25) !important;
  box-shadow: 0 6px 20px rgba(245, 87, 108, 0.1) !important;
  transform: translateY(-3px);
}

.tag-item :deep(.n-card__footer) {
  background: linear-gradient(135deg, rgba(250, 250, 250, 0.8) 0%, rgba(248, 244, 255, 0.8) 100%) !important;
}

.tag-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.tag-name {
  font-size: 15px;
  color: #2d3748;
  font-weight: 600;
}

.primary-tag {
  background: linear-gradient(135deg, #f5576c 0%, #f093fb 100%) !important;
  border: none !important;
  border-radius: 8px !important;
}

.tag-icon {
  font-size: 14px;
}

.tag-desc {
  color: #718096;
  font-size: 13px;
}

.confirm-modal {
  :deep(.n-dialog) {
    background: rgba(255, 255, 255, 0.95) !important;
    backdrop-filter: blur(10px);
    border-radius: 16px !important;
    border: 1px solid rgba(245, 87, 108, 0.1) !important;
  }
}

:deep(.n-card__content) {
  flex: none !important;
  height: fit-content;
}
</style>
