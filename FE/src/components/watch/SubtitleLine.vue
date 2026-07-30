<script setup lang="ts">
import { computed } from 'vue'
import type { SubtitleLineDto, SubtitleTokenDto } from '@/types/api'

const props = defineProps<{ line: SubtitleLineDto }>()
const emit = defineEmits<{ tokenClick: [token: SubtitleTokenDto] }>()

interface Segment {
  text: string
  token: SubtitleTokenDto | null
}

const segments = computed<Segment[]>(() => {
  const { sourceText, tokens } = props.line
  const result: Segment[] = []
  let cursor = 0
  for (const token of tokens) {
    if (token.startCharOffset > cursor) {
      result.push({ text: sourceText.slice(cursor, token.startCharOffset), token: null })
    }
    result.push({ text: sourceText.slice(token.startCharOffset, token.endCharOffset), token })
    cursor = token.endCharOffset
  }
  if (cursor < sourceText.length) {
    result.push({ text: sourceText.slice(cursor), token: null })
  }
  return result
})
</script>

<template>
  <div class="subtitle-line">
    <p class="source-text mb-1 fs-5">
      <template v-for="(segment, index) in segments" :key="index">
        <span
          v-if="segment.token"
          class="token"
          :class="segment.token.tokenType === 'PHRASE' ? 'token-phrase' : 'token-word'"
          @click="emit('tokenClick', segment.token)"
          >{{ segment.text }}</span
        >
        <span v-else>{{ segment.text }}</span>
      </template>
    </p>
    <p class="target-text text-muted mb-0">{{ line.targetText }}</p>
  </div>
</template>

<style scoped>
.token {
  cursor: pointer;
  border-bottom: 2px solid transparent;
}
.token-word {
  border-bottom-color: var(--brand-green);
}
.token-phrase {
  border-bottom-color: #f5a623;
  border-bottom-style: dashed;
}
.token:hover {
  background-color: var(--brand-green-light);
  border-radius: 4px;
}
</style>
