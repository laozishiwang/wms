<template>
  <div class="wms-workspace">
    <section class="welcome-card">
      <div class="welcome-content">
        <div class="eyebrow">WAREHOUSE WORKSPACE</div>
        <h1>欢迎回来，{{ userStore.getUser.nickname || '仓库管理员' }}</h1>
        <p>今天也从容管理仓库，关注单据进度与库存变化。</p>
        <div class="welcome-actions">
          <WarehouseSelect v-model="warehouseId" class="!w-200px" placeholder="全部仓库" @change="refresh()" />
          <el-button type="primary" :loading="loading" @click="refresh">刷新数据</el-button>
          <el-button @click="$router.push('/pda')">进入移动作业</el-button>
        </div>
      </div>
      <div class="welcome-note">
        <span>仓储工作台</span>
        <strong>清晰掌握每一次流转</strong>
        <small>入库 · 出库 · 移库 · 盘点</small>
      </div>
    </section>
    <div class="section-heading"><div><h2>业务概览</h2><p>查看单据状态，快速进入对应业务</p></div></div>
    <WmsHomeOrderSummaryCards ref="orderSummaryCardsRef" />
    <WmsHomeOrderTrendChart ref="orderTrendChartRef" />
    <WmsHomeInventoryCharts ref="inventoryChartsRef" />

    <div
      class="mt-2px flex items-center justify-center text-13px text-[var(--el-text-color-secondary)]"
    >
      <Icon class="mr-5px" icon="ep:clock" />
      统计时间：{{ statTime }}
    </div>
  </div>
</template>

<script lang="ts" setup>
import { formatDate } from '@/utils/formatTime'
import { useUserStore } from '@/store/modules/user'
import WarehouseSelect from '@/views/wms/md/warehouse/components/WarehouseSelect.vue'
import WmsHomeInventoryCharts from './components/WmsHomeInventoryCharts.vue'
import WmsHomeOrderSummaryCards from './components/WmsHomeOrderSummaryCards.vue'
import WmsHomeOrderTrendChart from './components/WmsHomeOrderTrendChart.vue'

/** WMS 首页 */
defineOptions({ name: 'WmsHome' })

const userStore = useUserStore()
const loading = ref(false)
const warehouseId = ref<number>()
const statTime = ref(formatDate(new Date()))
const orderSummaryCardsRef = ref<InstanceType<typeof WmsHomeOrderSummaryCards>>()
const orderTrendChartRef = ref<InstanceType<typeof WmsHomeOrderTrendChart>>()
const inventoryChartsRef = ref<InstanceType<typeof WmsHomeInventoryCharts>>()

/** 刷新：重新加载各个组件的数据（传入 warehouseId 以进行针对性查询），并更新时间戳 */
const refresh = async () => {
  loading.value = true
  try {
    await Promise.all([
      orderSummaryCardsRef.value?.load(warehouseId.value),
      orderTrendChartRef.value?.load(warehouseId.value),
      inventoryChartsRef.value?.load(warehouseId.value)
    ])
    statTime.value = formatDate(new Date())
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refresh()
})
</script>

<style scoped>
.wms-workspace { --wms-card-radius: 16px; max-width: 1600px; margin: auto; }
.welcome-card { display: flex; justify-content: space-between; gap: 32px; padding: 38px 42px; margin-bottom: 30px; border: 1px solid #e8edf5; border-radius: 22px; background: linear-gradient(110deg, #fff 52%, #edf5ff); box-shadow: 0 12px 35px rgba(32, 57, 93, .06); }
.eyebrow { color: #438ef1; font-size: 11px; font-weight: 700; letter-spacing: .15em; }
h1 { margin: 14px 0 10px; color: #17253b; font-size: clamp(26px, 3vw, 36px); line-height: 1.25; }
.welcome-content p, .section-heading p { color: #8795a9; margin: 0; }
.welcome-actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 28px; }
.welcome-note { align-self: center; display: flex; flex-direction: column; gap: 12px; min-width: 230px; padding: 24px; border: 1px solid #dbe9fb; border-radius: 18px; background: rgba(255,255,255,.7); color: #6683a6; }
.welcome-note strong { color: #243c5b; font-size: 18px; }
.section-heading { margin: 0 2px 18px; }
.section-heading h2 { margin: 0 0 7px; color: #25344a; font-size: 20px; }
.section-heading p { font-size: 13px; }
@media (max-width: 700px) { .welcome-card { padding: 26px 22px; } .welcome-note { display: none; } }
</style>
