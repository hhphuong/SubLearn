<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { videoApi } from '@/api/video.api'
import { useSubtitlesStore } from '@/stores/subtitles.store'
import { useSubtitleSync } from '@/composables/useSubtitleSync'
import YoutubePlayer from '@/components/watch/YoutubePlayer.vue'
import SubtitleLine from '@/components/watch/SubtitleLine.vue'
import WordPopup from '@/components/watch/WordPopup.vue'
import { toApiError } from '@/api/errors'
import type { SubtitleTokenDto } from '@/types/api'

const props = defineProps<{ videoId: string }>()
const route = useRoute()
const router = useRouter()
const subtitlesStore = useSubtitlesStore()

const numericVideoId = computed(() => Number(props.videoId))
const targetLanguage = computed(() => (typeof route.query.lang === 'string' ? route.query.lang : 'en'))

const youtubeVideoId = ref<string | null>(null)
const title = ref<string | null>(null)
const loadError = ref<string | null>(null)
const isLoading = ref(true)

const currentTimeMs = ref(0)
const linesRef = computed(() => subtitlesStore.lines)
const { currentLine } = useSubtitleSync(currentTimeMs, linesRef, () => subtitlesStore.fetchNextPage())

const playerRef = ref<InstanceType<typeof YoutubePlayer> | null>(null)
const activeToken = ref<SubtitleTokenDto | null>(null)

function onTimeUpdate(ms: number) {
  currentTimeMs.value = ms
}

function onTokenClick(token: SubtitleTokenDto) {
  playerRef.value?.pause()
  activeToken.value = token
}

function closePopup() {
  activeToken.value = null
}

onMounted(async () => {
  try {
    const summary = await videoApi.getSummary(numericVideoId.value)
    if (summary.status !== 'READY' || !summary.youtubeVideoId) {
      router.replace({ name: 'home' })
      return
    }
    youtubeVideoId.value = summary.youtubeVideoId
    title.value = summary.title
    await subtitlesStore.init(numericVideoId.value, targetLanguage.value)
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    isLoading.value = false
  }
})

onUnmounted(() => {
  subtitlesStore.reset()
})
</script>

<template>
  <div v-if="isLoading" class="text-center py-5">
    <div class="spinner-border text-primary" role="status"></div>
  </div>

  <div v-else-if="loadError" class="alert alert-danger">
    {{ loadError }}
    <RouterLink to="/">Về trang chủ</RouterLink>
  </div>

  <div v-else-if="youtubeVideoId">
    <h1 class="h4 mb-3">{{ title }}</h1>
    <div class="row g-4">
      <div class="col-12 col-lg-7">
        <YoutubePlayer ref="playerRef" :video-id="youtubeVideoId" @timeupdate="onTimeUpdate" />
      </div>
      <div class="col-12 col-lg-5">
        <div v-if="currentLine">
          <SubtitleLine :line="currentLine" @token-click="onTokenClick" />
        </div>
        <p v-else class="text-muted">Đang chờ phụ đề...</p>
      </div>
    </div>

    <WordPopup
      v-if="activeToken && currentLine"
      :word="activeToken.surfaceText"
      :sentence-context="currentLine.sourceText"
      :source-language="subtitlesStore.sourceLanguage ?? ''"
      :target-language="targetLanguage"
      :video-id="numericVideoId"
      :subtitle-line-id="currentLine.id"
      @close="closePopup"
    />
  </div>
</template>
