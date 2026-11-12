<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { ApprovalApi, type ApprovalType } from '@/api/wms/approval'

const props = defineProps<{ type: ApprovalType; id: number; status: number; beforeSubmit?: () => boolean }>()
const emit = defineEmits<{ changed: [] }>()
const visible = ref(false)
const history = ref<any[]>([])
const act = async (action: 'submit' | 'approve' | 'reject') => {
  if (action === 'submit') {
    if (props.beforeSubmit && !props.beforeSubmit()) return
    await ElMessageBox.confirm('确认提交审批？提交后暂不能修改单据。', '提交审批')
    await ApprovalApi.submit(props.type, props.id)
  } else {
    const result = await ElMessageBox.prompt(action === 'approve' ? '请输入审批意见（可留空）' : '请输入驳回原因', action === 'approve' ? '审批通过' : '审批驳回', { inputType: 'textarea' })
    await ApprovalApi[action](props.type, props.id, result.value)
  }
  ElMessage.success('操作成功')
  emit('changed')
}
const showHistory = async () => {
  history.value = await ApprovalApi.history(props.type, props.id)
  visible.value = true
}
</script>

<template>
  <el-button v-if="status === 0" v-hasPermi="['wms:approval:submit']" link type="primary" @click="act('submit')">提交审批</el-button>
  <el-button v-if="status === 1" v-hasPermi="['wms:approval:approve']" link type="success" @click="act('approve')">通过</el-button>
  <el-button v-if="status === 1" v-hasPermi="['wms:approval:approve']" link type="danger" @click="act('reject')">驳回</el-button>
  <el-button v-hasPermi="['wms:approval:query']" link type="primary" @click="showHistory">审批记录</el-button>
  <el-dialog v-model="visible" title="审批记录" width="600px" append-to-body>
    <el-table :data="history" empty-text="暂无审批记录">
      <el-table-column prop="action" label="操作" width="120" />
      <el-table-column prop="comment" label="意见" />
      <el-table-column prop="createTime" label="时间" width="180" />
    </el-table>
  </el-dialog>
</template>
