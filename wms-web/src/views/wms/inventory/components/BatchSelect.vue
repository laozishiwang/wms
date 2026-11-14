<script setup lang="ts">
import { getBatchInventoryList, type BatchInventoryVO } from '@/api/wms/inventory/batch'
import { listArrivalBalances } from './arrival-api'

const props = defineProps<{ warehouseId?: number; skuId?: number; modelValue?: string; locationId?: number; filterLocation?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | undefined]; change: [value: BatchInventoryVO | undefined] }>()
const options = ref<BatchInventoryVO[]>([])

const load = async (open: boolean) => {
  if (open && props.warehouseId && props.skuId) {
    options.value = await getBatchInventoryList(props.warehouseId, props.skuId)
    if (props.filterLocation) {
      const balances = await listArrivalBalances(props.warehouseId, props.skuId)
      const available = new Map<string, number>()
      balances.filter((item) => (item.locationId ?? undefined) === props.locationId && item.batchNo && Number(item.quantity) > 0).forEach((item) => available.set(item.batchNo!, (available.get(item.batchNo!) || 0) + Number(item.quantity)))
      options.value = options.value.filter((item) => available.has(item.batchNo)).map((item) => ({ ...item, quantity: Number(available.get(item.batchNo)!.toFixed(2)) }))
    }
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
