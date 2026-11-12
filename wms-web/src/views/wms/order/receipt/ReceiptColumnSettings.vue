<script setup lang="ts">
const props = withDefaults(defineProps<{ modelValue: string[]; options: { key: string; label: string }[]; title?: string }>(), { title: '入库明细字段排序' })
const emit = defineEmits<{ 'update:modelValue': [value: string[]] }>()
const visible = ref(false)
const draft = ref<string[]>([])
const open = () => { draft.value = [...props.modelValue]; visible.value = true }
const move = (index: number, direction: number) => {
  const next = index + direction
  if (next < 0 || next >= draft.value.length) return
  ;[draft.value[index], draft.value[next]] = [draft.value[next], draft.value[index]]
}
const save = () => { emit('update:modelValue', [...draft.value]); visible.value = false }
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="title" width="min(460px, 94vw)" append-to-body>
    <p>使用上下按钮调整顺序。物品信息和操作列固定在两侧。</p>
    <div class="order-list">
    <div v-for="(key, index) in draft" :key="key" class="order-row">
      <span>{{ options.find((item) => item.key === key)?.label }}</span>
      <div>
        <el-button size="small" :disabled="index === 0" :aria-label="`上移${options.find((item) => item.key === key)?.label}`" @click="move(index, -1)">上移</el-button>
        <el-button size="small" :disabled="index === draft.length - 1" :aria-label="`下移${options.find((item) => item.key === key)?.label}`" @click="move(index, 1)">下移</el-button>
      </div>
    </div>
    </div>
    <template #footer>
      <el-button @click="draft = options.map((item) => item.key)">恢复默认</el-button>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.order-row { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 7px 0; border-bottom: 1px solid var(--el-border-color-lighter); }
.order-list { max-height: 55vh; overflow-y: auto; }
</style>
