<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useVideoHistoryStore } from '@/stores/videoHistory.store'
import { languageLabel } from '@/utils/languages'
import type { VideoHistoryEntryResponse } from '@/types/api'

const router = useRouter()
const historyStore = useVideoHistoryStore()

const totalPages = computed(() => Math.max(1, Math.ceil(historyStore.totalElements / historyStore.size)))

function thumbnailUrl(entry: VideoHistoryEntryResponse) {
  return `https://i.ytimg.com/vi/${entry.youtubeVideoId}/hqdefault.jpg`
}

function formatDuration(seconds: number | null) {
  if (!seconds && seconds !== 0) return null
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const pad = (n: number) => n.toString().padStart(2, '0')
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${m}:${pad(s)}`
}

function formatRelativeTime(iso: string) {
  const diffMs = Date.now() - new Date(iso).getTime()
  const minutes = Math.floor(diffMs / 60_000)
  if (minutes < 1) return 'Vừa xong'
  if (minutes < 60) return `${minutes} phút trước`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} giờ trước`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} ngày trước`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months} tháng trước`
  return `${Math.floor(months / 12)} năm trước`
}

function openVideo(entry: VideoHistoryEntryResponse) {
  router.push({ name: 'watch', params: { videoId: entry.videoId } })
}

function goToPage(newPage: number) {
  if (newPage < 0 || newPage >= totalPages.value) return
  historyStore.fetchPage(newPage)
}

onMounted(() => {
  historyStore.fetchPage(0)
})
</script>

<template>
  <section class="history-section">
    <h2 class="history-title">🕘 Xem gần đây</h2>

    <div v-if="historyStore.loading && historyStore.entries.length === 0" class="text-center py-4">
      <div class="spinner-border spinner-border-sm" role="status"></div>
    </div>
    <div v-else-if="historyStore.error" class="alert alert-danger py-2">{{ historyStore.error }}</div>
    <div v-else-if="historyStore.entries.length === 0" class="history-empty">
      <div class="history-empty__icon">🎬</div>
      <p class="text-muted mb-0">Chưa có video nào được xem gần đây.</p>
    </div>
    <template v-else>
      <div class="history-grid">
        <button
          v-for="entry in historyStore.entries"
          :key="entry.videoId"
          type="button"
          class="history-card"
          @click="openVideo(entry)"
        >
          <div class="history-card__thumb">
            <img :src="thumbnailUrl(entry)" :alt="entry.title ?? ''" loading="lazy" />
            <span v-if="formatDuration(entry.durationSeconds)" class="history-card__duration">
              {{ formatDuration(entry.durationSeconds) }}
            </span>
          </div>
          <div class="history-card__body">
            <div class="history-card__title">{{ entry.title ?? entry.youtubeVideoId }}</div>
            <div class="history-card__meta">
              <span v-if="entry.sourceLanguage" class="history-card__badge">{{ languageLabel(entry.sourceLanguage) }}</span>
              <span class="history-card__time">{{ formatRelativeTime(entry.lastWatchedAt) }}</span>
            </div>
          </div>
        </button>
      </div>

      <div v-if="totalPages > 1" class="history-pager">
        <button
          class="btn btn-sm btn-outline-primary"
          type="button"
          :disabled="historyStore.page === 0"
          @click="goToPage(historyStore.page - 1)"
        >
          ‹ Trước
        </button>
        <span class="history-pager__label">Trang {{ historyStore.page + 1 }}/{{ totalPages }}</span>
        <button
          class="btn btn-sm btn-outline-primary"
          type="button"
          :disabled="historyStore.page + 1 >= totalPages"
          @click="goToPage(historyStore.page + 1)"
        >
          Tiếp ›
        </button>
      </div>
    </template>
  </section>
</template>

<style scoped>
.history-section {
  width: 100%;
}

.history-title {
  font-size: 1.15rem;
  font-weight: 800;
  margin-bottom: 1rem;
}

.history-empty {
  background: #fff;
  border: 1px dashed var(--brand-border);
  border-radius: var(--brand-radius-md);
  padding: 2.5rem 1rem;
  text-align: center;
}

.history-empty__icon {
  font-size: 2.2rem;
  margin-bottom: 0.4rem;
}

.history-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1rem;
  max-height: 720px;
  overflow-y: auto;
  padding: 0.15rem;
}

.history-card {
  display: flex;
  flex-direction: column;
  text-align: left;
  background: #fff;
  border: 1px solid var(--brand-border);
  border-radius: var(--brand-radius-md);
  overflow: hidden;
  box-shadow: var(--brand-shadow);
  padding: 0;
}

.history-card:hover {
  border-color: var(--brand-green);
}

.history-card__thumb {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  background: var(--brand-bg);
}

.history-card__thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.history-card__duration {
  position: absolute;
  right: 0.4rem;
  bottom: 0.4rem;
  background: rgba(0, 0, 0, 0.75);
  color: #fff;
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.1rem 0.4rem;
  border-radius: 4px;
}

.history-card__body {
  padding: 0.65rem 0.75rem 0.8rem;
}

.history-card__title {
  font-weight: 700;
  font-size: 0.9rem;
  line-height: 1.3;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 0.4rem;
  min-height: 2.3em;
}

.history-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.4rem;
}

.history-card__badge {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
  font-size: 0.72rem;
  font-weight: 600;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
}

.history-card__time {
  font-size: 0.75rem;
  color: var(--brand-muted);
  white-space: nowrap;
}

.history-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  margin-top: 1rem;
}

.history-pager__label {
  font-size: 0.85rem;
  color: var(--brand-muted);
}
</style>
