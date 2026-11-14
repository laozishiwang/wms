<script setup lang="ts">
import SkuPicker from '@/views/wms/pda/SkuPicker.vue'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
const model = defineModel<ItemSkuVO>()
const visible = ref(false)
</script>
<template>
  <el-tooltip content="仅当二维码没有物品编号时需要选择" placement="top"><el-button type="primary" plain @click="visible = true">{{ model ? `扫码物品：${model.itemName || ''} ${model.name || ''}` : '选择扫码物品（可选）' }}</el-button></el-tooltip>
  <el-button v-if="model" link @click="model = undefined">清除</el-button>
  <SkuPicker v-model="visible" @select="model = $event" />
</template>
