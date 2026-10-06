<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])

const tables = ref([])
const menuItems = ref([])
const availability = ref({})   // menuItemId -> { ok, shortages }
const selected = ref(null)     // 选中的桌台对象
const current = ref(null)      // 当前账单明细 { ticket, items, consumptions }

const finishDialog = ref(false)
const operator = ref(localStorage.getItem('cafe.operator') || '')
const finishing = ref(false)

const tableType = (s) => ({ '空闲': 'success', '占用': 'warning', '清洁中': 'info' }[s] || 'info')
const ticketType = (s) => ({ '制作中': 'warning', '已完成': 'success', '已结账': 'info' }[s] || 'info')

const total = computed(() => (current.value ? Number(current.value.ticket.total).toFixed(2) : '0.00'))

function stockOf(mi) {
  return availability.value[mi.id] || { ok: true, shortages: [] }
}
function stockTip(mi) {
  const s = stockOf(mi)
  if (s.ok) return ''
  return s.shortages
    .map((x) => `豆种「${x.beanOrigin}」需要 ${Number(x.needG)} 克，当前可用 ${Number(x.availableG)} 克`)
    .join('；')
}

async function loadTables() { tables.value = await api.listTableInfos() }
async function loadMenu() {
  const [items, avail] = await Promise.all([api.listMenuItems('在售'), api.listMenuAvailability()])
  menuItems.value = items
  const map = {}
  for (const a of avail) map[a.menuItemId] = a
  availability.value = map
}

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
  if (!stockOf(mi).ok) { ElMessage.warning(`「${mi.name}」库存不足：${stockTip(mi)}`); return }
  try {
    current.value = await api.addOrderItem(current.value.ticket.id, { menuItemId: mi.id, qty: 1 })
  } catch (e) { ElMessage.error(e.message) }
}

async function refresh() {
  if (current.value) current.value = await api.getOrderTicket(current.value.ticket.id)
}

function openFinish() {
  if (!current.value) return
  finishDialog.value = true
}

async function confirmFinish() {
  if (!operator.value.trim()) { ElMessage.warning('请填写操作者（收银员）'); return }
  finishing.value = true
  try {
    const detail = await api.finishOrderTicket(current.value.ticket.id, operator.value.trim())
    current.value = detail
    localStorage.setItem('cafe.operator', operator.value.trim())
    finishDialog.value = false
    emit('changed')
    await loadMenu()   // 库存已扣减，刷新缺料标记
    // 本次消耗一目了然：批次 / 克数 / 操作者 / 时间
    const lines = detail.consumptions.length
      ? detail.consumptions.map((c) =>
          `<div>批次 <b>${c.batchNo}</b>（${c.beanOrigin}）为「${c.menuName}」扣减 <b>${Number(c.grams)} 克</b></div>`
        ).join('')
      : '<div>本单饮品无配方，未产生咖啡豆消耗</div>'
    ElMessageBox.alert(
      `${lines}<div style="margin-top:8px;color:#909399">操作者：${detail.ticket.finishedBy}　时间：${detail.ticket.finishedAt}</div>`,
      '制作完成 · 本次咖啡豆消耗',
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
    )
  } catch (e) {
    // 失败原因（如某豆种不足）直接展示；账单保持「制作中」，可继续处理
    ElMessage.error(e.message)
    await refresh()
  } finally {
    finishing.value = false
  }
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
          <button
            v-for="mi in menuItems" :key="mi.id"
            class="menu-card" :class="{ disabled: !stockOf(mi).ok }"
            @click="addItem(mi)"
          >
            <span class="mc-name">{{ mi.name }}</span>
            <span class="mc-cat">{{ mi.category }}</span>
            <span class="mc-price">¥{{ Number(mi.price).toFixed(2) }}</span>
            <el-tooltip v-if="!stockOf(mi).ok" :content="stockTip(mi)" placement="top">
              <span class="mc-stock">库存不足</span>
            </el-tooltip>
          </button>
          <div v-if="!menuItems.length" class="empty">暂无在售饮品</div>
        </div>
      </section>

      <!-- 中：点单流水 + 消耗记录 -->
      <section class="col col-flow">
        <div class="col-head">点单流水</div>
        <el-table :data="current ? current.items : []" border height="300">
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

        <template v-if="current && current.consumptions && current.consumptions.length">
          <div class="col-head consume-head">咖啡豆消耗（完成时按配方生成）</div>
          <el-table :data="current.consumptions" border size="small" max-height="180">
            <el-table-column prop="batchNo" label="批次" width="110" />
            <el-table-column prop="menuName" label="饮品" min-width="100" />
            <el-table-column label="克数" width="80" align="right">
              <template #default="{ row }">{{ Number(row.grams) }} g</template>
            </el-table-column>
            <el-table-column prop="operator" label="操作者" width="80" align="center" />
            <el-table-column prop="createdAt" label="时间" width="150" />
          </el-table>
        </template>
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
            <div v-if="current.ticket.finishedBy" class="pay-row">
              <span>完成信息</span>
              <b>{{ current.ticket.finishedBy }} · {{ current.ticket.finishedAt }}</b>
            </div>
            <div class="pay-total">
              <span>合计</span><b>¥{{ total }}</b>
            </div>
            <el-button v-if="current.ticket.status === '制作中'" type="success" @click="openFinish">完成制作</el-button>
            <el-button v-if="current.ticket.status === '已完成'" type="primary" @click="settle">结账</el-button>
          </template>
        </div>
        <div v-else class="pay-box hint">请选择上方桌台</div>
      </section>
    </div>

    <!-- 完成制作：录入操作者，提交后按当时配方扣减批次库存 -->
    <el-dialog v-model="finishDialog" title="完成制作" width="420px">
      <p class="finish-tip">
        提交后将按当前配方扣减咖啡豆批次余量（按最早入库顺序自动分配，可跨批次）；
        若任一豆种库存不足，整单不会扣减，账单保持「制作中」。
      </p>
      <el-form label-width="80px">
        <el-form-item label="操作者">
          <el-input v-model="operator" placeholder="收银员姓名，如 小王" @keyup.enter="confirmFinish" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishDialog=false">取消</el-button>
        <el-button type="success" :loading="finishing" :disabled="finishing" @click="confirmFinish">
          确认完成并扣减库存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pos-title { margin: 0 0 12px; font-size: 18px; }
.pos-grid { display: grid; grid-template-columns: 1.2fr 1fr 1fr; gap: 14px; align-items: start; }
.col { background: #fff; border: 1px solid var(--line); border-radius: 12px; padding: 12px; min-height: 420px; }
.col-head { font-weight: 700; margin-bottom: 10px; color: var(--primary-deep); }
.consume-head { margin-top: 14px; }

.menu-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
.menu-card {
  display: flex; flex-direction: column; gap: 4px; align-items: flex-start;
  border: 1px solid var(--line); border-radius: 10px; padding: 10px 12px;
  background: var(--primary-wash); cursor: pointer; transition: all .15s;
}
.menu-card:hover { border-color: var(--primary); transform: translateY(-2px); box-shadow: 0 4px 12px rgba(109,76,65,.18); }
.menu-card.disabled { opacity: .55; cursor: not-allowed; }
.menu-card.disabled:hover { border-color: var(--line); transform: none; box-shadow: none; }
.mc-name { font-weight: 700; color: var(--ink); }
.mc-cat { font-size: 12px; color: var(--muted); }
.mc-price { color: var(--primary-deep); font-weight: 700; }
.mc-stock {
  font-size: 12px; color: #fff; background: var(--el-color-danger);
  border-radius: 4px; padding: 1px 6px;
}

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

.finish-tip { margin: 0 0 12px; font-size: 13px; color: var(--muted); line-height: 1.6; }
</style>
