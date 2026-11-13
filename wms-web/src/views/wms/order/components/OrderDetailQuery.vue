<script setup lang="ts">
import request from '@/config/axios'
import { DICT_TYPE, getIntDictOptions } from '@/utils/dict'
import WarehouseSelect from '@/views/wms/md/warehouse/components/WarehouseSelect.vue'
const props = defineProps<{ direction: 'receipt' | 'shipment' }>()
const visible = ref(false)
const loading = ref(false)
const rows = ref<any[]>([])
const total = ref(0)
const dates = ref<string[]>([])
const query = reactive<any>({ pageNo: 1, pageSize: 10 })
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
      <el-form-item label="单据号"><el-input v-model="query.no" clearable /></el-form-item>
      <el-form-item label="物品名称"><el-input v-model="query.itemName" clearable /></el-form-item>
      <el-form-item label="规格编号"><el-input v-model="query.skuCode" clearable /></el-form-item>
      <el-form-item label="仓库"><WarehouseSelect v-model="query.warehouseId" class="!w-200px" /></el-form-item>
      <el-form-item label="库位"><el-input v-model="query.locationKeyword" clearable placeholder="库位编号或名称" /></el-form-item>
      <el-form-item label="批次号"><el-input v-model="query.batchNo" clearable /></el-form-item>
      <el-form-item label="托盘号"><el-input v-model="query.palletNo" clearable /></el-form-item>
      <el-form-item label="箱号"><el-input v-model="query.boxNo" clearable /></el-form-item>
      <el-form-item label="卷号"><el-input v-model="query.rollNo" clearable /></el-form-item>
      <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(DICT_TYPE.WMS_ORDER_STATUS)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="类型"><el-select v-model="query.type" clearable style="width: 150px"><el-option v-for="item in getIntDictOptions(direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE)" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
      <el-form-item label="单据日期"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD HH:mm:ss" :default-time="[new Date('2000-01-01 00:00:00'), new Date('2000-01-01 23:59:59')]" start-placeholder="开始日期" end-placeholder="结束日期" /></el-form-item>
      <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="no" label="单据号" min-width="150" fixed />
      <el-table-column label="状态" min-width="100"><template #default="{ row }"><dict-tag :type="DICT_TYPE.WMS_ORDER_STATUS" :value="row.status" /></template></el-table-column>
      <el-table-column label="类型" min-width="120"><template #default="{ row }"><dict-tag :type="direction === 'receipt' ? DICT_TYPE.WMS_RECEIPT_ORDER_TYPE : DICT_TYPE.WMS_SHIPMENT_ORDER_TYPE" :value="row.type" /></template></el-table-column>
      <el-table-column prop="orderTime" label="单据日期" min-width="160" />
      <el-table-column prop="warehouseName" label="仓库" min-width="120" />
      <el-table-column prop="itemName" label="物品名称" min-width="150" />
      <el-table-column prop="skuCode" label="规格编号" min-width="130" />
      <el-table-column prop="skuName" label="规格" min-width="120" />
      <el-table-column prop="locationCode" label="库位编号" min-width="120" />
      <el-table-column prop="locationName" label="库位名称" min-width="120" />
      <el-table-column prop="batchNo" label="批次号" min-width="120" />
      <el-table-column prop="palletNo" label="托盘号" min-width="120" />
      <el-table-column prop="boxNo" label="箱号" min-width="120" />
      <el-table-column prop="rollNo" label="卷号" min-width="120" />
      <el-table-column prop="quantity" label="数量" min-width="100" />
      <el-table-column prop="price" label="单价" min-width="100" />
      <el-table-column prop="totalPrice" label="金额" min-width="100" />
    </el-table>
    <Pagination :total="total" v-model:page="query.pageNo" v-model:limit="query.pageSize" @pagination="load" />
  </el-dialog>
</template>
