<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const tables = ref([])
const menuItems = ref([])
const selected = ref(null)   // 选中的桌台对象
const current = ref(null)    // 当前账单明细 { ticket, items }

const tableType = (s) => ({ '空闲': 'success', '占用': 'warning', '清洁中': 'info' }[s] || 'info')
const ticketType = (s) => ({ '制作中': 'warning', '已完成': 'success', '已结账': 'info' }[s] || 'info')

const total = computed(() => (current.value ? Number(current.value.ticket.total).toFixed(2) : '0.00'))

async function loadTables() { tables.value = await api.listTableInfos() }
async function loadMenu() { menuItems.value = await api.listMenuItems('在售') }

async function selectTable(t) {
  selected.value = t
  current.value = null
  if (t.status === '空闲') return
  if (t.status === '清洁中') { ElMessage.info('该桌台清洁中，暂不可点单'); return }
  // 占用：查找或创建活跃账单
  const all = await api.listOrderTickets()
  const active = all.find((x) => x.tableId === t.id && (x.status === '制作中' || x.status === '已完成'))
  if (active) {
    current.value = await api.getOrderTicket(active.id)
  } else {
    current.value = await api.createOrderTicket({ tableId: t.id, items: [] })
    emit('changed')
  }
}

async function openSelected() {
  if (!selected.value) { ElMessage.warning('请先选择桌台'); return }
  try {
    const updated = await api.openTable(selected.value.id)
    await loadTables()
    await selectTable(updated)
    ElMessage.success('已开台')
  } catch (e) { ElMessage.error(e.message) }
}

async function addItem(mi) {
  if (!current.value) { ElMessage.warning('请先开台'); return }
  if (current.value.ticket.status !== '制作中') { ElMessage.warning('当前账单不可加单（需为制作中）'); return }
  try {
    current.value = await api.addOrderItem(current.value.ticket.id, { menuItemId: mi.id, qty: 1 })
  } catch (e) { ElMessage.error(e.message) }
}

async function refresh() {
  if (current.value) current.value = await api.getOrderTicket(current.value.ticket.id)
}
async function finish() {
  try {
    current.value = await api.finishOrderTicket(current.value.ticket.id)
    emit('changed'); ElMessage.success('已标记完成制作')
  } catch (e) { ElMessage.error(e.message) }
}
async function settle() {
  try {
    await api.settleOrderTicket(current.value.ticket.id)
    await loadTables()
    current.value = null
    emit('changed'); ElMessage.success('已结账，桌台已释放')
  } catch (e) { ElMessage.error(e.message) }
}

onMounted(async () => { await loadTables(); await loadMenu() })
</script>

<template>
  <div class="pos">
    <h2 class="pos-title">收银出单 · POS 一条式</h2>
    <div class="pos-grid">
      <!-- 左：饮品菜单 -->
      <section class="col col-menu">
        <div class="col-head">饮品菜单（在售）</div>
        <div class="menu-grid">
          <button v-for="mi in menuItems" :key="mi.id" class="menu-card" @click="addItem(mi)">
            <span class="mc-name">{{ mi.name }}</span>
            <span class="mc-cat">{{ mi.category }}</span>
            <span class="mc-price">¥{{ Number(mi.price).toFixed(2) }}</span>
          </button>
          <div v-if="!menuItems.length" class="empty">暂无在售饮品</div>
        </div>
      </section>

      <!-- 中：点单流水 -->
      <section class="col col-flow">
        <div class="col-head">点单流水</div>
        <el-table :data="current ? current.items : []" border height="360">
          <el-table-column prop="menuName" label="饮品" min-width="120" />
          <el-table-column prop="price" label="单价" width="90" align="right">
            <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column label="小计" width="90" align="right">
            <template #default="{ row }">¥{{ Number(row.subtotal).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
        <div v-if="!current" class="flow-empty">请先在右侧选择并开台</div>
      </section>

      <!-- 右：桌台与结算 -->
      <section class="col col-pay">
        <div class="col-head">桌台与结算</div>
        <div class="table-grid">
          <button
            v-for="t in tables" :key="t.id"
            class="table-card" :class="{ on: selected && selected.id === t.id }"
            @click="selectTable(t)"
          >
            <span class="tc-no">{{ t.tableNo }}</span>
            <span class="tc-seats">{{ t.seats }} 座</span>
            <el-tag size="small" :type="tableType(t.status)">{{ t.status }}</el-tag>
          </button>
        </div>

        <div class="pay-box" v-if="selected">
          <div class="pay-row">
            <span>当前桌台</span><b>{{ selected.tableNo }}（{{ selected.status }}）</b>
          </div>
          <el-button v-if="selected.status === '空闲'" type="primary" @click="openSelected">开台</el-button>

          <template v-if="current">
            <div class="pay-row">
              <span>账单状态</span>
              <el-tag size="small" :type="ticketType(current.ticket.status)">{{ current.ticket.status }}</el-tag>
            </div>
            <div class="pay-total">
              <span>合计</span><b>¥{{ total }}</b>
            </div>
            <el-button v-if="current.ticket.status === '制作中'" type="success" @click="finish">完成制作</el-button>
            <el-button v-if="current.ticket.status === '已完成'" type="primary" @click="settle">结账</el-button>
          </template>
        </div>
        <div v-else class="pay-box hint">请选择上方桌台</div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.pos-title { margin: 0 0 12px; font-size: 18px; }
.pos-grid { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 14px; align-items: start; }
.col { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px; min-height: 420px; }
.col-head { font-weight: 700; margin-bottom: 10px; color: var(--primary-deep); }

.menu-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.menu-card {
  display: flex; flex-direction: column; gap: 4px; align-items: flex-start;
  border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px;
  background: var(--primary-wash); cursor: pointer; transition: all .15s;
}
.menu-card:hover { border-color: var(--primary); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(109,76,65,.18); }
.mc-name { font-weight: 700; color: var(--ink); }
.mc-cat { font-size: 12px; color: var(--muted); }
.mc-price { color: var(--primary-deep); font-weight: 700; }

.empty { grid-column: 1 / -1; color: var(--muted); text-align: center; padding: 30px 0; }

.flow-empty { color: var(--muted); text-align: center; padding: 40px 0; }

.table-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-bottom: 12px; }
.table-card {
  display: flex; flex-direction: column; gap: 4px; align-items: center;
  border: 1px solid var(--line); border-radius: 10px; padding: 10px 6px;
  background: #fff; cursor: pointer; transition: all .15s;
}
.table-card.on { border-color: var(--primary); background: var(--primary-wash); }
.tc-no { font-weight: 700; font-size: 16px; }
.tc-seats { font-size: 12px; color: var(--muted); }

.pay-box { border-top: 1px dashed var(--line); padding-top: 12px; display: flex; flex-direction: column; gap: 10px; }
.pay-box.hint { color: var(--muted); }
.pay-row { display: flex; align-items: center; justify-content: space-between; }
.pay-total { display: flex; align-items: center; justify-content: space-between; font-size: 16px; }
.pay-total b { color: var(--primary-deep); font-size: 22px; }
</style>
