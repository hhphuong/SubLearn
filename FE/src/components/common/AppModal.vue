<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'

const props = withDefaults(
  defineProps<{
    show: boolean
    title?: string
    message: string
    type?: 'confirm' | 'alert'
    variant?: 'warning' | 'danger' | 'info'
    confirmText?: string
    cancelText?: string
    isProcessing?: boolean
  }>(),
  {
    title: 'Cảnh báo',
    type: 'alert',
    variant: 'warning',
    confirmText: 'Xác nhận',
    cancelText: 'Hủy',
    isProcessing: false,
  },
)

const emit = defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
  (e: 'close'): void
}>()

function onBackdropClick() {
  if (props.isProcessing) return
  if (props.type === 'confirm') {
    emit('cancel')
  } else {
    emit('close')
  }
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.show && !props.isProcessing) {
    if (props.type === 'confirm') {
      emit('cancel')
    } else {
      emit('close')
    }
  }
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Transition name="modal-fade">
    <div
      v-if="show"
      class="app-modal-backdrop"
      role="dialog"
      aria-modal="true"
      @click.self="onBackdropClick"
    >
      <div class="app-modal-card">
        <div class="app-modal-header">
          <div class="d-flex align-items-center gap-2">
            <div class="modal-icon-badge" :class="`modal-icon-badge--${variant}`">
              <svg
                v-if="variant === 'warning'"
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
                <line x1="12" y1="9" x2="12" y2="13" />
                <line x1="12" y1="17" x2="12.01" y2="17" />
              </svg>
              <svg
                v-else-if="variant === 'danger'"
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="15" y1="9" x2="9" y2="15" />
                <line x1="9" y1="9" x2="15" y2="15" />
              </svg>
              <svg
                v-else
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <circle cx="12" cy="12" r="10" />
                <line x1="12" y1="16" x2="12" y2="12" />
                <line x1="12" y1="8" x2="12.01" y2="8" />
              </svg>
            </div>
            <h3 class="app-modal-title mb-0">{{ title }}</h3>
          </div>
          <button
            type="button"
            class="btn-close"
            aria-label="Đóng"
            :disabled="isProcessing"
            @click="onBackdropClick"
          ></button>
        </div>

        <div class="app-modal-body">
          <p class="app-modal-message mb-0">{{ message }}</p>
        </div>

        <div class="app-modal-footer">
          <template v-if="type === 'confirm'">
            <button
              type="button"
              class="btn btn-sm btn-outline-secondary px-3"
              :disabled="isProcessing"
              @click="emit('cancel')"
            >
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="btn btn-sm btn-primary px-3 d-inline-flex align-items-center gap-2"
              :disabled="isProcessing"
              @click="emit('confirm')"
            >
              <span v-if="isProcessing" class="spinner-border spinner-border-sm" role="status"></span>
              <span>{{ confirmText }}</span>
            </button>
          </template>
          <template v-else>
            <button
              type="button"
              class="btn btn-sm btn-primary px-4"
              @click="emit('close')"
            >
              {{ confirmText || 'Đóng' }}
            </button>
          </template>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.app-modal-backdrop {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1100;
  padding: 1rem;
}

.app-modal-card {
  background: #ffffff;
  border-radius: var(--brand-radius-md, 14px);
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.16);
  border: 1px solid var(--brand-border, #e3ece8);
  width: min(440px, 94vw);
  overflow: hidden;
  animation: modal-pop 0.18s cubic-bezier(0.16, 1, 0.3, 1);
}

.app-modal-header {
  padding: 1rem 1.25rem 0.75rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #f1f5f9;
}

.app-modal-title {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--brand-ink, #1f2d3d);
}

.modal-icon-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex-shrink: 0;
}

.modal-icon-badge--warning {
  background: #fef3c7;
  color: #d97706;
}

.modal-icon-badge--danger {
  background: #fee2e2;
  color: #dc2626;
}

.modal-icon-badge--info {
  background: #e0f2fe;
  color: #0284c7;
}

.app-modal-body {
  padding: 1.25rem;
  color: #374151;
  font-size: 0.925rem;
  line-height: 1.5;
  white-space: pre-line;
}

.app-modal-footer {
  padding: 0.75rem 1.25rem 1rem;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.6rem;
  background: #fafbfc;
  border-top: 1px solid #f1f5f9;
}

.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.18s ease;
}

.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

@keyframes modal-pop {
  0% {
    transform: scale(0.95);
    opacity: 0;
  }
  100% {
    transform: scale(1);
    opacity: 1;
  }
}
</style>
