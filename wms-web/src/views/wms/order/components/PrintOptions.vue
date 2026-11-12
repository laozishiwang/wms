<script setup lang="ts">
interface PrintOptionsValue { barcode: boolean; remark: boolean; audit: boolean }
const props = defineProps<{ storageKey: string }>()
const emit = defineEmits<{ confirm: [value: PrintOptionsValue] }>()
const visible = ref(false)
const value = reactive<PrintOptionsValue>({ barcode: true, remark: true, audit: true })
const open = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(props.storageKey) || '{}')
    value.barcode = saved.barcode !== false
    value.remark = saved.remark !== false
    value.audit = saved.audit !== false
  } catch {
    Object.assign(value, { barcode: true, remark: true, audit: true })
  }
  visible.value = true
}
const confirm = () => {
  try { localStorage.setItem(props.storageKey, JSON.stringify(value)) } catch { /* 当前浏览器无法保存配置时仍可打印 */ }
  visible.value = false
  emit('confirm', { ...value })
}
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="打印内容设置" width="420px" append-to-body>
    <p>选择要显示的内容，设置保存在当前浏览器。</p>
    <div><el-checkbox v-model="value.barcode">单号条码</el-checkbox></div>
    <div><el-checkbox v-model="value.remark">备注</el-checkbox></div>
    <div><el-checkbox v-model="value.audit">创建和更新信息</el-checkbox></div>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="confirm">打印</el-button>
    </template>
  </el-dialog>
</template>
