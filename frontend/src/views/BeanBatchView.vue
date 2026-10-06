<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])
const rows = ref([])
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, batchNo: '', origin: '', roastLevel: '中', weightKg: '' })
const ROAST_LEVELS = ['浅', '中', '深']

const consumeDialog = ref(false)
const consumeBatch = ref(null)
const consumptions = ref([])

const statusType = (s) => ({ '待烘焙': 'info', '烘焙中': 'warning', '已入库': 'success', '已用完': 'default' }[s] || 'info')

async function load() {
  rows.value = await api.listBeanBatches()
}
function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, batchNo: '', origin: '', roastLevel: '中', weightKg: '' })
  dialog.value = true
}
function openEdit(r) {
  editing.value = true
  Object.assign(form, { id: r.id, batchNo: r.batchNo, origin: r.origin, roastLevel: r.roastLevel, weightKg: r.weightKg })
  dialog.value = true
}
async function submit() {
  try {
    if (editing.value) {
      await api.updateBeanBatch(form.id, { ...form })
    } else {
      await api.createBeanBatch({ ...form, weightKg: Number(form.weightKg) })
    }
    dialog.value = false
    await load()
    emit('changed')
    ElMessage.success('已保存')
  } catch (e) { ElMessage.error(e.message) }
}
async function action(r, fn, label) {
  try { await fn(r.id); await load(); emit('changed'); ElMessage.success(label) }
  catch (e) { ElMessage.error(e.message) }
}

/** 批次追溯：该批次被哪些账单消耗过 */
async function openConsumptions(r) {
  consumeBatch.value = r
  consumptions.value = await api.listBatchConsumptions(r.id)
  consumeDialog.value = true
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>咖啡豆批次</h2>
      <el-button type="primary" @click="openCreate">新增批次</el-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="batchNo" label="批次号" width="130" />
      <el-table-column prop="origin" label="产地（豆种）" min-width="150" />
      <el-table-column prop="roastLevel" label="烘焙度" width="80" align="center" />
      <el-table-column prop="weightKg" label="重量(kg)" width="90" align="right" />
      <el-table-column label="余量(g)" width="100" align="right">
        <template #default="{ row }">{{ Number(row.remainingG).toFixed(1) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" min-width="320">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="row.status==='待烘焙'" size="small" type="warning" @click="action(row, api.roastBeanBatch, '已送入烘焙')">送入烘焙</el-button>
          <el-button v-if="row.status==='烘焙中'" size="small" type="success" @click="action(row, api.finishRoastBeanBatch, '已入库')">标记入库</el-button>
          <el-button v-if="row.status==='已入库'" size="small" @click="action(row, api.depleteBeanBatch, '已用完')">标记用完</el-button>
          <el-button size="small" type="primary" plain @click="openConsumptions(row)">消耗记录</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑批次' : '新增批次'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="批次号"><el-input v-model="form.batchNo" placeholder="如 BB-20260901" /></el-form-item>
        <el-form-item label="产地"><el-input v-model="form.origin" placeholder="如 埃塞俄比亚 耶加雪菲" /></el-form-item>
        <el-form-item label="烘焙度">
          <el-select v-model="form.roastLevel" style="width:100%">
            <el-option v-for="l in ROAST_LEVELS" :key="l" :label="l" :value="l" />
          </el-select>
        </el-form-item>
        <el-form-item label="重量(kg)"><el-input v-model="form.weightKg" type="number" placeholder="大于 0" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批次追溯：被哪些账单消耗过 -->
    <el-dialog
      v-model="consumeDialog"
      :title="`消耗记录 · ${consumeBatch ? consumeBatch.batchNo + '（' + consumeBatch.origin + '）' : ''}`"
      width="720px"
    >
      <el-table :data="consumptions" border size="small" max-height="420">
        <el-table-column prop="ticketId" label="账单号" width="90" align="center" />
        <el-table-column prop="menuName" label="饮品" min-width="120" />
        <el-table-column label="扣减克数" width="100" align="right">
          <template #default="{ row }">{{ Number(row.grams) }} g</template>
        </el-table-column>
        <el-table-column prop="operator" label="操作者" width="100" align="center" />
        <el-table-column prop="createdAt" label="消耗时间" width="170" />
      </el-table>
      <div v-if="!consumptions.length" class="empty-tip">该批次暂无消耗记录</div>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h2 { margin: 0; font-size: 18px; }
.empty-tip { color: var(--muted); text-align: center; padding: 20px 0; }
</style>
