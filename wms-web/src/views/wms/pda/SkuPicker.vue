<script setup lang="ts">
import { ItemSkuApi, type ItemSkuVO } from '@/api/wms/md/item/sku'

const visible = defineModel<boolean>({ required: true })
defineProps<{ allowCreate?: boolean; allowBind?: boolean }>()
const emit = defineEmits<{ select: [sku: ItemSkuVO]; create: [] }>()
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const rows = ref<ItemSkuVO[]>([])
const loading = ref(false)
const load = async () => {
  loading.value = true
  try {
    const result = await ItemSkuApi.getItemSkuPage({ pageNo: page.value, pageSize: 20, itemName: keyword.value.trim() || undefined })
    rows.value = result.list
    total.value = result.total
  } finally { loading.value = false }
}
watch(visible, (open) => {
  if (open) { keyword.value = ''; page.value = 1; load() }
})
const select = (sku: ItemSkuVO) => { visible.value = false; emit('select', sku) }
</script>

<template>
  <el-dialog v-model="visible" title="选择物品" width="min(520px, 95vw)" append-to-body>
    <form class="search" @submit.prevent="page = 1; load()"><el-input v-model="keyword" placeholder="输入物品名称" /><el-button native-type="submit" type="primary">查询</el-button></form>
    <div v-loading="loading" class="sku-list">
      <button v-for="sku in rows" :key="sku.id" class="sku-option" :disabled="allowBind === false" @click="select(sku)"><strong>{{ sku.itemName }} {{ sku.name }}</strong><span>{{ sku.code || '无规格编号' }}</span></button>
      <el-empty v-if="!loading && !rows.length" description="没有找到物品" />
    </div>
    <div class="pages"><el-button :disabled="page === 1" @click="page--; load()">上一页</el-button><span>{{ page }} / {{ Math.max(1, Math.ceil(total / 20)) }}</span><el-button :disabled="page * 20 >= total" @click="page++; load()">下一页</el-button></div>
    <template v-if="allowCreate" #footer><el-button type="primary" plain @click="visible = false; emit('create')">新建物品</el-button></template>
  </el-dialog>
</template>

<style scoped>
.search { display:flex; gap:8px; margin-bottom:12px; }.sku-list { max-height:48vh; overflow-y:auto; }.sku-option { display:grid; gap:6px; width:100%; padding:14px 8px; text-align:left; border:0; border-bottom:1px solid #e9eef6; background:#fff; color:#17263c; }.sku-option span { font-size:13px; color:#77879c; }.pages { display:flex; justify-content:space-between; align-items:center; margin-top:12px; color:#718198; }
</style>
