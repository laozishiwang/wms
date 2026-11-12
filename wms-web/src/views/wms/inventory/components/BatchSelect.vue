<script setup lang="ts">
import { getBatchInventoryList, type BatchInventoryVO } from '@/api/wms/inventory/batch'

const props = defineProps<{ warehouseId?: number; skuId?: number; modelValue?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | undefined]; change: [value: BatchInventoryVO | undefined] }>()
const options = ref<BatchInventoryVO[]>([])

const load = async (open: boolean) => {
  if (open && props.warehouseId && props.skuId) {
    options.value = await getBatchInventoryList(props.warehouseId, props.skuId)
  }
}
const change = (value?: string) => {
  const selected = value || undefined
  emit('update:modelValue', selected)
  emit('change', options.value.find((item) => item.batchNo === selected))
}
</script>

<template>
  <el-select
    :model-value="modelValue"
    clearable
    placeholder="留空为无批次库存"
    @update:model-value="change"
    @visible-change="load"
  >
    <el-option v-for="item in options" :key="item.id" :label="`${item.batchNo}（${item.quantity}）`" :value="item.batchNo" />
  </el-select>
</template>
