<script setup lang="ts">
import QRCode from 'qrcode'
import request from '@/config/axios'
import { ElMessage } from 'element-plus'

interface Place { id: number; warehouseId: number; parentId?: number; type: 'SHELF' | 'LOCATION'; code: string; name: string }
const visible = ref(false)
const warehouse = ref<{ id: number; code: string; name: string }>()
const places = ref<Place[]>([])
const kind = ref<'SHELF' | 'LOCATION'>('SHELF')
const form = reactive({ code: '', name: '', parentId: undefined as number | undefined })
const label = ref({ title: '', code: '', qr: '' })
const printRef = ref<HTMLButtonElement>()
const imageRef = ref<HTMLImageElement>()
const printOptions = { id: 'wms-storage-label', popTitle: '仓储二维码' }
const load = async () => { places.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: warehouse.value!.id } }) }
const open = async (row: { id: number; code: string; name: string }) => { warehouse.value = row; places.value = []; kind.value = 'SHELF'; form.code = ''; form.name = ''; form.parentId = undefined; visible.value = true; await load() }
const create = async () => {
  if (!form.code.trim() || !form.name.trim() || (kind.value === 'LOCATION' && !form.parentId)) { ElMessage.warning('请填写编号、名称及所属货架'); return }
  await request.post({ url: '/wms/storage-place/create', data: { warehouseId: warehouse.value!.id, type: kind.value, code: form.code.trim(), name: form.name.trim(), parentId: kind.value === 'LOCATION' ? form.parentId : undefined } })
  form.code = ''; form.name = ''; await load(); ElMessage.success('创建成功')
}
const print = async (type: 'WAREHOUSE' | 'SHELF' | 'LOCATION', item: { id: number; name: string; code: string }) => {
  label.value = { title: item.name, code: item.code, qr: await QRCode.toDataURL(`WMS:${type}:${item.id}`, { width: 260, margin: 1 }) }
  await nextTick(); await imageRef.value?.decode(); printRef.value?.click()
}
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="`${warehouse?.name || ''} · 货架与库位`" width="900px" append-to-body>
    <el-button v-if="warehouse" type="primary" plain @click="print('WAREHOUSE', warehouse)">打印仓库二维码</el-button>
    <el-divider />
    <el-form inline>
      <el-form-item label="类型"><el-select v-model="kind" style="width: 120px"><el-option label="货架" value="SHELF" /><el-option label="库位" value="LOCATION" /></el-select></el-form-item>
      <el-form-item v-if="kind === 'LOCATION'" label="所属货架"><el-select v-model="form.parentId" style="width: 150px"><el-option v-for="item in places.filter((p) => p.type === 'SHELF')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="编号"><el-input v-model="form.code" maxlength="64" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item><el-button v-hasPermi="['wms:warehouse:create']" type="primary" @click="create">新增</el-button></el-form-item>
    </el-form>
    <el-table :data="places"><el-table-column label="类型" width="90"><template #default="{ row }">{{ row.type === 'SHELF' ? '货架' : '库位' }}</template></el-table-column><el-table-column prop="code" label="编号" /><el-table-column prop="name" label="名称" /><el-table-column label="所属货架"><template #default="{ row }">{{ places.find((p) => p.id === row.parentId)?.name || '-' }}</template></el-table-column><el-table-column label="操作" width="120"><template #default="{ row }"><el-button link type="primary" @click="print(row.type, row)">打印二维码</el-button></template></el-table-column></el-table>
    <p>入库时扫描库位码可登记位置，出库可选择有库存的库位。</p>
    <div style="position: fixed; left: -9999px"><div id="wms-storage-label" style="text-align: center; color: #000; background: white; width: 300px"><img ref="imageRef" :src="label.qr" alt="二维码" style="width: 220px; height: 220px" /><div style="font-size: 20px">{{ label.title }}</div><div style="font-size: 16px">{{ label.code }}</div></div></div>
    <button ref="printRef" v-print="printOptions" type="button" style="display: none"></button>
  </el-dialog>
</template>
