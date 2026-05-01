<script setup lang="ts">
import { computed } from 'vue';

defineOptions({
  name: 'AgreementModal'
});

type AgreementType = 'user' | 'privacy';

interface Props {
  type: AgreementType;
}

const props = defineProps<Props>();

const visible = defineModel<boolean>('visible');

const title = computed(() => (props.type === 'user' ? '用户协议' : '隐私政策'));

const sections = computed(() => {
  if (props.type === 'user') {
    return [
      {
        h: '1. 服务说明',
        p: '本服务为用户提供账号登录、内容管理与智能功能等服务。你在使用本服务前应仔细阅读并同意本协议。'
      },
      {
        h: '2. 账号与安全',
        p: '你应妥善保管账号信息并对账号下的所有行为负责。因你未妥善保管导致的损失由你自行承担。'
      },
      {
        h: '3. 使用规范',
        p: '你承诺不利用本服务从事违法违规活动，不发布或传播违法内容，不干扰服务正常运行。'
      },
      {
        h: '4. 知识产权',
        p: '本服务相关的产品、技术、界面与内容（除用户依法享有的内容外）均受法律保护。'
      },
      {
        h: '5. 免责声明',
        p: '本服务基于现状提供。因不可抗力、网络原因或第三方原因造成的服务中断，我们将在合理范围内协助处理。'
      },
      {
        h: '6. 协议变更',
        p: '我们可能会适时更新协议内容，更新后将以站内公告等方式提示。你继续使用即视为同意更新后的协议。'
      },
      {
        h: '7. 联系方式',
        p: '如对本协议有疑问，可通过站内联系方式与我们沟通。'
      }
    ];
  }

  return [
    {
      h: '1. 我们收集的信息',
      p: '为实现登录与服务功能，我们可能收集账号信息（如用户名/手机号/邮箱）、设备与日志信息（如IP、浏览器信息、访问时间）。'
    },
    {
      h: '2. 信息的使用',
      p: '我们使用你的信息用于身份验证、提供与改进服务、保障账号安全、统计分析与合规要求。'
    },
    {
      h: '3. 信息的共享与披露',
      p: '我们不会向无关第三方出售你的个人信息。仅在获得你的同意、法律法规要求或为提供服务必须时与合作方共享必要信息。'
    },
    {
      h: '4. 信息的存储与保护',
      p: '我们采取合理的安全措施保护信息安全，包括访问控制、加密传输与权限管理等。'
    },
    {
      h: '5. 你的权利',
      p: '你可以访问、更正、删除你的个人信息，或申请注销账号。部分操作可能受法律法规及安全要求限制。'
    },
    {
      h: '6. Cookie 与同类技术',
      p: '我们可能使用 Cookie 用于保持登录状态与改善体验。你可以在浏览器中管理或清除 Cookie。'
    },
    {
      h: '7. 政策更新',
      p: '我们可能适时更新隐私政策。更新后将以站内公告等方式提示。'
    }
  ];
});

function close() {
  visible.value = false;
}
</script>

<template>
  <NModal v-model:show="visible" preset="dialog" :title="title" :show-icon="false" :mask-closable="false" style="width: 720px;">
    <div class="agreement-wrap">
      <div class="agreement-hint">为保障你的权益，请认真阅读以下内容。</div>

      <div class="agreement-body">
        <div v-for="(s, i) in sections" :key="i" class="agreement-section">
          <div class="agreement-title">{{ s.h }}</div>
          <div class="agreement-text">{{ s.p }}</div>
        </div>
      </div>
    </div>

    <template #action>
      <NSpace :size="12">
        <NButton type="primary" @click="close">我已阅读并同意</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.agreement-wrap {
  padding: 6px 2px;
}

.agreement-hint {
  font-size: 12px;
  color: var(--n-text-color-3);
  margin-bottom: 10px;
}

.agreement-body {
  max-height: 52vh;
  overflow: auto;
  padding-right: 6px;
}

.agreement-section {
  padding: 12px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.08);
  margin-bottom: 10px;
}

.agreement-title {
  font-weight: 700;
  font-size: 14px;
  color: var(--n-text-color);
  margin-bottom: 6px;
}

.agreement-text {
  font-size: 13px;
  line-height: 1.8;
  color: var(--n-text-color-2);
}

.agreement-body::-webkit-scrollbar {
  width: 6px;
}

.agreement-body::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.15);
  border-radius: 6px;
}
</style>
