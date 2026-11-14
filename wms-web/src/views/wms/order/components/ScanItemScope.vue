<script setup lang="ts">
import SkuPicker from '@/views/wms/pda/SkuPicker.vue'
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
const model = defineModel<ItemSkuVO>()
const visible = ref(false)
</script>
<template>
  <el-button plain @click="visible = true">{{ model ? `扫码物品：${model.itemName || ''} ${model.name || ''}` : '指定扫码物品' }}</el-button>
  <el-button v-if="model" link @click="model = undefined">清除</el-button>
  <SkuPicker v-model="visible" @select="model = $event" />
</template>
