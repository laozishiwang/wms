<script setup lang="ts">
import request from '@/config/axios'

interface LocationBalance { locationId: number; code: string; name: string; quantity: number }
const props = defineProps<{ warehouseId?: number; skuId?: number; modelValue?: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const options = ref<LocationBalance[]>([])
const load = async (open: boolean) => {
  if (open && props.warehouseId && props.skuId) {
    options.value = await request.get({ url: '/wms/storage-place/inventory', params: { warehouseId: props.warehouseId, skuId: props.skuId } })
  }
}
</script>

<template>
  <el-select :model-value="modelValue" :disabled="disabled" clearable placeholder="留空为未分配库位" @update:model-value="emit('update:modelValue', $event || undefined)" @visible-change="load">
    <el-option v-for="item in options" :key="item.locationId" :label="`${item.name} (${item.code}) · ${item.quantity}`" :value="item.locationId" />
  </el-select>
</template>
