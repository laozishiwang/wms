<script setup lang="ts">
import * as DictDataApi from '@/api/system/dict/dict.data'
import DictDataForm from '@/views/system/dict/data/DictDataForm.vue'
import { useDictStore } from '@/store/modules/dict'

const visible = ref(false)
const loading = ref(false)
const rows = ref<DictDataApi.DictDataVO[]>([])
const formRef = ref<InstanceType<typeof DictDataForm>>()
const message = useMessage()
const dictStore = useDictStore()
const dictType = 'wms_receipt_order_type'

const load = async () => {
  loading.value = true
  try {
    const params = { pageNo: 1, pageSize: 100, dictType }
    const result = await DictDataApi.getDictDataPage(params)
    rows.value = result.list
  } finally { loading.value = false }
}
const open = async () => { visible.value = true; await load() }
const changed = async () => { await dictStore.resetDict(); await load() }
const remove = async (id: number) => {
  await message.delConfirm()
  await DictDataApi.deleteDictData(id)
  await changed()
  message.success('删除成功')
}
defineExpose({ open })
</script>

<template>
  <el-dialog v-model="visible" title="维护入库类型" width="min(720px, 95vw)" append-to-body>
    <p>新增类型时，数据键值请填写未使用的整数；已有单据使用的类型建议停用。</p>
    <el-button v-hasPermi="['system:dict:create']" type="primary" plain @click="formRef?.open('create', undefined, dictType)">新增类型</el-button>
    <el-table v-loading="loading" :data="rows" class="mt-12px">
      <el-table-column prop="label" label="类型名称" />
      <el-table-column prop="value" label="键值" width="100" />
      <el-table-column label="状态" width="100"><template #default="{ row }">{{ row.status === 0 ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button v-hasPermi="['system:dict:update']" link type="primary" @click="formRef?.open('update', row.id, dictType)">修改</el-button>
          <el-button v-hasPermi="['system:dict:delete']" link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <DictDataForm ref="formRef" @success="changed" />
  </el-dialog>
</template>
