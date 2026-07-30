<script setup lang="ts"></script>

<template>
  <div class="not-found">
    <div class="not-found__icon">🔍</div>
    <h1 class="not-found__code">404</h1>
    <p class="text-muted mb-4">Không tìm thấy trang bạn yêu cầu.</p>
    <RouterLink class="btn btn-primary" to="/">Về trang chủ</RouterLink>
  </div>
</template>

<style scoped>
.not-found {
  text-align: center;
  padding: 4rem 1rem;
}

.not-found__icon {
  font-size: 3rem;
  margin-bottom: 0.5rem;
}

.not-found__code {
  font-size: 3rem;
  font-weight: 800;
  color: var(--brand-green);
  margin-bottom: 0.5rem;
}
</style>
