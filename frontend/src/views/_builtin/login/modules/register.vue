<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref } from 'vue';
import AgreementModal from './agreement-modal.vue';
import { fetchEmailCode, fetchRegister } from '@/service/api/auth';
import { $t } from '@/locales';

defineOptions({
  name: 'Register'
});

const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  username: string;
  password: string;
  confirmPassword: string;
  code: string;
}

const model: FormModel = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  code: ''
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  const { formRules, createConfirmPwdRule } = useFormRules();

  return {
    username: formRules.email,
    password: formRules.pwd,
    confirmPassword: createConfirmPwdRule(model.password),
    code: formRules.code
  };
});

const loading = ref(false);
const codeLoading = ref(false);

const agreementVisible = ref(false);
const agreementType = ref<'user' | 'privacy'>('user');

function openAgreement(type: 'user' | 'privacy') {
  agreementType.value = type;
  agreementVisible.value = true;
}
const countdown = ref(0);
let countdownTimer: ReturnType<typeof setInterval> | null = null;

const codeButtonLabel = computed(() => {
  if (countdown.value > 0) {
    return $t('page.login.codeLogin.reGetCode', { time: countdown.value });
  }

  return $t('page.login.codeLogin.getCode');
});

function clearCountdownTimer() {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown() {
  clearCountdownTimer();
  countdown.value = 60;
  countdownTimer = setInterval(() => {
    countdown.value -= 1;

    if (countdown.value <= 0) {
      countdown.value = 0;
      codeLoading.value = false;
      clearCountdownTimer();
    }
  }, 1000);
}

function isEmailValid(email: string) {
  if (email.trim() === '') {
    window.$message?.error?.($t('form.email.required'));
    return false;
  }

  const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
  if (!emailReg.test(email)) {
    window.$message?.error?.($t('form.email.invalid'));
    return false;
  }

  return true;
}

async function handleGetCode() {
  if (codeLoading.value || countdown.value > 0) {
    return;
  }

  if (!isEmailValid(model.username)) {
    return;
  }

  codeLoading.value = true;
  const { error } = await fetchEmailCode(model.username);
  if (!error) {
    window.$message?.success?.($t('page.login.codeLogin.sendCodeSuccess'));
    startCountdown();
  } else {
    codeLoading.value = false;
  }
}

async function handleSubmit() {
  await validate();
  loading.value = true;
  const { error } = await fetchRegister(model.username, model.password, model.code);
  if (!error) {
    window.$message?.success($t('page.login.common.registerSuccess') || '注册成功');
    toggleLoginModule('pwd-login');
  }
  loading.value = false;
}

onBeforeUnmount(() => {
  clearCountdownTimer();
});
</script>

<template>
  <NForm ref="formRef" :model="model" :rules="rules" size="large" :show-label="false" @keyup.enter="handleSubmit">
    <NFormItem path="username">
      <NInput v-model:value="model.username" :placeholder="$t('form.email.required')">
        <template #prefix>
          <icon-ant-design:mail-outlined />
        </template>
      </NInput>
    </NFormItem>
    <NFormItem path="password">
      <NInput
        v-model:value="model.password"
        type="password"
        show-password-on="click"
        :placeholder="$t('page.login.common.passwordPlaceholder')"
      >
        <template #prefix>
          <icon-ant-design:key-outlined />
        </template>
      </NInput>
    </NFormItem>
    <NFormItem path="confirmPassword">
      <NInput
        v-model:value="model.confirmPassword"
        type="password"
        show-password-on="click"
        :placeholder="$t('page.login.common.confirmPasswordPlaceholder')"
      >
        <template #prefix>
          <icon-ant-design:key-outlined />
        </template>
      </NInput>
    </NFormItem>
    <NFormItem path="code">
      <div class="code-row">
        <NInput v-model:value="model.code" :placeholder="$t('page.login.common.codePlaceholder')">
          <template #prefix>
            <icon-ant-design:security-scan-outlined />
          </template>
        </NInput>
        <NButton
          size="large"
          type="primary"
          class="code-btn"
          :loading="codeLoading || countdown > 0"
          @click="handleGetCode"
        >
          {{ codeButtonLabel }}
        </NButton>
      </div>
    </NFormItem>
    <NSpace vertical :size="18" class="w-full">
      <NButton type="primary" size="large" round block :loading="loading" @click="handleSubmit">
        {{ $t('page.login.common.register') }}
      </NButton>
      <NButton block @click="toggleLoginModule('pwd-login')">
        {{ $t('page.login.common.back') }}
      </NButton>
    </NSpace>

    <div class="mt-4 text-center">
      注册即代表已阅读并同意我们的
      <NButton text type="primary" @click="openAgreement('user')">用户协议</NButton>
      和
      <NButton text type="primary" @click="openAgreement('privacy')">隐私政策</NButton>
    </div>

    <AgreementModal v-model:visible="agreementVisible" :type="agreementType" />
  </NForm>
</template>

<style scoped>
.code-row {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.code-row :deep(.n-input) {
  flex: 1;
}

.code-btn {
  min-width: 116px;
  font-weight: 600;
  letter-spacing: 0.5px;
  white-space: nowrap;
}
</style>



