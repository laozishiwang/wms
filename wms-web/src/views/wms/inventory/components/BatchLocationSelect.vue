<script setup lang="ts">
import request from '@/config/axios'

interface LocationBalance { locationId: number | null; code: string | null; name: string | null; quantity: number }
const props = defineProps<{ warehouseId?: number; skuId?: number; batchNo?: string; modelValue?: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<LocationBalance[]>([])
const load = async (open: boolean) => {
  if (open && props.warehouseId && props.skuId) {
    const rows: LocationBalance[] = await request.get({
      url: '/wms/storage-place/dimension-inventory',
      params: { warehouseId: props.warehouseId, skuId: props.skuId, batchNo: props.batchNo || '' }
    })
    options.value = rows.filter((row) => row.locationId != null)
  }
}
</script>

<template>
  <el-select :model-value="modelValue" :disabled="disabled" clearable placeholder="留空为未分配库位" @update:model-value="emit('update:modelValue', $event || undefined)" @visible-change="load">
    <el-option v-for="item in options" :key="item.locationId" :label="`${item.name} (${item.code}) · ${item.quantity}`" :value="item.locationId" />
  </el-select>
</template>
