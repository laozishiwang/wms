<script setup lang="ts">

import { listArrivalBalances, type ArrivalBalance } from './arrival-api'

const props = defineProps<{
  warehouseId?: number
  skuId?: number
  locationId?: number | null
  batchNo?: string | null
  modelValue?: number | null
  disabled?: boolean
  allowZeroUnassigned?: boolean
}>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const balances = ref<ArrivalBalance[]>([])
const loading = ref(false)
const loaded = ref(false)
const options = computed(() => balances.value.filter((item) =>
  (item.locationId ?? null) === (props.locationId ?? null) &&
  (item.batchNo || '') === (props.batchNo || '') && Number(item.quantity) > 0
))
const selected = computed(() => props.modelValue == null ? undefined : props.modelValue === 0 ? 'unassigned' : String(props.modelValue))
const label = (item: ArrivalBalance) => {
  if (item.arrivalId === null) return `历史未分配（ID：0） · 可用 ${item.quantity}`
  const details = [item.palletNo && `托盘 ${item.palletNo}`, item.boxNo && `箱 ${item.boxNo}`, item.rollNo && `卷 ${item.rollNo}`, item.netLength != null && `净长 ${item.netLength}cm`, item.grossLength != null && `毛长 ${item.grossLength}cm`, item.netWeight != null && `净重 ${item.netWeight}kg`, item.grossWeight != null && `毛重 ${item.grossWeight}kg`, item.length != null && `长 ${item.length}cm`, item.width != null && `宽 ${item.width}cm`, item.height != null && `高 ${item.height}cm`].filter(Boolean)
  return `ID：${item.arrivalId}${details.length ? ` · ${details.join(' / ')}` : ''} · 可用 ${item.quantity}`
}
const load = async (open: boolean) => {
  if (!open || !props.warehouseId || !props.skuId) return
  loading.value = true
  try { balances.value = await listArrivalBalances(props.warehouseId, props.skuId); loaded.value = true }
  finally { loading.value = false }
}
watch(() => [props.warehouseId, props.skuId, props.locationId, props.batchNo], () => { balances.value = []; loaded.value = false })
</script>

<template>
  <el-select :model-value="selected" :disabled="disabled || !warehouseId || !skuId" :loading="loading" clearable placeholder="选择到货记录" @visible-change="load" @update:model-value="emit('update:modelValue', $event === 'unassigned' ? 0 : $event ? Number($event) : undefined)">
    <el-option v-if="selected && !options.some((item) => (item.arrivalId === null ? 'unassigned' : String(item.arrivalId)) === selected) && !(allowZeroUnassigned && loaded && selected === 'unassigned')" :value="selected" :label="selected === 'unassigned' ? '历史未分配' : `到货 #${selected}`" disabled />
    <el-option v-for="item in options" :key="item.arrivalId ?? 'unassigned'" :value="item.arrivalId === null ? 'unassigned' : String(item.arrivalId)" :label="label(item)" />
    <el-option v-if="allowZeroUnassigned && loaded && !options.some((item) => item.arrivalId === null)" value="unassigned" label="历史未分配（ID：0，账面库存 0）" />
  </el-select>
</template>
