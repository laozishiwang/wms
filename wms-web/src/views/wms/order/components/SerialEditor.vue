<script setup lang="ts">
import { ElMessage } from 'element-plus'

const model = defineModel<string[] | null>({ default: () => [] })
const visible = ref(false)
const text = ref('')
const scan = ref('')
const lines = computed(() => text.value.split(/\r?\n/).map((value) => value.trim()).filter(Boolean))
const open = () => { text.value = (model.value || []).join('\n'); scan.value = ''; visible.value = true }
const add = () => {
  const value = scan.value.trim()
  if (!value) return
  if (lines.value.includes(value)) { ElMessage.warning('序列号已录入'); return }
  text.value = [...lines.value, value].join('\n')
  scan.value = ''
}
const save = () => {
  if (new Set(lines.value).size !== lines.value.length) { ElMessage.warning('序列号不能重复'); return }
  model.value = lines.value
  visible.value = false
}
</script>

<template>
  <el-button link type="primary" @click="open">序列号 ({{ model?.length || 0 }})</el-button>
  <el-dialog v-model="visible" title="录入序列号" width="min(520px, 94vw)" append-to-body>
    <el-input v-model="scan" placeholder="扫描或输入序列号后按回车" @keyup.enter="add" />
    <el-input v-model="text" class="mt-12px" type="textarea" :rows="8" placeholder="每行一个序列号" />
    <p>已录入 {{ lines.length }} 个</p>
    <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" @click="save">确定</el-button></template>
  </el-dialog>
</template>
