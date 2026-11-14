<script setup lang="ts">
defineProps<{ title: string }>()
</script>
<template>
  <div class="detail-entry">
    <div class="detail-heading"><strong>{{ title }}</strong><slot name="settings"></slot></div>
    <div v-if="$slots.select" class="method"><span>选择物品</span><div><slot name="select"></slot></div></div>
    <div v-if="$slots.import" class="method"><span>批量导入</span><div><slot name="import"></slot></div></div>
    <div v-if="$slots.scan" class="method"><span>扫码添加</span><div><slot name="scan"></slot></div></div>
  </div>
</template>
<style scoped>
.detail-entry { margin: 12px 0 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; overflow: hidden; }
.detail-heading { display:flex; align-items:center; gap:12px; padding:12px 16px; background:var(--el-fill-color-light); }
.method { display:flex; align-items:center; gap:16px; padding:10px 16px; border-top:1px solid var(--el-border-color-lighter); }
.method > span { flex:0 0 72px; color:var(--el-text-color-secondary); font-size:13px; }
.method > div { display:flex; flex-wrap:wrap; align-items:center; gap:8px; }
.method :deep(.el-button + .el-button) { margin-left:0; }
@media (max-width: 700px) { .method { align-items:flex-start; flex-direction:column; gap:8px; } .method > span { flex:auto; } }
</style>
