<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const emit = defineEmits(['changed'])
const rows = ref([])
const dialog = ref(false)
const editing = ref(false)
const form = reactive({ id: null, name: '', price: '', category: '意式', status: '在售' })
const CATEGORIES = ['意式', '手冲', '非咖啡']

const statusType = (s) => (s === '在售' ? 'success' : 'info')

async function load() {
  rows.value = await api.listMenuItems()
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
onMounted(load)
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>饮品菜单</h2>
      <el-button type="primary" @click="openCreate">新增饮品</el-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="name" label="饮品名" min-width="140" />
      <el-table-column prop="category" label="类别" width="100" align="center" />
      <el-table-column prop="price" label="价格(元)" width="110" align="right">
        <template #default="{ row }">{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center">
        <template #default="{ row }"><el-button size="small" @click="openEdit(row)">编辑</el-button></template>
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
  </div>
</template>

<style scoped>
.page-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 14px; }
h2 { margin: 0; font-size: 18px; }
</style>
