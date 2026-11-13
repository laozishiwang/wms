<script setup lang="ts">
import request from '@/config/axios'
import { formatDate } from '@/utils/formatTime'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
const props = defineProps<{ direction: 'receipt' | 'shipment' }>()
const visible = ref(false)
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dates = ref<string[]>([])
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
const optionLists = reactive<Record<string, {value: string | number; label: string}[]>>({})
const optionRequests: Record<string, number> = {}
const loadOptions = async (field: string, term = '') => {
  const requestId = optionRequests[field] = (optionRequests[field] || 0) + 1
  const data = await request.get({ url: '/wms/order-detail-query/page', params: { ...query, direction: props.direction, startTime: dates.value?.[0], endTime: dates.value?.[1], optionField: field, optionSearch: term } })
  if (optionRequests[field] === requestId) optionLists[field] = data.list
}
const fields = [{key:'warehouseId',label:'仓库'}, {key:'no',label:'单据号'}, {key:'itemName',label:'物品名称'}, {key:'skuCode',label:'规格编号'}, {key:'locationKeyword',label:'库位'}, {key:'batchNo',label:'批次号'}, {key:'palletNo',label:'托盘号'}, {key:'boxNo',label:'箱号'}, {key:'rollNo',label:'卷号'}]

const settingsVisible = ref(false)
const draftColumns = ref<{key: string; label: string; show: boolean}[]>([])
const openSettings = () => { draftColumns.value = columns.value.map((column) => ({ ...column })); settingsVisible.value = true }
const defaults = [{key:'no',label:'单据号'}, {key:'status',label:'状态'}, {key:'type',label:'类型'}, {key:'orderTime',label:'单据日期'}, {key:'warehouseName',label:'仓库'}, {key:'merchantName',label:'供应商/客户'}, {key:'item',label:'物品/规格'}, {key:'location',label:'库位'}, {key:'batchNo',label:'批次号'}, {key:'pack',label:'托盘/箱/卷号'}, {key:'dates',label:'生产/到期日期'}, {key:'lengths',label:'净长/毛长(cm)'}, {key:'weights',label:'净重/毛重(kg)'}, {key:'dimensions',label:'长/宽/高(cm)'}, {key:'prices',label:'单价/销售价/金额'}, {key:'quantity',label:'数量'}, {key:'serialNos',label:'序列号'}, {key:'bizOrderNo',label:'业务单号'}, {key:'remark',label:'备注'}]
const storageKey = `wms:detail-columns:${props.direction}`
const columns = ref(defaults.map((c) => ({...c, show:true})))
try { const saved = JSON.parse(localStorage.getItem(storageKey) || '[]'); if (Array.isArray(saved) && saved.length) columns.value = [...saved.filter((v) => defaults.some((c) => c.key === v.key)).map((v) => ({...defaults.find((c) => c.key === v.key)!, show: v.show})), ...defaults.filter((c) => !saved.some((v) => v.key === c.key)).map((c) => ({...c,show:true}))] } catch { /* Invalid browser settings use defaults. */ }
const moveColumn = (index: number, delta: number) => { const target = index + delta; if (target >= 0 && target < draftColumns.value.length) [draftColumns.value[index], draftColumns.value[target]] = [draftColumns.value[target], draftColumns.value[index]] }
const saveColumns = () => { columns.value = draftColumns.value.map((column) => ({ ...column })); localStorage.setItem(storageKey, JSON.stringify(columns.value)); settingsVisible.value = false }
const dateText = (value: any) => value ? formatDate(value, 'YYYY-MM-DD') : '-'
const textValue = (value: any) => value ?? '-'
const load = async () => {
  loading.value = true
  try {
    const result = await request.get({ url: '/wms/order-detail-query/page', params: { ...query, direction: props.direction, startTime: dates.value?.[0], endTime: dates.value?.[1] } })
    rows.value = result.list
    total.value = result.total
  } finally { loading.value = false }
}
const search = () => { query.pageNo = 1; load() }
const reset = () => { Object.keys(query).forEach((key) => delete query[key]); Object.assign(query, { pageNo: 1, pageSize: 10 }); dates.value = []; load() }
const open = () => { visible.value = true; search() }
defineExpose({ open })
</script>
<template>
  <el-dialog v-model="visible" :title="direction === 'receipt' ? '入库明细查询' : '出库明细查询'" width="95%" append-to-body>
    <el-form inline label-width="70px">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label"><el-select v-model="query[field.key]" clearable filterable remote style="width: 200px" :remote-method="(text: string) => loadOptions(field.key, text)" @visible-change="(shown: boolean) => shown && loadOptions(field.key)"><el-option v-for="option in optionLists[field.key] || []" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(DICT_TYPE.WMS_ORDER_STATUS)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.type" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="单据日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date('2000-01-01 00:00:00'), new Date('2000-01-01 23:59:59')]" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button @click="openSettings">表格设置</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column v-for="column in columns.filter((c) => c.show)" :key="column.key" :label="column.label" :prop="column.key" min-width="150" :fixed="column.key === 'no' ? 'left' : false">
        <template #default="{ row }">
          <dict-tag v-if="column.key === 'status'" :type="DICT_TYPE.WMS_ORDER_STATUS" :value="row.status" />
          <dict-tag v-else-if="column.key === 'type'" :type="direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE" :value="row.type" />
          <template v-else-if="column.key === 'orderTime'">{{ dateText(row.orderTime) }}</template>
          <template v-else-if="column.key === 'item'">{{ textValue(row.itemName) }} / {{ textValue(row.itemCode) }}<br />{{ textValue(row.skuName) }} / {{ textValue(row.skuCode) }}</template>
          <template v-else-if="column.key === 'location'">{{ textValue(row.locationName) }}<br />{{ textValue(row.locationCode) }}</template>
          <template v-else-if="column.key === 'pack'">托盘：{{ textValue(row.palletNo) }}<br />箱：{{ textValue(row.boxNo) }}<br />卷：{{ textValue(row.rollNo) }}</template>
          <template v-else-if="column.key === 'dates'">生产：{{ dateText(row.productionDate) }}<br />到期：{{ dateText(row.expirationDate) }}</template>
          <template v-else-if="column.key === 'lengths'">净：{{ textValue(row.netLength) }}<br />毛：{{ textValue(row.grossLength) }}</template>
          <template v-else-if="column.key === 'weights'">净：{{ textValue(row.netWeight) }}<br />毛：{{ textValue(row.grossWeight) }}</template>
          <template v-else-if="column.key === 'dimensions'">长：{{ textValue(row.length) }}<br />宽：{{ textValue(row.width) }}<br />高：{{ textValue(row.height) }}</template>
          <template v-else-if="column.key === 'prices'">单价：{{ textValue(row.price) }}<br />销售价：{{ textValue(row.salesPrice) }}<br />金额：{{ textValue(row.totalPrice) }}</template>
          <template v-else>{{ textValue(row[column.key]) }}</template>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    <el-dialog v-model="settingsVisible" title="表格设置" width="460px" append-to-body>
      <div v-for="(column, index) in draftColumns" :key="column.key" class="column-setting"><el-checkbox v-model="column.show">{{ column.label }}</el-checkbox><span><el-button size="small" :disabled="index === 0" @click="moveColumn(index, -1)">上移</el-button><el-button size="small" :disabled="index === draftColumns.length - 1" @click="moveColumn(index, 1)">下移</el-button></span></div>
      <template #footer><el-button @click="draftColumns = defaults.map((c) => ({...c, show:true}))">恢复默认</el-button><el-button type="primary" @click="saveColumns">保存</el-button></template>
    </el-dialog>
  </el-dialog>
</template>
<style scoped>.column-setting { display: flex; justify-content: space-between; padding: 4px 0; }</style>
