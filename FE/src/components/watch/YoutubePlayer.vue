<script setup lang="ts">
import { watch } from 'vue'
import { useYoutubePlayer } from '@/composables/useYoutubePlayer'

const props = defineProps<{ videoId: string }>()
const emit = defineEmits<{ ready: []; timeupdate: [ms: number] }>()

const { isReady, currentTimeMs, play, pause, seekTo } = useYoutubePlayer('yt-player', props.videoId)

watch(isReady, (ready) => {
  if (ready) emit('ready')
})

watch(currentTimeMs, (ms) => {
  emit('timeupdate', ms)
})

defineExpose({ play, pause, seekTo })
</script>

<template>
  <div class="ratio ratio-16x9">
    <div id="yt-player"></div>
  </div>
</template>
