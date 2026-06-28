<template>
  <div class="equipment-management">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>设备管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="success" @click="handleAddEquipment">
              <el-icon><Plus /></el-icon>
              新增设备
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="设备名称">
            <el-input v-model="filterForm.name" placeholder="请输入设备名称" clearable />
          </el-form-item>
          <el-form-item label="设备类型">
            <el-select v-model="filterForm.type" placeholder="请选择设备类型" clearable>
              <el-option label="测量仪器" value="measurement" />
              <el-option label="实验装置" value="experiment" />
              <el-option label="计算机设备" value="computer" />
              <el-option label="其他设备" value="other" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="请选择状态" clearable>
              <el-option label="正常" value="normal" />
              <el-option label="故障" value="fault" />
              <el-option label="维护中" value="maintenance" />
              <el-option label="报废" value="scrapped" />
            </el-select>
          </el-form-item>
          <el-form-item label="所属实验室">
            <el-select v-model="filterForm.laboratory" placeholder="请选择实验室" clearable>
              <el-option label="力学实验室A" value="力学实验室A" />
              <el-option label="电学实验室B" value="电学实验室B" />
              <el-option label="光学实验室C" value="光学实验室C" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleFilter">筛选</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 设备统计 -->
      <div class="stats-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-number">{{ stats.total }}</div>
              <div class="stat-label">设备总数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-number">{{ stats.normal }}</div>
              <div class="stat-label">正常设备</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-number">{{ stats.fault }}</div>
              <div class="stat-label">故障设备</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-item">
              <div class="stat-number">{{ stats.maintenance }}</div>
              <div class="stat-label">维护中</div>
            </div>
          </el-col>
        </el-row>
      </div>

      <!-- 设备列表 -->
      <el-table :data="equipmentList" style="width: 100%" v-loading="loading">
        <el-table-column prop="name" label="设备名称" min-width="150" />
        <el-table-column prop="model" label="型号" width="120" />
        <el-table-column prop="type" label="设备类型" width="120">
          <template #default="scope">
            <el-tag :type="getTypeTagType(scope.row.type)">
              {{ scope.row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="laboratory" label="所属实验室" width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="purchaseDate" label="购买日期" width="120" />
        <el-table-column prop="lastMaintenance" label="最后维护" width="120" />
        <el-table-column prop="nextMaintenance" label="下次维护" width="120" />
        <el-table-column prop="manager" label="负责人" width="100" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleViewEquipment(scope.row)"
            >
              查看
            </el-button>
            <el-button 
              type="warning" 
              size="small" 
              @click="handleEditEquipment(scope.row)"
            >
              编辑
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="handleDeleteEquipment(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 设备详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="设备详情"
      width="800px"
    >
      <div v-if="currentEquipment" class="equipment-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="设备名称">{{ currentEquipment.name }}</el-descriptions-item>
          <el-descriptions-item label="型号">{{ currentEquipment.model }}</el-descriptions-item>
          <el-descriptions-item label="设备类型">{{ currentEquipment.type }}</el-descriptions-item>
          <el-descriptions-item label="所属实验室">{{ currentEquipment.laboratory }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentEquipment.status)">
              {{ currentEquipment.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="负责人">{{ currentEquipment.manager }}</el-descriptions-item>
          <el-descriptions-item label="购买日期">{{ currentEquipment.purchaseDate }}</el-descriptions-item>
          <el-descriptions-item label="购买价格">{{ currentEquipment.price }}元</el-descriptions-item>
          <el-descriptions-item label="最后维护">{{ currentEquipment.lastMaintenance }}</el-descriptions-item>
          <el-descriptions-item label="下次维护">{{ currentEquipment.nextMaintenance }}</el-descriptions-item>
          <el-descriptions-item label="设备描述" :span="2">{{ currentEquipment.description }}</el-descriptions-item>
        </el-descriptions>

        <!-- 维护记录 -->
        <div class="maintenance-records" style="margin-top: 20px;">
          <h4>维护记录</h4>
          <el-table :data="currentEquipment.maintenanceRecords" style="width: 100%">
            <el-table-column prop="date" label="维护日期" />
            <el-table-column prop="type" label="维护类型" />
            <el-table-column prop="operator" label="操作人员" />
            <el-table-column prop="description" label="维护内容" />
            <el-table-column prop="cost" label="维护费用" />
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 新增/编辑设备对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="isEdit ? '编辑设备' : '新增设备'"
      width="600px"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入设备名称" />
        </el-form-item>
        
        <el-form-item label="型号" prop="model">
          <el-input v-model="editForm.model" placeholder="请输入设备型号" />
        </el-form-item>
        
        <el-form-item label="设备类型" prop="type">
          <el-select v-model="editForm.type" placeholder="请选择设备类型" style="width: 100%">
            <el-option label="测量仪器" value="测量仪器" />
            <el-option label="实验装置" value="实验装置" />
            <el-option label="计算机设备" value="计算机设备" />
            <el-option label="其他设备" value="其他设备" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="所属实验室" prop="laboratory">
          <el-select v-model="editForm.laboratory" placeholder="请选择实验室" style="width: 100%">
            <el-option label="力学实验室A" value="力学实验室A" />
            <el-option label="电学实验室B" value="电学实验室B" />
            <el-option label="光学实验室C" value="光学实验室C" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="正常" value="正常" />
            <el-option label="故障" value="故障" />
            <el-option label="维护中" value="维护中" />
            <el-option label="报废" value="报废" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="购买日期" prop="purchaseDate">
          <el-date-picker
            v-model="editForm.purchaseDate"
            type="date"
            placeholder="选择购买日期"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="购买价格" prop="price">
          <el-input-number v-model="editForm.price" :min="0" :precision="2" style="width: 100%" />
        </el-form-item>
        
        <el-form-item label="负责人" prop="manager">
          <el-input v-model="editForm.manager" placeholder="请输入负责人姓名" />
        </el-form-item>
        
        <el-form-item label="设备描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入设备描述"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveEquipment">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const detailDialogVisible = ref(false)
const editDialogVisible = ref(false)
const currentEquipment = ref(null)
const isEdit = ref(false)
const editFormRef = ref()

// 筛选表单
const filterForm = reactive({
  name: '',
  type: '',
  status: '',
  laboratory: ''
})

// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 统计数据
const stats = ref({
  total: 0,
  normal: 0,
  fault: 0,
  maintenance: 0
})

// 编辑表单
const editForm = reactive({
  id: null,
  name: '',
  model: '',
  type: '',
  laboratory: '',
  status: '正常',
  purchaseDate: '',
  price: 0,
  manager: '',
  description: ''
})

// 表单验证规则
const editRules = {
  name: [
    { required: true, message: '请输入设备名称', trigger: 'blur' }
  ],
  model: [
    { required: true, message: '请输入设备型号', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择设备类型', trigger: 'change' }
  ],
  laboratory: [
    { required: true, message: '请选择所属实验室', trigger: 'change' }
  ],
  status: [
    { required: true, message: '请选择设备状态', trigger: 'change' }
  ],
  purchaseDate: [
    { required: true, message: '请选择购买日期', trigger: 'change' }
  ],
  price: [
    { required: true, message: '请输入购买价格', trigger: 'blur' }
  ],
  manager: [
    { required: true, message: '请输入负责人姓名', trigger: 'blur' }
  ]
}

// 设备列表数据
const equipmentList = ref([
  {
    id: 1,
    name: '单摆实验装置',
    model: 'MP-001',
    type: '实验装置',
    laboratory: '力学实验室A',
    status: '正常',
    purchaseDate: '2023-01-15',
    lastMaintenance: '2024-01-10',
    nextMaintenance: '2024-04-10',
    manager: '张老师',
    price: 5000,
    description: '用于测量重力加速度的单摆实验装置，精度高，操作简便。',
    maintenanceRecords: [
      { date: '2024-01-10', type: '定期维护', operator: '李师傅', description: '清洁设备，检查零件', cost: 200 },
      { date: '2023-10-15', type: '故障维修', operator: '王师傅', description: '更换摆线', cost: 150 }
    ]
  },
  {
    id: 2,
    name: '万用表',
    model: 'MM-001',
    type: '测量仪器',
    laboratory: '电学实验室B',
    status: '正常',
    purchaseDate: '2023-03-20',
    lastMaintenance: '2024-01-12',
    nextMaintenance: '2024-04-12',
    manager: '李老师',
    price: 800,
    description: '数字万用表，测量精度高，功能齐全。',
    maintenanceRecords: [
      { date: '2024-01-12', type: '定期维护', operator: '李师傅', description: '校准测量精度', cost: 100 }
    ]
  },
  {
    id: 3,
    name: '示波器',
    model: 'OS-002',
    type: '测量仪器',
    laboratory: '电学实验室B',
    status: '维护中',
    purchaseDate: '2023-02-10',
    lastMaintenance: '2024-01-09',
    nextMaintenance: '2024-04-09',
    manager: '李老师',
    price: 15000,
    description: '数字示波器，带宽100MHz，采样率高。',
    maintenanceRecords: [
      { date: '2024-01-09', type: '故障维修', operator: '王师傅', description: '更换显示屏', cost: 2000 }
    ]
  },
  {
    id: 4,
    name: '分光计',
    model: 'SP-001',
    type: '测量仪器',
    laboratory: '光学实验室C',
    status: '正常',
    purchaseDate: '2023-05-15',
    lastMaintenance: '2024-01-11',
    nextMaintenance: '2024-04-11',
    manager: '王老师',
    price: 12000,
    description: '精密分光计，用于光谱分析实验。',
    maintenanceRecords: [
      { date: '2024-01-11', type: '定期维护', operator: '张师傅', description: '清洁光学元件', cost: 300 }
    ]
  }
])

// 获取类型标签类型
const getTypeTagType = (type) => {
  const typeMap = {
    '测量仪器': 'primary',
    '实验装置': 'success',
    '计算机设备': 'warning',
    '其他设备': 'info'
  }
  return typeMap[type] || 'info'
}

// 获取状态标签类型
const getStatusTagType = (status) => {
  const statusMap = {
    '正常': 'success',
    '故障': 'danger',
    '维护中': 'warning',
    '报废': 'info'
  }
  return statusMap[status] || 'info'
}

// 计算统计数据
const calculateStats = () => {
  const total = equipmentList.value.length
  const normal = equipmentList.value.filter(e => e.status === '正常').length
  const fault = equipmentList.value.filter(e => e.status === '故障').length
  const maintenance = equipmentList.value.filter(e => e.status === '维护中').length
  
  stats.value = { total, normal, fault, maintenance }
}

// 筛选
const handleFilter = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('筛选完成')
  }, 1000)
}

// 重置
const handleReset = () => {
  filterForm.name = ''
  filterForm.type = ''
  filterForm.status = ''
  filterForm.laboratory = ''
  handleFilter()
}

// 刷新
const handleRefresh = () => {
  loading.value = true
  setTimeout(() => {
    loading.value = false
    ElMessage.success('刷新完成')
  }, 1000)
}

// 新增设备
const handleAddEquipment = () => {
  isEdit.value = false
  Object.assign(editForm, {
    id: null,
    name: '',
    model: '',
    type: '',
    laboratory: '',
    status: '正常',
    purchaseDate: '',
    price: 0,
    manager: '',
    description: ''
  })
  editDialogVisible.value = true
}

// 查看设备
const handleViewEquipment = (row) => {
  currentEquipment.value = row
  detailDialogVisible.value = true
}

// 编辑设备
const handleEditEquipment = (row) => {
  isEdit.value = true
  Object.assign(editForm, row)
  editDialogVisible.value = true
}

// 删除设备
const handleDeleteEquipment = (row) => {
  ElMessageBox.confirm(
    `确定要删除设备"${row.name}"吗？此操作不可恢复！`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const index = equipmentList.value.findIndex(e => e.id === row.id)
    if (index > -1) {
      equipmentList.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  })
}

// 保存设备
const handleSaveEquipment = async () => {
  if (!editFormRef.value) return
  
  try {
    await editFormRef.value.validate()
    
    if (isEdit.value) {
      // 编辑
      const equipment = equipmentList.value.find(e => e.id === editForm.id)
      if (equipment) {
        Object.assign(equipment, editForm)
      }
      ElMessage.success('编辑成功')
    } else {
      // 新增
      const newEquipment = {
        ...editForm,
        id: Date.now(),
        lastMaintenance: '-',
        nextMaintenance: '-',
        maintenanceRecords: []
      }
      equipmentList.value.unshift(newEquipment)
      ElMessage.success('新增成功')
    }
    
    editDialogVisible.value = false
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

// 分页大小改变
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  handleFilter()
}

// 当前页改变
const handleCurrentChange = (current) => {
  pagination.current = current
  handleFilter()
}

onMounted(() => {
  pagination.total = equipmentList.value.length
  calculateStats()
})
</script>

<style scoped>
.equipment-management {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.filter-section {
  margin-bottom: 20px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.stats-section {
  margin-bottom: 20px;
}

.stat-item {
  text-align: center;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 8px;
}

.stat-number {
  font-size: 24px;
  font-weight: bold;
  color: #409EFF;
  margin-bottom: 8px;
}

.stat-label {
  color: #606266;
  font-size: 14px;
}

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.equipment-detail {
  padding: 20px 0;
}

.maintenance-records h4 {
  margin: 20px 0 10px 0;
  color: #303133;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style> 