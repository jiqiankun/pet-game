<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { apiGet, apiPost } from '../../api/client'

const backups = ref<string[]>([])
const message = ref('')
const error = ref('')
const busy = ref(false)
const importRef = ref<HTMLInputElement | null>(null)

/** 导出当前存档（下载 .pet-save.zip）。 */
function exportSave() {
  // 使用浏览器直接下载（后端返回二进制流，不走 JSON 拦截器）
  window.location.href = '/api/save/export'
}

/** 导入存档。 */
async function importSave() {
  const file = importRef.value?.files?.[0]
  if (!file) {
    error.value = '请先选择 .pet-save.zip 存档文件'
    return
  }
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    const form = new FormData()
    form.append('file', file)
    const res = await fetch('/api/save/import', { method: 'POST', body: form })
    const json = await res.json()
    if (!json.success) {
      error.value = json.message ?? '导入失败'
      return
    }
    message.value = '存档导入成功，当前存档已自动备份。'
    importRef.value!.value = ''
  } catch (e: any) {
    error.value = e.message ?? '导入失败'
  } finally {
    busy.value = false
  }
}

/** 手动备份当前存档。 */
async function manualBackup() {
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    const res = await apiPost<{ status: string; file: string }>('/api/save/backup')
    message.value = `手动备份成功：${res.data.file}`
    await loadBackups()
  } catch (e: any) {
    error.value = e.message ?? '备份失败'
  } finally {
    busy.value = false
  }
}

/** 加载备份列表。 */
async function loadBackups() {
  try {
    const res = await apiGet<string[]>('/api/save/backups')
    backups.value = res.data || []
  } catch (e: any) {
    error.value = e.message ?? '加载备份列表失败'
  }
}

/** 重置游戏（先自动备份再清空）。 */
async function resetGame() {
  if (!window.confirm('重置将清除全部存档进度（会自动备份当前存档）。确认继续？')) {
    return
  }
  busy.value = true
  message.value = ''
  error.value = ''
  try {
    await apiPost('/api/save/reset')
    message.value = '游戏已重置，当前存档已自动备份。'
    await loadBackups()
  } catch (e: any) {
    error.value = e.message ?? '重置失败'
  } finally {
    busy.value = false
  }
}

onMounted(loadBackups)
</script>

<template>
  <div class="save-page">
    <h2>存档备份</h2>

    <section class="save-card">
      <h3>导出 / 导入</h3>
      <p>将当前存档导出为 <code>.pet-save.zip</code> 文件；导入会先自动备份当前存档，失败自动回滚。</p>
      <div class="row">
        <button class="btn" :disabled="busy" @click="exportSave">导出存档</button>
        <button class="btn" :disabled="busy" @click="importRef?.click()">导入存档</button>
        <input ref="importRef" type="file" accept=".zip,.pet-save.zip" hidden @change="importSave" />
      </div>
    </section>

    <section class="save-card">
      <h3>手动备份</h3>
      <p>将当前存档写入服务端备份目录（<code>game.backup-dir</code>），用于高风险操作前的快照。</p>
      <div class="row">
        <button class="btn" :disabled="busy" @click="manualBackup">立即备份</button>
      </div>
    </section>

    <section class="save-card">
      <h3>备份列表</h3>
      <p v-if="backups.length === 0" class="empty">暂无备份文件。</p>
      <ul v-else class="backup-list">
        <li v-for="name in backups" :key="name" class="backup-item">{{ name }}</li>
      </ul>
    </section>

    <section class="save-card danger">
      <h3>重置游戏</h3>
      <p>清空全部存档进度，重置前自动备份当前存档。此操作不可撤销。</p>
      <div class="row">
        <button class="btn danger-btn" :disabled="busy" @click="resetGame">重置游戏</button>
      </div>
    </section>

    <p v-if="message" class="message">{{ message }}</p>
    <p v-if="error" class="error">{{ error }}</p>
  </div>
</template>

<style scoped>
.save-page { padding: 24px; max-width: 640px; }
.save-page h2 { margin: 0 0 16px; color: var(--color-primary); font-size: 20px; }
.save-card { padding: 20px; margin-bottom: 16px; border-radius: var(--radius-md); background: var(--bg-card); box-shadow: var(--shadow-1); }
.save-card h3 { margin: 0 0 8px; font-size: 16px; }
.save-card p { color: var(--text-secondary); line-height: 1.6; }
.save-card code { background: rgba(0,0,0,.05); padding: 1px 5px; border-radius: 4px; }
.row { display: flex; gap: 12px; margin-top: 12px; flex-wrap: wrap; }
.btn { padding: 8px 16px; border: 0; border-radius: 6px; background: var(--color-primary); color: #fff; cursor: pointer; }
.btn:disabled { cursor: not-allowed; opacity: .6; }
.danger-btn { background: #c43d3d; }
.backup-list { margin: 8px 0 0; padding: 0; list-style: none; }
.backup-item { padding: 8px 10px; border-bottom: 1px solid #eee; font-size: 13px; font-family: monospace; word-break: break-all; }
.empty { color: var(--text-secondary); }
.message { color: #20864b !important; }
.error { color: #c43d3d !important; }

@media (max-width: 768px) {
  .save-page { padding: 12px; }
  .row { flex-direction: column; }
  .row .btn { width: 100%; }
}
</style>