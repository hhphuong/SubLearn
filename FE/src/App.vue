<script setup lang="ts">
import { RouterView } from 'vue-router'
import AppNavbar from '@/components/layout/AppNavbar.vue'
</script>

<template>
  <AppNavbar />
  <main class="app-main">
    <div class="app-main__inner">
      <RouterView />
    </div>
  </main>
</template>

<style scoped>
.app-main {
  min-height: calc(100vh - 60px);
}

.app-main__inner {
  max-width: 1140px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}
</style>
