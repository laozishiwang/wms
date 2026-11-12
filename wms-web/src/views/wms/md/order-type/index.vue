<script setup lang="ts">
import * as DictDataApi from '@/api/system/dict/dict.data'
import DictDataForm from '@/views/system/dict/data/DictDataForm.vue'
import { useDictStore } from '@/store/modules/dict'

defineOptions({ name: 'WmsOrderType' })
const type = ref('wms_receipt_order_type')
const rows = ref<DictDataApi.DictDataVO[]>([])
const loading = ref(false)
const form = ref<InstanceType<typeof DictDataForm>>()
const message = useMessage()
const load = async () => {
  loading.value = true
  try { const params = { pageNo: 1, pageSize: 100, dictType: type.value }; rows.value = (await DictDataApi.getDictDataPage(params)).list }
  finally { loading.value = false }
}
const changed = async () => { await useDictStore().resetDict(); await load() }
const remove = async (id: number) => { try { await message.delConfirm(); await DictDataApi.deleteDictData(id); await changed(); message.success('删除成功') } catch {} }
onMounted(load)
</script>

<template>
  <ContentWrap>
    <el-tabs v-model="type" @tab-change="load"><el-tab-pane label="入库类型" name="wms_receipt_order_type" /><el-tab-pane label="出库类型" name="wms_shipment_order_type" /></el-tabs>
    <p>数据键值使用未占用的非负整数。已被单据使用的类型建议停用，避免历史单据失去名称。</p>
    <el-button v-hasPermi="['system:dict:create']" type="primary" plain @click="form?.open('create', undefined, type)">新增类型</el-button>
    <el-table v-loading="loading" :data="rows" class="mt-12px">
      <el-table-column prop="label" label="类型名称" /><el-table-column prop="value" label="键值" /><el-table-column label="状态"><template #default="{ row }">{{ row.status === 0 ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作"><template #default="{ row }"><el-button v-hasPermi="['system:dict:update']" link type="primary" @click="form?.open('update', row.id, type)">修改</el-button><el-button v-hasPermi="['system:dict:delete']" link type="danger" @click="remove(row.id)">删除</el-button></template></el-table-column>
    </el-table>
    <DictDataForm ref="form" @success="changed" />
  </ContentWrap>
</template>
