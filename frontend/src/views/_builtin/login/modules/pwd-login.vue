<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import { useFormRules, useNaiveForm } from '@/hooks/common/form';
import { $t } from '@/locales';
import AgreementModal from './agreement-modal.vue';

defineOptions({
  name: 'PwdLogin'
});

const authStore = useAuthStore();
const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  userName: string;
  password: string;
}

const model: FormModel = reactive({
  userName: '',
  password: ''
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  // inside computed to make locale reactive, if not apply i18n, you can define it without computed
  const { formRules, createRequiredRule } = useFormRules();

  return {
    userName: [createRequiredRule($t('form.userName.required'))],
    password: formRules.pwd
  };
});

async function handleSubmit() {
  await validate();
  await authStore.login(model.userName, model.password);
}

type AccountKey = 'admin' | 'user';

interface Account {
  key: AccountKey;
  label: string;
  userName: string;
  password: string;
}

const accounts = computed<Account[]>(() => [
  {
    key: 'admin',
    label: $t('page.login.pwdLogin.admin'),
    userName: 'admin',
    password: 'admin123'
  },
  {
    key: 'user',
    label: $t('page.login.pwdLogin.user'),
    userName: 'testuser',
    password: 'test123'
  }
]);

function handleAccountLogin(account: Account) {
  // 将账号信息填充到表单中，然后触发正常的验证流程
  model.userName = account.userName;
  model.password = account.password;

  // 调用正常的表单提交流程，确保验证
  handleSubmit();
}

const agreementVisible = ref(false);
const agreementType = ref<'user' | 'privacy'>('user');

function openAgreement(type: 'user' | 'privacy') {
  agreementType.value = type;
  agreementVisible.value = true;
}
</script>

<template>
  <NForm ref="formRef" :model="model" :rules="rules" size="large" :show-label="false" @keyup.enter="handleSubmit">
    <NFormItem path="userName" class="form-item-wrapper">
      <div class="input-wrapper">
        <NInput v-model:value="model.userName" :placeholder="$t('page.login.common.userNamePlaceholder')" class="styled-input">
          <template #prefix>
            <icon-ant-design:user-outlined class="input-icon" />
          </template>
        </NInput>
      </div>
    </NFormItem>
    <NFormItem path="password" class="form-item-wrapper">
      <div class="input-wrapper">
        <NInput
          v-model:value="model.password"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.passwordPlaceholder')"
          class="styled-input"
        >
          <template #prefix>
            <icon-ant-design:key-outlined class="input-icon" />
          </template>
        </NInput>
      </div>
    </NFormItem>
    <div class="actions-wrapper">
      <NButton type="primary" size="large" round block :loading="authStore.loginLoading" class="login-btn" @click="handleSubmit">
        <span class="btn-text">{{ $t('page.login.common.login') }}</span>
      </NButton>
      <NButton block class="register-btn" @click="toggleLoginModule('register')">
        {{ $t(loginModuleRecord.register) }}
      </NButton>

      <p class="agreement-text">
        登录即代表已阅读并同意我们的
        <NButton text type="primary" class="agreement-link" @click="openAgreement('user')">用户协议</NButton>
        和
        <NButton text type="primary" class="agreement-link" @click="openAgreement('privacy')">隐私政策</NButton>
      </p>
    </div>

    <AgreementModal v-model:visible="agreementVisible" :type="agreementType" />
  </NForm>
</template>

<style scoped>
.form-item-wrapper {
  margin-bottom: 20px;
}

.input-wrapper {
  position: relative;
  width: 100%;
}

/* Input styling */
.styled-input {
  --n-text-color: #1a1a2e !important;
  --n-placeholder-color: rgba(26, 26, 46, 0.5) !important;
  --n-caret-color: #e94560 !important;
}

.styled-input :deep(.n-input__input-el) {
  color: #1a1a2e !important;
  caret-color: #e94560;
}

.styled-input :deep(.n-input__placeholder) {
  color: rgba(26, 26, 46, 0.5) !important;
}

.styled-input :deep(.n-input-wrapper) {
  background: rgba(255, 255, 255, 0.85) !important;
  border: 1px solid rgba(255, 255, 255, 0.3) !important;
  border-radius: 14px !important;
  padding: 8px 16px !important;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
}

.styled-input :deep(.n-input:hover .n-input-wrapper) {
  background: rgba(255, 255, 255, 0.95) !important;
  border-color: rgba(233, 69, 96, 0.4) !important;
  box-shadow: 0 8px 25px -5px rgba(233, 69, 96, 0.2);
}

.styled-input :deep(.n-input:focus-within .n-input-wrapper) {
  background: rgba(255, 255, 255, 1) !important;
  border-color: #e94560 !important;
  box-shadow:
    0 8px 30px -5px rgba(233, 69, 96, 0.3),
    0 0 0 4px rgba(233, 69, 96, 0.1);
}

.input-icon {
  font-size: 18px !important;
  color: #1a1a2e !important;
  opacity: 0.6;
  transition: all 0.3s ease;
}

.styled-input :deep(.n-input:focus-within) .input-icon {
  opacity: 1;
  color: #e94560 !important;
}

/* Password visibility icon */
.styled-input :deep(.n-input__eye),
.styled-input :deep(.n-input__eye-icon) {
  color: #1a1a2e !important;
  opacity: 0.6;
}

.styled-input :deep(.n-input__eye:hover),
.styled-input :deep(.n-input__eye-icon:hover) {
  color: #e94560 !important;
  opacity: 1;
}

/* Actions wrapper */
.actions-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 8px;
}

/* Login button */
.login-btn {
  position: relative;
  height: 52px !important;
  font-size: 16px !important;
  font-weight: 600 !important;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #e94560 0%, #ff6b9d 50%, #00d9ff 100%) !important;
  background-size: 200% 200% !important;
  border: none !important;
  overflow: hidden;
  transition: all 0.4s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow:
    0 10px 30px -10px rgba(233, 69, 96, 0.5),
    0 4px 15px -3px rgba(0, 217, 255, 0.3);
}

.login-btn::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, #ff6b9d 0%, #00d9ff 50%, #e94560 100%);
  background-size: 200% 200%;
  opacity: 0;
  transition: opacity 0.4s ease;
}

.login-btn:hover {
  transform: translateY(-3px);
  box-shadow:
    0 15px 40px -10px rgba(233, 69, 96, 0.6),
    0 8px 25px -5px rgba(0, 217, 255, 0.4),
    0 0 50px rgba(233, 69, 96, 0.3);
  animation: btnGradient 3s ease infinite;
}

.login-btn:hover::before {
  opacity: 1;
  animation: btnGradient 3s ease infinite;
}

@keyframes btnGradient {
  0%, 100% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
}

.btn-text {
  position: relative;
  z-index: 1;
}

/* Loading state */
:deep(.login-btn.n-button--loading) {
  background: linear-gradient(135deg, #e94560 0%, #ff6b9d 100%) !important;
}

/* Register button */
.register-btn {
  height: 48px !important;
  font-size: 15px !important;
  background: rgba(255, 255, 255, 0.05) !important;
  border: 1px solid rgba(255, 255, 255, 0.15) !important;
  border-radius: 14px !important;
  color: rgba(255, 255, 255, 0.7) !important;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.register-btn:hover {
  background: rgba(255, 255, 255, 0.1) !important;
  border-color: rgba(255, 255, 255, 0.25) !important;
  color: rgba(255, 255, 255, 0.9) !important;
  transform: translateY(-2px);
}

/* Agreement text */
.agreement-text {
  text-align: center;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  line-height: 1.8;
  margin-top: 8px;
}

.agreement-link {
  color: #e94560 !important;
  font-size: 13px !important;
  padding: 0 2px !important;
  transition: all 0.3s ease;
}

.agreement-link:hover {
  color: #00d9ff !important;
  text-shadow: 0 0 10px rgba(0, 217, 255, 0.5);
}

/* Dark mode compatibility */
:global(.dark) .styled-input :deep(.n-input-wrapper) {
  background: rgba(0, 0, 0, 0.2) !important;
}
</style>
