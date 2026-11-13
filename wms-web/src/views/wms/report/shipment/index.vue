<script setup lang="ts">
import OrderDetailQuery from '@/views/wms/order/components/OrderDetailQuery.vue'
const queryRef = ref<InstanceType<typeof OrderDetailQuery>>()
onMounted(() => queryRef.value?.open())
</script>
<template><ContentWrap><el-button type="primary" @click="queryRef?.open()">打开出库明细查询</el-button><OrderDetailQuery ref="queryRef" direction="shipment" /></ContentWrap></template>
