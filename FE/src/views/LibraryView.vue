<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { videoApi } from '@/api/video.api'
import { toApiError } from '@/api/errors'
import { DEFAULT_TARGET_LANGUAGE_CODE } from '@/utils/languages'
import type { VideoLibraryItemResponse } from '@/types/api'

const videos = ref<VideoLibraryItemResponse[]>([])
const totalElements = ref(0)
const isLoading = ref(true)
const loadError = ref<string | null>(null)

function formatDuration(seconds: number | null): string {
  if (seconds === null) return '--:--'
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = seconds % 60
  return hours > 0
    ? `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
    : `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`
}

function progressPercent(video: VideoLibraryItemResponse): number {
  if (!video.durationSeconds) return 0
  return Math.min(100, Math.round((video.lastPositionMs / (video.durationSeconds * 1000)) * 100))
}

function formatLastWatched(value: string): string {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

onMounted(async () => {
  try {
    const response = await videoApi.getLibrary()
    videos.value = response.videos
    totalElements.value = response.totalElements
  } catch (error) {
    loadError.value = toApiError(error).message
  } finally {
    isLoading.value = false
  }
})
</script>

<template>
  <section>
    <div class="library-heading">
      <div>
        <h1>Thư viện video</h1>
        <p class="text-muted mb-0">Tiếp tục học từ vị trí bạn đã dừng.</p>
      </div>
      <RouterLink class="btn btn-primary" to="/">Thêm video</RouterLink>
    </div>

    <div v-if="isLoading" class="text-center py-5">
      <div class="spinner-border text-primary" role="status"></div>
    </div>

    <div v-else-if="loadError" class="alert alert-danger">{{ loadError }}</div>

    <div v-else-if="videos.length === 0" class="empty-library">
      <div class="empty-library__icon" aria-hidden="true">🎬</div>
      <h2>Thư viện đang trống</h2>
      <p class="text-muted">Nhập một link YouTube để bắt đầu học và video sẽ tự động xuất hiện tại đây.</p>
      <RouterLink class="btn btn-primary" to="/">Học với video đầu tiên</RouterLink>
    </div>

    <div v-else>
      <p class="small text-muted">{{ totalElements }} video</p>
      <div class="video-grid">
        <article v-for="video in videos" :key="video.videoId" class="video-card">
          <RouterLink
            class="video-card__thumbnail"
            :to="{ name: 'watch', params: { videoId: video.videoId }, query: { lang: DEFAULT_TARGET_LANGUAGE_CODE } }"
          >
            <img
              :src="`https://i.ytimg.com/vi/${video.youtubeVideoId}/hqdefault.jpg`"
              :alt="video.title ?? 'YouTube video'"
              loading="lazy"
            />
            <span class="video-card__duration">{{ formatDuration(video.durationSeconds) }}</span>
          </RouterLink>

          <div class="video-card__body">
            <h2 class="video-card__title">{{ video.title || 'Video chưa có tiêu đề' }}</h2>
            <p class="video-card__meta">
              Xem gần nhất {{ formatLastWatched(video.lastWatchedAt) }}
            </p>
            <div class="video-progress" :aria-label="`Đã học ${progressPercent(video)}%`">
              <div class="video-progress__fill" :style="{ width: `${progressPercent(video)}%` }"></div>
            </div>
            <div class="video-card__footer">
              <span class="small text-muted">Đã học {{ progressPercent(video) }}%</span>
              <RouterLink
                class="btn btn-outline-primary btn-sm"
                :to="{ name: 'watch', params: { videoId: video.videoId }, query: { lang: DEFAULT_TARGET_LANGUAGE_CODE } }"
              >
                {{ video.lastPositionMs > 0 ? 'Tiếp tục học' : 'Bắt đầu học' }}
              </RouterLink>
            </div>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>

<style scoped>
.library-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

.library-heading h1 {
  font-size: 1.8rem;
  font-weight: 800;
  margin-bottom: 0.25rem;
}

.video-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.25rem;
}

.video-card {
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  box-shadow: var(--brand-shadow);
}

.video-card__thumbnail {
  position: relative;
  display: block;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: var(--brand-bg);
}

.video-card__thumbnail img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-card__duration {
  position: absolute;
  right: 0.5rem;
  bottom: 0.5rem;
  padding: 0.15rem 0.4rem;
  border-radius: 0.25rem;
  color: #fff;
  background: rgba(0, 0, 0, 0.78);
  font-size: 0.75rem;
  font-weight: 700;
}

.video-card__body {
  padding: 1rem;
}

.video-card__title {
  min-height: 2.7em;
  margin-bottom: 0.4rem;
  overflow: hidden;
  font-size: 1rem;
  font-weight: 750;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.video-card__meta {
  margin-bottom: 0.75rem;
  color: var(--brand-muted);
  font-size: 0.78rem;
}

.video-progress {
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--brand-green-light);
}

.video-progress__fill {
  height: 100%;
  border-radius: inherit;
  background: var(--brand-green);
}

.video-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: 0.75rem;
}

.empty-library {
  padding: 4rem 1.5rem;
  text-align: center;
  background: #fff;
  border: 1px dashed var(--brand-border);
  border-radius: var(--brand-radius-md);
}

.empty-library__icon {
  font-size: 3rem;
  margin-bottom: 0.75rem;
}

@media (max-width: 640px) {
  .library-heading {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
