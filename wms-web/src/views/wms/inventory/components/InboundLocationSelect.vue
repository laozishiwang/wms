<script setup lang="ts">
import request from '@/config/axios'

interface Place { id: number; type: string; code: string; name: string }
const props = defineProps<{ warehouseId?: number; modelValue?: number; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: number | undefined] }>()
const places = ref<Place[]>([])
const load = async (open: boolean) => {
  if (open && props.warehouseId) {
    const rows: Place[] = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: props.warehouseId } })
    places.value = rows.filter((row) => row.type === 'LOCATION')
  }
}
</script>

<template>
  <el-select :model-value="modelValue" :disabled="disabled || !warehouseId" clearable placeholder="留空为未分配库位" @update:model-value="emit('update:modelValue', $event || undefined)" @visible-change="load">
    <el-option v-for="item in places" :key="item.id" :label="`${item.name} (${item.code})`" :value="item.id" />
  </el-select>
</template>
