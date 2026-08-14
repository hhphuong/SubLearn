<script setup lang="ts">
withDefaults(defineProps<{ size?: number }>(), { size: 32 })
</script>

<template>
  <svg :width="size" :height="size" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg" role="img" aria-label="SubLearn">
    <rect x="1" y="1" width="38" height="38" rx="11" fill="url(#sublearn-logo-gradient)" />
    <path d="M15.5 13.5L27 20L15.5 26.5V13.5Z" fill="#fff" />
    <rect x="10" y="28.5" width="9" height="3" rx="1.5" fill="#fff" fill-opacity="0.95" />
    <rect x="21" y="28.5" width="9" height="3" rx="1.5" fill="#fff" fill-opacity="0.6" />
    <defs>
      <linearGradient id="sublearn-logo-gradient" x1="1" y1="1" x2="39" y2="39" gradientUnits="userSpaceOnUse">
        <stop stop-color="#22c98a" />
        <stop offset="1" stop-color="#16965f" />
      </linearGradient>
    </defs>
  </svg>
</template>
