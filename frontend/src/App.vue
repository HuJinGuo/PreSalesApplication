<template>
  <div class="app-shell">
    <router-view />
  </div>
</template>

<script setup lang="ts"></script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--page-bg);
}
</style>
