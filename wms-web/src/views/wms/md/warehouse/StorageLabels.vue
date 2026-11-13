<script setup lang="ts">
import QRCode from 'qrcode'
import request from '@/config/axios'
import { ElMessage } from 'element-plus'

interface Place { id: number; warehouseId: number; parentId?: number; type: 'SHELF' | 'LOCATION'; code: string; name: string }
const visible = ref(false)
const warehouse = ref<{ id: number; code: string; name: string }>()
const places = ref<Place[]>([])
const kind = ref<'SHELF' | 'LOCATION'>('SHELF')
const visiblePlaces = computed(() => places.value.filter((place) => place.type === kind.value))
const form = reactive({ code: '', name: '', parentId: undefined as number | undefined })
const editing = ref<Place>()
const editForm = reactive({ code: '', name: '' })
const label = ref({ title: '', code: '', qr: '' })
const printRef = ref<HTMLButtonElement>()
const imageRef = ref<HTMLImageElement>()
const selectedPlaces = ref<Place[]>([])
const batchLabels = ref<Array<{ id: number; title: string; code: string; qr: string }>>([])
const batchPrintRef = ref<HTMLButtonElement>()
const batchContainerRef = ref<HTMLElement>()
const printOptions = { id: 'wms-storage-label', popTitle: '仓储二维码' }
const batchPrintOptions = { id: 'wms-storage-labels', popTitle: '仓储二维码' }
const load = async () => { places.value = await request.get({ url: '/wms/storage-place/list', params: { warehouseId: warehouse.value!.id } }) }
const open = async (row: { id: number; code: string; name: string }) => { warehouse.value = row; places.value = []; selectedPlaces.value = []; kind.value = 'SHELF'; form.code = ''; form.name = ''; form.parentId = undefined; visible.value = true; await load() }
const create = async () => {
  if (!form.code.trim() || !form.name.trim() || (kind.value === 'LOCATION' && !form.parentId)) { ElMessage.warning('请填写编号、名称及所属库区'); return }
  await request.post({ url: '/wms/storage-place/create', data: { warehouseId: warehouse.value!.id, type: kind.value, code: form.code.trim(), name: form.name.trim(), parentId: kind.value === 'LOCATION' ? form.parentId : undefined } })
  form.code = ''; form.name = ''; await load(); ElMessage.success('创建成功')
}
const startEdit = (place: Place) => {
  editing.value = place
  editForm.code = place.code
  editForm.name = place.name
}
const saveEdit = async () => {
  if (!editForm.code.trim() || !editForm.name.trim()) { ElMessage.warning('请填写编号和名称'); return }
  await request.put({ url: '/wms/storage-place/update', data: { id: editing.value!.id, warehouseId: warehouse.value!.id, code: editForm.code.trim(), name: editForm.name.trim() } })
  editing.value = undefined
  await load()
  ElMessage.success('修改成功')
}
const print = async (type: 'WAREHOUSE' | 'SHELF' | 'LOCATION', item: { id: number; name: string; code: string }) => {
  label.value = { title: item.name, code: item.code, qr: await QRCode.toDataURL(`WMS:${type}:${item.id}`, { width: 260, margin: 1 }) }
  await nextTick(); await imageRef.value?.decode(); printRef.value?.click()
}
const printSelected = async () => {
  batchLabels.value = await Promise.all(selectedPlaces.value.map(async (place) => ({
    id: place.id, title: place.name, code: place.code,
    qr: await QRCode.toDataURL(`WMS:${place.type}:${place.id}`, { width: 260, margin: 1 })
  })))
  await nextTick()
  await Promise.all(Array.from(batchContainerRef.value?.querySelectorAll('img') || []).map((img) => img.decode()))
  batchPrintRef.value?.click()
}
watch(kind, () => { selectedPlaces.value = [] })
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" :title="`${warehouse?.name || ''} · 库区与库位`" width="900px" append-to-body>
    <el-button v-if="warehouse" type="primary" plain @click="print('WAREHOUSE', warehouse)">打印仓库二维码</el-button>
    <el-button plain :disabled="!selectedPlaces.length" @click="printSelected">批量打印所选二维码（{{ selectedPlaces.length }}）</el-button>
    <el-divider />
    <el-form inline>
      <el-form-item label="类型"><el-select v-model="kind" style="width: 120px"><el-option label="库区" value="SHELF" /><el-option label="库位" value="LOCATION" /></el-select></el-form-item>
      <el-form-item v-if="kind === 'LOCATION'" label="所属库区"><el-select v-model="form.parentId" style="width: 150px"><el-option v-for="item in places.filter((p) => p.type === 'SHELF')" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="编号"><el-input v-model="form.code" maxlength="64" /></el-form-item>
      <el-form-item label="名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
      <el-form-item><el-button v-hasPermi="['wms:warehouse:create']" type="primary" @click="create">新增</el-button></el-form-item>
    </el-form>
    <el-table :data="visiblePlaces" @selection-change="selectedPlaces = $event"><el-table-column type="selection" width="50" /><el-table-column label="类型" width="90"><template #default="{ row }">{{ row.type === 'SHELF' ? '库区' : '库位' }}</template></el-table-column><el-table-column prop="code" label="编号" /><el-table-column prop="name" label="名称" /><el-table-column label="所属库区"><template #default="{ row }">{{ places.find((p) => p.id === row.parentId)?.name || '-' }}</template></el-table-column><el-table-column label="操作" width="170"><template #default="{ row }"><el-button v-hasPermi="['wms:warehouse:update']" link type="primary" @click="startEdit(row)">修改</el-button><el-button link type="primary" @click="print(row.type, row)">打印二维码</el-button></template></el-table-column></el-table>
    <p>入库时扫描库位码可登记位置，出库可选择有库存的库位。</p>
    <div style="position: fixed; left: -9999px"><div id="wms-storage-label" style="text-align: center; color: #000; background: white; width: 300px"><img ref="imageRef" :src="label.qr" alt="二维码" style="width: 220px; height: 220px" /><div style="font-size: 20px">{{ label.title }}</div><div style="font-size: 16px">{{ label.code }}</div></div></div>
    <button ref="printRef" v-print="printOptions" type="button" style="display: none"></button>
    <div style="position: fixed; left: -9999px"><div id="wms-storage-labels" ref="batchContainerRef" style="color: #000; background: white"><div v-for="item in batchLabels" :key="item.id" style="display: inline-block; width: 250px; text-align: center; break-inside: avoid; padding: 10px"><img :src="item.qr" alt="二维码" style="width: 200px; height: 200px" /><div style="font-size: 20px">{{ item.title }}</div><div style="font-size: 16px">{{ item.code }}</div></div></div></div>
    <button ref="batchPrintRef" v-print="batchPrintOptions" type="button" style="display: none"></button>
    <el-dialog :model-value="!!editing" title="修改库区或库位" width="min(460px, 94vw)" append-to-body @update:model-value="(value) => { if (!value) editing = undefined }">
      <el-form label-width="70px">
        <el-form-item label="编号"><el-input v-model="editForm.code" maxlength="64" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="editForm.name" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="editing = undefined">取消</el-button><el-button type="primary" @click="saveEdit">保存</el-button></template>
    </el-dialog>
  </el-dialog>
</template>
