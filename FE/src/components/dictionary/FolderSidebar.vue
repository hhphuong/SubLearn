<script setup lang="ts">
import { ref, watch } from 'vue'
import { useDictionaryStore } from '@/stores/dictionary.store'
import { toApiError } from '@/api/errors'
import AppModal from '@/components/common/AppModal.vue'

const props = defineProps<{ sourceLanguage: string }>()

const dictionaryStore = useDictionaryStore()
const isCreating = ref(false)
const newFolderName = ref('')
const createError = ref<string | null>(null)
const deleteFolderTarget = ref<{ id: number; name: string } | null>(null)

watch(
  () => props.sourceLanguage,
  (lang) => {
    dictionaryStore.folderIdFilter = null
    dictionaryStore.fetchFolders(lang)
  },
  { immediate: true },
)

function selectFolder(id: number | null) {
  dictionaryStore.folderIdFilter = id
  dictionaryStore.fetchList(true)
}

function startCreating() {
  isCreating.value = true
  createError.value = null
  newFolderName.value = ''
}

async function confirmCreate() {
  const name = newFolderName.value.trim()
  if (!name) return
  createError.value = null
  try {
    const folder = await dictionaryStore.createFolder(props.sourceLanguage, name)
    isCreating.value = false
    selectFolder(folder.id)
  } catch (error) {
    createError.value = toApiError(error).message
  }
}

function onDeleteFolder(id: number, name: string) {
  deleteFolderTarget.value = { id, name }
}

async function confirmDeleteFolder() {
  if (!deleteFolderTarget.value) return
  const id = deleteFolderTarget.value.id
  deleteFolderTarget.value = null
  await dictionaryStore.removeFolder(id)
  dictionaryStore.fetchList(true)
}
</script>

<template>
  <div class="folder-sidebar">
    <div v-if="!sourceLanguage" class="text-muted small py-2">Chọn một ngôn ngữ để xem/tạo folder.</div>
    <template v-else>
      <button
        type="button"
        class="folder-row"
        :class="{ 'folder-row--active': dictionaryStore.folderIdFilter === null }"
        @click="selectFolder(null)"
      >
        <span>📄 Tất cả</span>
      </button>

      <div v-if="dictionaryStore.foldersLoading" class="text-muted small py-2">Đang tải folder...</div>
      <button
        v-for="folder in dictionaryStore.folders"
        :key="folder.id"
        type="button"
        class="folder-row"
        :class="{ 'folder-row--active': dictionaryStore.folderIdFilter === folder.id }"
        @click="selectFolder(folder.id)"
      >
        <span class="folder-row__name">📁 {{ folder.name }}</span>
        <span class="folder-row__count">{{ folder.entryCount }}</span>
        <span class="folder-row__delete" title="Xóa folder" @click.stop="onDeleteFolder(folder.id, folder.name)">✕</span>
      </button>

      <div v-if="isCreating" class="d-flex gap-1 mt-2">
        <input
          v-model="newFolderName"
          type="text"
          class="form-control form-control-sm"
          placeholder="Tên folder mới..."
          autofocus
          @keyup.enter="confirmCreate"
        />
        <button class="btn btn-sm btn-primary" type="button" @click="confirmCreate">Tạo</button>
        <button class="btn btn-sm btn-outline-secondary" type="button" @click="isCreating = false">Hủy</button>
      </div>
      <button v-else type="button" class="btn btn-sm btn-outline-primary w-100 mt-2" @click="startCreating">
        + Tạo folder mới
      </button>
      <div v-if="createError" class="text-danger small mt-1">{{ createError }}</div>
    </template>

    <AppModal
      :show="deleteFolderTarget !== null"
      title="Cảnh báo xóa folder"
      :message="`Xóa folder &quot;${deleteFolderTarget?.name}&quot;?\nCác từ trong folder sẽ không bị xóa, chỉ chuyển về &quot;Chưa phân loại&quot;.`"
      type="confirm"
      variant="warning"
      confirm-text="Xóa folder"
      cancel-text="Hủy"
      @confirm="confirmDeleteFolder"
      @cancel="deleteFolderTarget = null"
      @close="deleteFolderTarget = null"
    />
  </div>
</template>

<style scoped>
.folder-sidebar {
  display: flex;
  flex-direction: column;
}

.folder-row {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 0.45rem 0.6rem;
  border-radius: var(--brand-radius-sm, 8px);
  font-size: 0.9rem;
  color: inherit;
}

.folder-row:hover {
  background: var(--brand-bg);
}

.folder-row--active {
  background: var(--brand-green-light);
  color: var(--brand-green-dark);
  font-weight: 600;
}

.folder-row__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.folder-row__count {
  font-size: 0.75rem;
  color: var(--brand-muted);
}

.folder-row__delete {
  font-size: 0.75rem;
  color: var(--brand-muted);
  opacity: 0;
  padding: 0 0.15rem;
}

.folder-row:hover .folder-row__delete {
  opacity: 1;
}

.folder-row__delete:hover {
  color: #dc3545;
}
</style>
