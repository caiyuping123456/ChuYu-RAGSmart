<script setup lang="ts">
import ChatList from './modules/chat-list.vue';
import InputBox from './modules/input-box.vue';
</script>

<template>
  <div class="chat-container">
    <ChatList />
    <InputBox />
  </div>
</template>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: 100%;
  position: relative;
  background: linear-gradient(135deg, #fafafa 0%, #f5f0ff 50%, #fff5f8 100%);
  padding: 24px;
  border-radius: 24px;
  overflow: hidden;
}

/* 装饰性背景光晕 */
.chat-container::before {
  content: '';
  position: absolute;
  top: -80px;
  right: -80px;
  width: 250px;
  height: 250px;
  background: linear-gradient(135deg, rgba(245, 87, 108, 0.08) 0%, rgba(240, 147, 251, 0.08) 100%);
  border-radius: 50%;
  filter: blur(50px);
  pointer-events: none;
}

.chat-container::after {
  content: '';
  position: absolute;
  bottom: -60px;
  left: -60px;
  width: 200px;
  height: 200px;
  background: linear-gradient(135deg, rgba(102, 126, 234, 0.08) 0%, rgba(118, 75, 162, 0.08) 100%);
  border-radius: 50%;
  filter: blur(40px);
  pointer-events: none;
}
</style>
