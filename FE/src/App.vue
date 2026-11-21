<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import AppNavbar from '@/components/layout/AppNavbar.vue'
import { useAuthStore } from '@/stores/auth.store'

const route = useRoute()
const authStore = useAuthStore()
const isAdminRoute = computed(() => route.path.startsWith('/admin'))

onMounted(() => {
  if (authStore.isAuthenticated && !authStore.user?.role) {
    void authStore.fetchMe().catch(() => {})
  }
})
</script>

<template>
  <!-- Giao diện quản trị Admin riêng biệt, hoàn toàn độc lập với giao diện người dùng -->
  <RouterView v-if="isAdminRoute" />

  <!-- Giao diện người dùng tiêu chuẩn với AppNavbar và container nội dung -->
  <div v-else class="user-layout">
    <AppNavbar />
    <main class="app-main">
      <div class="app-main__inner">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.user-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-main {
  flex: 1;
}

.app-main__inner {
  max-width: 1140px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
  width: 100%;
}
</style>
