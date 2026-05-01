<script setup lang="ts">
defineOptions({
  name: 'GlobalFooter'
});
</script>

<template>
  <DarkModeContainer class="h-full flex-center" style="flex-direction: column; gap: 6px;">
    <a href="https://github.com/itwanger/PaiSmart.git" target="_blank" rel="noopener noreferrer">
      Copyright MIT © 2025 PaiSmart
    </a>
    <footer style="text-align: center; padding: 20px; font-size: 14px; color: #666;">
      <a
        href="https://beian.miit.gov.cn/"
        target="_blank"
        rel="noopener noreferrer"
        style="color: #666; text-decoration: none;"
      >
        赣ICP备2026008639号
      </a>
    </footer>
  </DarkModeContainer>
</template>

<style scoped></style>
