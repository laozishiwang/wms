<template>
  <Dialog v-model="visible" title="物品二维码规则" width="860px">
    <p v-if="sku">当前规格：{{ sku.name }}（{{ sku.code }}）</p>
    <el-button v-hasPermi="['wms:item:update']" type="primary" @click="edit()">新增规则</el-button>
    <el-table :data="rules" class="mt-12px">
      <el-table-column prop="name" label="规则名称" />
      <el-table-column prop="prefix" label="前缀" />
      <el-table-column prop="delimiter" label="分隔符" />
      <el-table-column label="关联规格" width="110"><template #default="{ row }"><el-button link type="primary" @click="showBindings(row)">{{ row.bindingCount || 0 }} 个</el-button></template></el-table-column>
      <el-table-column label="状态"><template #default="{ row }">{{ row.enabled ? '启用' : '停用' }}</template></el-table-column>
      <el-table-column label="操作" width="150"><template #default="{ row }">
        <el-button v-if="sku" v-hasPermi="['wms:item:update']" link type="primary" @click="toggle(row)">{{ bound.includes(row.id) ? '解除关联' : '关联' }}</el-button>
        <el-button link type="primary" @click="edit(row)">查看 / 修改</el-button>
        <el-button v-hasPermi="['wms:item:update']" link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-dialog v-model="bindingsVisible" append-to-body title="已关联物品规格" width="min(620px, 94vw)"><el-table :data="bindings"><el-table-column prop="itemName" label="物品" /><el-table-column prop="skuName" label="规格" /><el-table-column prop="skuCode" label="规格编号" /><el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-hasPermi="['wms:item:update']" link type="danger" @click="unbindFromList(row)">解除关联</el-button></template></el-table-column></el-table></el-dialog>
    <el-dialog v-model="editing" append-to-body title="规则设置" width="680px">
      <el-form label-width="100px">
        <el-form-item label="规则名称"><el-input v-model="form.name" maxlength="100" /></el-form-item>
        <el-form-item label="匹配前缀"><el-input v-model="form.prefix" placeholder="可留空" maxlength="64" /></el-form-item>
        <el-form-item label="分隔符"><el-input v-model="form.delimiter" placeholder="例如 #" maxlength="16" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="form.enabled" /></el-form-item>
        <el-form-item label="二维码样码"><el-input v-model="form.sample" placeholder="输入或扫描样码，例如 SKU#批次#数量" /></el-form-item>
        <el-form-item v-if="segments.length > 1" label="识别结果"><div class="segment-list"><label v-for="(segment, index) in segments" :key="index"><span>{{ segment || '（空）' }}</span><el-select :model-value="fieldAt(index + 1)" clearable placeholder="选择含义" @update:model-value="setField(index + 1, $event)"><el-option v-for="field in fieldOptions" :key="field.key" :label="field.label" :value="field.key" /></el-select></label></div></el-form-item>
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
import type { ItemSkuVO } from '@/api/wms/md/item/sku'
type Rule = { id?: number; name: string; prefix: string; delimiter: string; enabled: boolean; bindingCount?: number; fields: Record<string, number | undefined>; sample: string }
const fieldOptions = [ ['skuCode','规格编号'], ['batchNo','批次号'], ['palletNo','托盘号'], ['boxNo','箱号'], ['rollNo','卷号'], ['quantity','数量'], ['netLength','净长'], ['grossLength','毛长'], ['netWeight','净重'], ['grossWeight','毛重'], ['length','长'], ['width','宽'], ['height','高'], ['price','成本价'], ['salesPrice','销售价'] ].map(([key,label]) => ({key,label}))
const visible = ref(false), editing = ref(false), rules = ref<Rule[]>([]), previewText = ref('')
const sku = ref<ItemSkuVO>(), bound = ref<number[]>([])
const bindingsVisible = ref(false), bindings = ref<Array<{skuId: number; itemName: string; skuName: string; skuCode: string}>>([]), bindingRule = ref<Rule>()
const empty = (): Rule => ({ name: '', prefix: '', delimiter: '#', enabled: true, fields: { skuCode: 1 }, sample: '' })
const form = ref<Rule>(empty())
const load = async () => { rules.value = await request.get({ url: '/wms/scan-rule/list' }) }
const open = async (item?: ItemSkuVO) => { sku.value = item; visible.value = true; await load(); bound.value = item?.id ? await request.get({ url: '/wms/scan-rule/sku-rules', params: { skuId: item.id } }) : [] }
defineExpose({ open })
const edit = (row?: Rule) => { form.value = row ? { ...row, fields: { ...row.fields }, sample: '' } : empty(); previewText.value = ''; editing.value = true }
const segments = computed(() => form.value.sample && form.value.delimiter ? form.value.sample.split(form.value.delimiter) : [])
const fieldAt = (position: number) => Object.entries(form.value.fields).find(([, value]) => value === position)?.[0]
const setField = (position: number, key?: string) => { for (const name of Object.keys(form.value.fields)) if (form.value.fields[name] === position || name === key) delete form.value.fields[name]; if (key) form.value.fields[key] = position }
const toggle = async (row: Rule) => { if (!sku.value?.id || !row.id) return; const params = { skuId: sku.value.id, ruleId: row.id }; if (bound.value.includes(row.id)) await request.delete({ url: '/wms/scan-rule/sku-rule', params }); else await request.put({ url: '/wms/scan-rule/sku-rule', params }); await open(sku.value) }
const showBindings = async (row: Rule) => { bindingRule.value = row; bindings.value = await request.get({ url: '/wms/scan-rule/bindings', params: { ruleId: row.id } }); bindingsVisible.value = true }
const unbindFromList = async (row: {skuId: number}) => { await request.delete({ url: '/wms/scan-rule/sku-rule', params: { skuId: row.skuId, ruleId: bindingRule.value?.id } }); if (bindingRule.value) await showBindings(bindingRule.value); await open(sku.value) }
const payload = () => ({ ...form.value, fields: Object.fromEntries(Object.entries(form.value.fields).filter(([, value]) => value != null)) })
const save = async () => { if (!form.value.name.trim() || !Object.keys(payload().fields).length) { ElMessage.warning('请填写名称并选择字段'); return }; if (form.value.id && form.value.bindingCount) { const linked: Array<{itemName: string; skuName: string; skuCode: string}> = await request.get({ url: '/wms/scan-rule/bindings', params: { ruleId: form.value.id } }); const names = linked.map(item => `${item.itemName || '已删除物品'} ${item.skuName || ''}（${item.skuCode || '-'}）`).join('、'); await ElMessageBox.confirm(`修改会影响关联规格：${names}。确定保存？`, '修改规则') }; await request.post({ url: '/wms/scan-rule/save', data: payload() }); ElMessage.success('保存成功'); editing.value = false; await load() }
const preview = async () => { const data: Record<string,string> = await request.post({ url: '/wms/scan-rule/preview', data: payload() }); const items: ItemSkuVO[] = data.skuCode ? await request.get({ url: '/wms/item-sku/exact', params: { value: data.skuCode } }) : []; const item = !data.skuCode ? '扫码前需选择物品　' : items.length === 1 ? `识别物品：${items[0].itemName || ''} ${items[0].name || ''}　` : '物品未找到或不唯一　'; previewText.value = item + Object.entries(data).map(([key,value]) => `${fieldOptions.find(item => item.key === key)?.label || key}：${value}`).join('　') }
const remove = async (row: Rule) => { await ElMessageBox.confirm(`删除规则“${row.name}”？`, '删除规则'); await request.delete({ url: '/wms/scan-rule/delete', params: { id: row.id } }); await load() }
</script>
<style scoped>
.field-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; width: 100%; }
.field-grid label { display: flex; flex-direction: column; line-height: 1.6; }
.field-grid :deep(.el-input-number) { width: 100%; }
.segment-list { width: 100%; display: grid; gap: 8px; }
.segment-list label { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; align-items: center; }
</style>
