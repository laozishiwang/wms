<script setup lang="ts">
import request from '@/config/axios'
import { ElDialog, ElMessage } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import { DICT_TYPE, getDictLabel, getIntDictOptions } from '@/utils/dict'
import { toCsv } from './detail-export'
import ReceiptOrderDetail from '../receipt/ReceiptOrderDetail.vue'
import ShipmentOrderDetail from '../shipment/ShipmentOrderDetail.vue'
const props = defineProps<{ direction: 'receipt' | 'shipment'; inline?: boolean }>()
const visible = ref(false)
const loading = ref(false)
const exporting = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dates = ref<string[]>([])
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
const optionLists = reactive<Record<string, {value: string | number; label: string}[]>>({})
const optionRequests: Record<string, number> = {}
const loadOptions = async (field: string, term = '') => {
  const requestId = optionRequests[field] = (optionRequests[field] || 0) + 1
  const data = await request.get({ url: '/wms/order-detail-query/page', params: { ...query, direction: props.direction, startTime: dates.value?.[0], endTime: dates.value?.[1], optionField: field, optionSearch: term } })
  if (optionRequests[field] === requestId) {
    const selected = optionLists[field]?.find((option) => option.value === query[field])
    optionLists[field] = selected && !data.list.some((option: { value: string | number }) => option.value === selected.value)
      ? [selected, ...data.list] : data.list
  }
}
watch([query, dates], () => {
  for (const field of Object.keys(optionRequests)) optionRequests[field]++
  for (const field of Object.keys(optionLists)) {
    optionLists[field] = optionLists[field].filter((option) => option.value === query[field])
  }
}, { deep: true })
const fields = [{key:'warehouseId',label:'仓库'}, {key:'no',label:'单据号'}, {key:'itemName',label:'物品名称'}, {key:'skuCode',label:'规格编号'}, {key:'locationKeyword',label:'库位'}, {key:'batchNo',label:'批次号'}, {key:'palletNo',label:'托盘号'}, {key:'boxNo',label:'箱号'}, {key:'rollNo',label:'卷号'}]

const advancedVisible = ref(false)
const rangeGroups = [
  { label: '长度（cm）', fields: [{key:'netLength',label:'净长'}, {key:'grossLength',label:'毛长'}] },
  { label: '重量（kg）', fields: [{key:'netWeight',label:'净重'}, {key:'grossWeight',label:'毛重'}] },
  { label: '尺寸（cm）', fields: [{key:'length',label:'长'}, {key:'width',label:'宽'}, {key:'height',label:'高'}] },
  { label: '金额与数量', fields: [{key:'quantity',label:'数量'}, {key:'price',label:'单据单价'}, {key:'arrivalCostPrice',label:'到货成本价'}, {key:'salesPrice',label:'销售价'}, {key:'totalPrice',label:'金额'}] }
]
const settingsVisible = ref(false)
const receiptDetail = ref<InstanceType<typeof ReceiptOrderDetail>>()
const shipmentDetail = ref<InstanceType<typeof ShipmentOrderDetail>>()
const openOrder = (id: number) => {
  if (props.direction === 'receipt') receiptDetail.value?.open(id)
  else shipmentDetail.value?.open(id)
}
const draftColumns = ref<{key: string; label: string; show: boolean}[]>([])
const openSettings = () => { draftColumns.value = columns.value.map((column) => ({ ...column })); settingsVisible.value = true }
const defaults = [{key:'no',label:'单据号'}, {key:'status',label:'状态'}, {key:'type',label:'类型'}, {key:'orderTime',label:'单据日期'}, {key:'warehouseName',label:'仓库'}, {key:'merchantName',label:'供应商/客户'}, {key:'item',label:'物品/规格'}, {key:'location',label:'库位'}, {key:'batchNo',label:'批次号'}, {key:'arrivalId',label:'到货记录'}, {key:'pack',label:'托盘/箱/卷号'}, {key:'dates',label:'生产/到期日期'}, {key:'lengths',label:'净长/毛长(cm)'}, {key:'weights',label:'净重/毛重(kg)'}, {key:'dimensions',label:'长/宽/高(cm)'}, {key:'prices',label:'单价/到货成本/销售价/金额'}, {key:'quantity',label:'数量'}, {key:'serialNos',label:'序列号'}, {key:'bizOrderNo',label:'业务单号'}, {key:'remark',label:'备注'}]
const storageKey = `wms:detail-columns:${props.direction}`
const columns = ref(defaults.map((c) => ({...c, show:true})))
try { const saved = JSON.parse(localStorage.getItem(storageKey) || '[]'); if (Array.isArray(saved) && saved.length) columns.value = [...saved.filter((v) => defaults.some((c) => c.key === v.key)).map((v) => ({...defaults.find((c) => c.key === v.key)!, show: v.show})), ...defaults.filter((c) => !saved.some((v) => v.key === c.key)).map((c) => ({...c,show:true}))] } catch { /* Invalid browser settings use defaults. */ }
const moveColumn = (index: number, delta: number) => { const target = index + delta; if (target >= 0 && target < draftColumns.value.length) [draftColumns.value[index], draftColumns.value[target]] = [draftColumns.value[target], draftColumns.value[index]] }
const saveColumns = () => { columns.value = draftColumns.value.map((column) => ({ ...column })); localStorage.setItem(storageKey, JSON.stringify(columns.value)); settingsVisible.value = false }
const dateText = (value: any) => value ? formatDate(value, 'YYYY-MM-DD') : '-'
const textValue = (value: any) => value ?? '-'
let listRequestId = 0
const load = async () => {
  const requestId = ++listRequestId
  loading.value = true
  try {
    const result = await request.get({ url: '/wms/order-detail-query/page', params: { ...query, direction: props.direction, startTime: dates.value?.[0], endTime: dates.value?.[1] } })
    if (requestId === listRequestId) {
      rows.value = result.list
      total.value = result.total
    }
  } finally { if (requestId === listRequestId) loading.value = false }
}
const search = () => {
  for (const group of rangeGroups) for (const field of group.fields) {
    const min = query[field.key + 'Min'], max = query[field.key + 'Max']
    if (min != null && max != null && min > max) { ElMessage.warning(`${field.label}的最小值不能大于最大值`); return }
  }
  for (const field of [{ key: 'productionDate', label: '生产日期' }, { key: 'expirationDate', label: '到期日期' }]) {
    const from = query[field.key + 'From'], to = query[field.key + 'To']
    if (from && to && from > to) { ElMessage.warning(`${field.label}的开始日期不能晚于结束日期`); return }
  }
  query.pageNo = 1; load()
}
const reset = () => { Object.keys(query).forEach((key) => delete query[key]); Object.assign(query, { pageNo: 1, pageSize: 10 }); dates.value = []; load() }
const exportDetails = async () => {
  if (exporting.value) return
  const params = { ...query, direction: props.direction, startTime: dates.value?.[0], endTime: dates.value?.[1], pageSize: 100 }
  const headers: Array<[string, string]> = [ ['no','单据号'], ['status','状态'], ['type','类型'], ['orderTime','单据日期'], ['warehouseName','仓库'], ['merchantName','供应商/客户'], ['itemName','物品名称'], ['itemCode','物品编号'], ['skuName','规格名称'], ['skuCode','规格编号'], ['itemUnit','单位'], ['locationName','库位'], ['locationCode','库位编号'], ['batchNo','批次号'], ['arrivalId','到货记录'], ['palletNo','托盘号'], ['boxNo','箱号'], ['rollNo','卷号'], ['productionDate','生产日期'], ['expirationDate','到期日期'], ['netLength','净长(cm)'], ['grossLength','毛长(cm)'], ['netWeight','净重(kg)'], ['grossWeight','毛重(kg)'], ['length','长(cm)'], ['width','宽(cm)'], ['height','高(cm)'], ['quantity','数量'], ['price','单据单价'], ['arrivalCostPrice','到货成本'], ['salesPrice','销售价'], ['totalPrice','金额'], ['serialNos','序列号'], ['bizOrderNo','业务单号'], ['remark','备注'] ]
  exporting.value = true
  try {
    const all: any[] = []
    for (let pageNo = 1; ; pageNo++) {
      const result = await request.get({ url: '/wms/order-detail-query/page', params: { ...params, pageNo } })
      all.push(...result.list)
      if (!result.list.length || all.length >= result.total) break
    }
    const data = all.map(row => headers.map(([key]) => key === 'status' ? getDictLabel(DICT_TYPE.WMS_ORDER_STATUS, row[key]) : key === 'type' ? getDictLabel(props.direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE, row[key]) : ['orderTime','productionDate','expirationDate'].includes(key) ? (row[key] ? dateText(row[key]) : '') : row[key]))
    const blob = new Blob([toCsv([headers.map(([, label]) => label), ...data])], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob), link = document.createElement('a')
    link.href = url; link.download = `${props.direction === 'receipt' ? '入库' : '出库'}明细.csv`; link.click(); URL.revokeObjectURL(url)
  } finally { exporting.value = false }
}
const open = () => { visible.value = true; search() }
onMounted(() => { if (props.inline) search() })
defineExpose({ open })
</script>
<template>
  <component :is="inline ? 'div' : ElDialog" v-model="visible" :title="direction === 'receipt' ? '入库明细查询' : '出库明细查询'" width="95%" :append-to-body="!inline">
    <el-form inline label-width="70px">
      <el-form-item v-for="field in fields" :key="field.key" :label="field.label"><el-select v-model="query[field.key]" clearable filterable remote style="width: 200px" :remote-method="(text: string) => loadOptions(field.key, text)" @visible-change="(shown: boolean) => shown && loadOptions(field.key)"><el-option v-for="option in optionLists[field.key] || []" :key="option.value" :value="option.value" :label="option.label" /></el-select></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(DICT_TYPE.WMS_ORDER_STATUS)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.type" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="单据日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date('2000-01-01 00:00:00'), new Date('2000-01-01 23:59:59')]" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button @click="advancedVisible = !advancedVisible">{{ advancedVisible ? '收起高级筛选' : '高级筛选' }}</el-button><el-button @click="openSettings">表格设置</el-button><el-button type="success" plain :loading="exporting" @click="exportDetails">导出明细</el-button></el-form-item>
    </el-form>
    <div v-show="advancedVisible" class="advanced-filters">
      <div class="filter-group">
        <strong>单据与追溯</strong>
        <div v-for="field in [{key:'merchantName',label:'供应商/客户'}, {key:'bizOrderNo',label:'业务单号'}]" :key="field.key" class="filter-range">
          <span>{{ field.label }}</span><el-select v-model="query[field.key]" clearable filterable remote :remote-method="(text: string) => loadOptions(field.key, text)" @visible-change="(shown: boolean) => shown && loadOptions(field.key)"><el-option v-for="option in optionLists[field.key] || []" :key="option.value" :value="option.value" :label="option.label" /></el-select>
        </div>
        <div v-for="field in [{key:'serialNo',label:'序列号'}, {key:'remark',label:'备注'}]" :key="field.key" class="filter-range">
          <span>{{ field.label }}</span><el-input v-model="query[field.key]" clearable :placeholder="`输入${field.label}`" />
        </div>
        <div class="filter-range"><span>到货记录 ID</span><el-input-number v-model="query.arrivalId" :min="0" :precision="0" :controls="false" :placeholder="direction === 'shipment' ? '0 为未分配' : '入库明细 ID'" /></div>
      </div>
      <div class="filter-group">
        <strong>生产与到期日期</strong>
        <div v-for="field in [{key:'productionDate',label:'生产日期'}, {key:'expirationDate',label:'到期日期'}]" :key="field.key" class="filter-range">
          <span>{{ field.label }}</span>
          <el-date-picker v-model="query[field.key + 'From']" type="date" value-format="YYYY-MM-DD 00:00:00" placeholder="开始日期" />
          <span>至</span>
          <el-date-picker v-model="query[field.key + 'To']" type="date" value-format="YYYY-MM-DD 23:59:59" placeholder="结束日期" />
        </div>
      </div>
      <div v-for="group in rangeGroups" :key="group.label" class="filter-group">
        <strong>{{ group.label }}</strong>
        <div v-for="field in group.fields" :key="field.key" class="filter-range">
          <span>{{ field.label }}</span>
          <el-input-number v-model="query[field.key + 'Min']" :controls="false" placeholder="最小值" />
          <span>至</span>
          <el-input-number v-model="query[field.key + 'Max']" :controls="false" placeholder="最大值" />
        </div>
      </div>
      <p class="filter-hint">范围包含边界；只填写一端即可，0 是有效值。出库到货属性来自关联的入库记录。</p>
    </div>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column v-for="column in columns.filter((c) => c.show)" :key="column.key" :label="column.label" :prop="column.key" min-width="150" :fixed="column.key === 'no' ? 'left' : false">
        <template #default="{ row }">
          <el-button v-if="column.key === 'no'" link type="primary" @click="openOrder(row.orderId)">{{ row.no }}</el-button>
          <dict-tag v-else-if="column.key === 'status'" :type="DICT_TYPE.WMS_ORDER_STATUS" :value="row.status" />
          <dict-tag v-else-if="column.key === 'type'" :type="direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE" :value="row.type" />
          <template v-else-if="column.key === 'orderTime'">{{ dateText(row.orderTime) }}</template>
          <template v-else-if="column.key === 'item'">{{ textValue(row.itemName) }} / {{ textValue(row.itemCode) }}<br />{{ textValue(row.skuName) }} / {{ textValue(row.skuCode) }}<br />单位：{{ textValue(row.itemUnit) }}</template>
          <template v-else-if="column.key === 'location'">{{ textValue(row.locationName) }}<br />{{ textValue(row.locationCode) }}</template>
          <template v-else-if="column.key === 'arrivalId'">{{ row.arrivalId == null || row.arrivalId === 0 ? '历史未分配' : `到货 #${row.arrivalId}` }}</template>
          <template v-else-if="column.key === 'pack'">托盘：{{ textValue(row.palletNo) }}<br />箱：{{ textValue(row.boxNo) }}<br />卷：{{ textValue(row.rollNo) }}</template>
          <template v-else-if="column.key === 'dates'">生产：{{ dateText(row.productionDate) }}<br />到期：{{ dateText(row.expirationDate) }}</template>
          <template v-else-if="column.key === 'lengths'">净：{{ textValue(row.netLength) }}<br />毛：{{ textValue(row.grossLength) }}</template>
          <template v-else-if="column.key === 'weights'">净：{{ textValue(row.netWeight) }}<br />毛：{{ textValue(row.grossWeight) }}</template>
          <template v-else-if="column.key === 'dimensions'">长：{{ textValue(row.length) }}<br />宽：{{ textValue(row.width) }}<br />高：{{ textValue(row.height) }}</template>
          <template v-else-if="column.key === 'prices'">单据单价：{{ textValue(row.price) }}<br />到货成本：{{ textValue(row.arrivalCostPrice) }}<br />销售价：{{ textValue(row.salesPrice) }}<br />金额：{{ textValue(row.totalPrice) }}</template>
          <template v-else>{{ textValue(row[column.key]) }}</template>
        </template>
      </el-table-column>
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
    <el-dialog v-model="settingsVisible" title="表格设置" width="460px" append-to-body>
      <div v-for="(column, index) in draftColumns" :key="column.key" class="column-setting"><el-checkbox v-model="column.show">{{ column.label }}</el-checkbox><span><el-button size="small" :disabled="index === 0" @click="moveColumn(index, -1)">上移</el-button><el-button size="small" :disabled="index === draftColumns.length - 1" @click="moveColumn(index, 1)">下移</el-button></span></div>
      <template #footer><el-button @click="draftColumns = defaults.map((c) => ({...c, show:true}))">恢复默认</el-button><el-button type="primary" @click="saveColumns">保存</el-button></template>
    </el-dialog>
    <ReceiptOrderDetail v-if="direction === 'receipt'" ref="receiptDetail" />
    <ShipmentOrderDetail v-else ref="shipmentDetail" />
  </component>
</template>
<style scoped>
.column-setting { display: flex; justify-content: space-between; padding: 4px 0; }
.advanced-filters { display: flex; flex-wrap: wrap; gap: 12px; margin-bottom: 16px; }
.filter-group { padding: 12px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; }
.filter-range { display: flex; align-items: center; gap: 6px; margin-top: 8px; }
.filter-range > span:first-child { width: 80px; }
.filter-range :deep(.el-input-number) { width: 110px; }
.filter-hint { flex-basis: 100%; margin: 0; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
