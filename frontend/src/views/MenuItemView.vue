<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])
const rows = ref([])
const recipes = ref({})        // menuItemId -> [{ beanOrigin, grams }]
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', price: '', category: '意式', status: '在售' })
const CATEGORIES = ['意式', '手冲', '非咖啡']

const recipeDialog = ref(false)
const recipeMenu = ref(null)   // 正在维护配方的饮品
const recipeLines = ref([])
const origins = ref([])
const savingRecipe = ref(false)

const statusType = (s) => (s === '在售' ? 'success' : 'info')

function recipeText(mi) {
  const lines = recipes.value[mi.id]
  if (!lines || !lines.length) return '未配置'
  return lines.map((l) => `${l.beanOrigin} ${Number(l.grams)}g`).join('、')
}

async function load() {
  rows.value = await api.listMenuItems()
  const map = {}
  await Promise.all(rows.value.map(async (m) => { map[m.id] = await api.getMenuRecipe(m.id) }))
  recipes.value = map
}
function openCreate() {
  editing.value = false
  Object.assign(form, { id: null, name: '', price: '', category: '意式', status: '在售' })
  dialog.value = true
}
function openEdit(r) {
  editing.value = true
  Object.assign(form, { id: r.id, name: r.name, price: r.price, category: r.category, status: r.status })
  dialog.value = true
}
async function submit() {
  try {
    if (editing.value) {
      await api.updateMenuItem(form.id, { ...form, price: Number(form.price) })
    } else {
      await api.createMenuItem({ ...form, price: Number(form.price) })
    }
    dialog.value = false
    await load()
    emit('changed')
    ElMessage.success('已保存')
  } catch (e) { ElMessage.error(e.message) }
}

// ---------------- 配方维护 ----------------

async function openRecipe(r) {
  recipeMenu.value = r
  if (!origins.value.length) origins.value = await api.listBeanOrigins()
  const lines = await api.getMenuRecipe(r.id)
  recipeLines.value = lines.map((l) => ({ beanOrigin: l.beanOrigin, grams: Number(l.grams) }))
  recipeDialog.value = true
}
function addLine() {
  recipeLines.value.push({ beanOrigin: origins.value[0] || '', grams: 15 })
}
function removeLine(i) {
  recipeLines.value.splice(i, 1)
}
async function submitRecipe() {
  savingRecipe.value = true
  try {
    const saved = await api.saveMenuRecipe(
      recipeMenu.value.id,
      recipeLines.value.map((l) => ({ beanOrigin: l.beanOrigin, grams: Number(l.grams) }))
    )
    recipes.value[recipeMenu.value.id] = saved
    recipeDialog.value = false
    ElMessage.success('配方已保存（只影响之后完成的账单）')
  } catch (e) { ElMessage.error(e.message) } finally { savingRecipe.value = false }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>饮品菜单</h2>
      <el-button type="primary" @click="openCreate">新增饮品</el-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="name" label="饮品名" min-width="130" />
      <el-table-column prop="category" label="类别" width="90" align="center" />
      <el-table-column prop="price" label="价格(元)" width="100" align="right">
        <template #default="{ row }">{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="配方（豆种 / 每杯克数）" min-width="220">
        <template #default="{ row }">
          <span :class="{ 'no-recipe': !recipes[row.id] || !recipes[row.id].length }">{{ recipeText(row) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="warning" plain @click="openRecipe(row)">配方</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="editing ? '编辑饮品' : '新增饮品'" width="420px">
      <el-form label-width="80px">
        <el-form-item label="饮品名"><el-input v-model="form.name" placeholder="如 拿铁" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="form.category" style="width:100%">
            <el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格(元)"><el-input v-model="form.price" type="number" placeholder="大于 0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width:100%">
            <el-option label="在售" value="在售" />
            <el-option label="下架" value="下架" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog=false">取消</el-button>
        <el-button type="primary" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 配方维护：整体替换；历史账单消耗以完成时的快照为准，不会被重算 -->
    <el-dialog v-model="recipeDialog" :title="`配方维护 · ${recipeMenu ? recipeMenu.name : ''}`" width="520px">
      <p class="recipe-tip">
        每行 = 每杯需要的豆种与克数；完成制作时按当时保存的配方扣减批次库存，
        修改配方不会重算已完成的账单。
      </p>
      <div v-for="(line, i) in recipeLines" :key="i" class="recipe-line">
        <el-select v-model="line.beanOrigin" placeholder="选择豆种" style="flex:1">
          <el-option v-for="o in origins" :key="o" :label="o" :value="o" />
        </el-select>
        <el-input-number v-model="line.grams" :min="0.1" :precision="1" :step="1" controls-position="right" style="width:140px" />
        <span class="unit">克/杯</span>
        <el-button size="small" type="danger" plain @click="removeLine(i)">删除</el-button>
      </div>
      <div v-if="!recipeLines.length" class="no-recipe-tip">未配置配方：该饮品完成制作时不消耗咖啡豆</div>
      <el-button size="small" @click="addLine">+ 添加一行</el-button>
      <template #footer>
        <el-button @click="recipeDialog=false">取消</el-button>
        <el-button type="primary" :loading="savingRecipe" @click="submitRecipe">保存配方</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h2 { margin: 0; font-size: 18px; }
.no-recipe { color: var(--muted); }
.recipe-tip { margin: 0 0 12px; font-size: 13px; color: var(--muted); line-height: 1.6; }
.recipe-line { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.unit { color: var(--muted); font-size: 13px; }
.no-recipe-tip { color: var(--muted); font-size: 13px; margin-bottom: 10px; }
</style>
