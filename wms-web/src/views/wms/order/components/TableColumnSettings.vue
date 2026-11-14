<script setup lang="ts">
import { getTenantId, getCurrentUserId } from '@/utils/auth'
type Column = { key: string; label: string; show: boolean }
const props = defineProps<{ options: Array<{ key: string; label: string }>; storageKey: string }>()
const columns = defineModel<Column[]>({ required: true })
const visible = ref(false), draft = ref<Column[]>([])
const key = () => `wms-columns:${getTenantId()}:${getCurrentUserId()}:${props.storageKey}`
const defaults = () => props.options.map(option => ({ ...option, show: true }))
const load = () => {
  const all = defaults()
  try {
    const saved = JSON.parse(localStorage.getItem(key()) || '[]') as Column[]
    const result = saved.flatMap(item => { const option = all.find(entry => entry.key === item.key); return option ? [{ ...option, show: item.show !== false }] : [] })
    columns.value = [...result.filter((item, index) => result.findIndex(entry => entry.key === item.key) === index), ...all.filter(option => !result.some(item => item.key === option.key))]
  } catch { columns.value = all }
}
const open = () => { draft.value = columns.value.map(item => ({ ...item })); visible.value = true }
const move = (index: number, delta: number) => { const next = index + delta; [draft.value[index], draft.value[next]] = [draft.value[next], draft.value[index]] }
const save = () => { columns.value = draft.value.map(item => ({ ...item })); localStorage.setItem(key(), JSON.stringify(columns.value)); visible.value = false }
onMounted(load)
</script>
<template>
  <el-button @click="open"><Icon class="mr-5px" icon="ep:setting" />表格设置</el-button>
  <el-dialog v-model="visible" title="表格设置" width="460px" append-to-body>
    <div v-for="(column, index) in draft" :key="column.key" class="column-setting"><el-checkbox v-model="column.show">{{ column.label }}</el-checkbox><span><el-button size="small" :disabled="index === 0" @click="move(index, -1)">上移</el-button><el-button size="small" :disabled="index === draft.length - 1" @click="move(index, 1)">下移</el-button></span></div>
    <template #footer><el-button @click="draft = defaults()">恢复默认</el-button><el-button type="primary" @click="save">保存</el-button></template>
  </el-dialog>
</template>
<style scoped>.column-setting { display:flex; justify-content:space-between; padding:4px 0; }</style>
