<template>
  <Dialog v-model="visible" title="二维码规则" width="860px">
    <el-button v-hasPermi="['wms:item:update']" type="primary" @click="edit()">新增规则</el-button>
    <el-table :data="rules" class="mt-12px">
      <el-table-column prop="name" label="规则名称" />
      <el-table-column prop="prefix" label="前缀" />
      <el-table-column prop="delimiter" label="分隔符" />
      <el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }">
        <el-button link type="primary" @click="edit(row)">查看 / 修改</el-button>
        <el-button v-hasPermi="['wms:item:update']" link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="editing" append-to-body title="规则设置" width="680px">
      <el-form label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="匹配前缀"><el-input v-model="form.prefix" placeholder="可留空" maxlength="64" /></el-form-item>
        <el-form-item label="分隔符"><el-input v-model="form.delimiter" placeholder="例如 #" maxlength="16" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="字段位置">
          <div class="field-grid">
            <label v-for="field in fieldOptions" :key="field.key">{{ field.label }}<el-input-number v-model="form.fields[field.key]" :min="1" :max="100" :precision="0" controls-position="right" placeholder="不提取" /></label>
          </div>
        </el-form-item>
        <el-form-item label="样码预览"><el-input v-model="form.sample" placeholder="输入完整二维码内容" /><el-button class="mt-8px" @click="preview">预览</el-button><span v-if="previewText" class="ml-12px">{{ previewText }}</span></el-form-item>
      </el-form>
      <template #footer><el-button @click="editing = false">取消</el-button><el-button v-hasPermi="['wms:item:update']" type="primary" @click="save">保存</el-button></template>
    </el-dialog>
  </Dialog>
</template>
<script lang="ts" setup>
import request from '@/config/axios'
import { ElMessage, ElMessageBox } from 'element-plus'
type Rule = { id?: number; name: string; prefix: string; delimiter: string; enabled: boolean; fields: Record<string, number | undefined>; sample: string }
const fieldOptions = [ ['skuCode','规格编号'], ['batchNo','批次号'], ['palletNo','托盘号'], ['boxNo','箱号'], ['rollNo','卷号'], ['quantity','数量'], ['netLength','净长'], ['grossLength','毛长'], ['netWeight','净重'], ['grossWeight','毛重'], ['length','长'], ['width','宽'], ['height','高'], ['price','成本价'], ['salesPrice','销售价'] ].map(([key,label]) => ({key,label}))
const visible = ref(false), editing = ref(false), rules = ref<Rule[]>([]), previewText = ref('')
const empty = (): Rule => ({ name: '', prefix: '', delimiter: '#', enabled: true, fields: { skuCode: 1 }, sample: '' })
const form = ref<Rule>(empty())
const load = async () => { rules.value = await request.get({ url: '/wms/scan-rule/list' }) }
const open = async () => { visible.value = true; await load() }
defineExpose({ open })
const edit = (row?: Rule) => { form.value = row ? { ...row, fields: { ...row.fields }, sample: '' } : empty(); previewText.value = ''; editing.value = true }
const payload = () => ({ ...form.value, fields: Object.fromEntries(Object.entries(form.value.fields).filter(([, value]) => value != null)) })
const save = async () => { if (!form.value.name.trim() || !form.value.fields.skuCode) { ElMessage.warning('请填写名称和规格编号的位置'); return }; await request.post({ url: '/wms/scan-rule/save', data: payload() }); ElMessage.success('保存成功'); editing.value = false; await load() }
const preview = async () => { const data: Record<string,string> = await request.post({ url: '/wms/scan-rule/preview', data: payload() }); previewText.value = Object.entries(data).map(([key,value]) => `${fieldOptions.find(item => item.key === key)?.label || key}：${value}`).join('　') }
const remove = async (row: Rule) => { await ElMessageBox.confirm(`删除规则“${row.name}”？`, '删除规则'); await request.delete({ url: '/wms/scan-rule/delete', params: { id: row.id } }); await load() }
</script>
<style scoped>
.field-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; width: 100%; }
.field-grid label { display: flex; flex-direction: column; line-height: 1.6; }
.field-grid :deep(.el-input-number) { width: 100%; }
</style>
