<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ItemApi } from '@/api/wms/md/item'
import { ItemSkuApi } from '@/api/wms/md/item/sku'
import ItemCategorySelect from '@/views/wms/md/item/category/components/ItemCategorySelect.vue'
import { generateWmsCode } from '@/views/wms/utils/constants'

const visible = defineModel<boolean>({ required: true })
const props = defineProps<{ barcode: string }>()
const emit = defineEmits<{ created: [barcode: string] }>()
const name = ref(''), categoryId = ref<number>(), unit = ref(''), skuName = ref('默认规格'), saving = ref(false)
watch(visible, (open) => { if (open) { name.value = ''; categoryId.value = undefined; unit.value = ''; skuName.value = '默认规格' } })
const save = async () => {
  const barcode = props.barcode.trim()
  if (!name.value.trim() || !categoryId.value || !skuName.value.trim()) { ElMessage.warning('请填写物品名称、分类和规格'); return }
  if (!barcode || barcode.length > 64) { ElMessage.warning('条码长度需为 1 至 64 个字符'); return }
  saving.value = true
  try {
    if ((await ItemSkuApi.getExact(barcode)).length) { ElMessage.warning('该条码已存在，请选择已有物品'); return }
    await ItemApi.createItem({ name: name.value.trim(), code: generateWmsCode('I'), categoryId: categoryId.value, unit: unit.value.trim() || undefined, skus: [{ name: skuName.value.trim(), code: generateWmsCode('S'), barCode: barcode }] })
    visible.value = false
    ElMessage.success('物品已创建')
    emit('created', barcode)
  } finally { saving.value = false }
}
</script>
<template>
  <el-dialog v-model="visible" title="新建物品" width="min(480px, 94vw)" append-to-body :close-on-click-modal="!saving" :show-close="!saving">
    <div class="barcode">条码 <strong>{{ barcode }}</strong></div>
    <el-form label-position="top" @submit.prevent="save">
      <el-form-item label="物品名称" required><el-input v-model="name" maxlength="60" placeholder="例如：可口可乐" /></el-form-item>
      <el-form-item label="物品分类" required><ItemCategorySelect v-model="categoryId" /></el-form-item>
      <el-form-item label="规格" required><el-input v-model="skuName" maxlength="255" placeholder="例如：500ml" /></el-form-item>
      <el-form-item label="单位"><el-input v-model="unit" maxlength="20" placeholder="例如：瓶" /></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建并添加</el-button></template>
  </el-dialog>
</template>
<style scoped>
.barcode { display:flex; justify-content:space-between; gap:12px; padding:12px; margin-bottom:18px; border-radius:10px; background:#f2f6fd; color:#6c7c94; }.barcode strong { color:#243752; overflow-wrap:anywhere; }
</style>
