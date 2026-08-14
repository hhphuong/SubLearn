import { computed, watch, type Ref } from 'vue'
import type { SubtitleLineDto } from '@/types/api'

/**
 * Binary search for the line covering `timeMs`, assuming `lines` is sorted by
 * `startMs` ascending (equivalently, by `sequence`). Returns the index of the
 * last line whose `startMs <= timeMs` — this doubles as "nearest preceding
 * line" when `timeMs` falls in a gap between two cues. Returns -1 if `lines`
 * is empty or `timeMs` is before the first line.
 */
export function findLineIndexForTime(lines: SubtitleLineDto[], timeMs: number): number {
  if (lines.length === 0) return -1

  let low = 0
  let high = lines.length - 1
  let result = -1

  while (low <= high) {
    const mid = Math.floor((low + high) / 2)
    const line = lines[mid]
    if (line === undefined) break
    if (line.startMs <= timeMs) {
      result = mid
      low = mid + 1
    } else {
      high = mid - 1
    }
  }

  return result
}

const NEAR_END_THRESHOLD = 0.9

export function useSubtitleSync(
  currentTimeMs: Ref<number>,
  lines: Ref<SubtitleLineDto[]>,
  onNearEnd: () => void,
) {
  const currentIndex = computed(() => findLineIndexForTime(lines.value, currentTimeMs.value))
  const currentLine = computed<SubtitleLineDto | null>(() => {
    const index = currentIndex.value
    return index >= 0 ? (lines.value[index] ?? null) : null
  })

  watch([currentIndex, () => lines.value.length], () => {
    const total = lines.value.length
    if (total === 0) return
    if (currentIndex.value >= Math.floor(total * NEAR_END_THRESHOLD)) {
      onNearEnd()
    }
  })

  return { currentIndex, currentLine }
}
