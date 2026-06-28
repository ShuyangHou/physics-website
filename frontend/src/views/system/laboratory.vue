<template>
  <div class="laboratory-management">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>实验室管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="success" @click="handleAddLaboratory">
              <el-icon><Plus /></el-icon>
              新增实验室
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="实验室名称">
            <el-input v-model="filterForm.name" placeholder="请输入实验室名称" clearable />
          </el-form-item>
          <el-form-item label="实验室类型">
            <el-select v-model="filterForm.type" placeholder="请选择实验室类型" clearable>
              <el-option label="力学实验室" value="mechanics" />
              <el-option label="电学实验室" value="electricity" />
              <el-option label="光学实验室" value="optics" />
              <el-option label="热学实验室" value="thermodynamics" />
              <el-option label="综合实验室" value="comprehensive" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="filterForm.status" placeholder="请选择状态" clearable>
              <el-option label="正常" value="normal" />
              <el-option label="维护中" value="maintenance" />
              <el-option label="停用" value="disabled" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleFilter">筛选</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 实验室列表 -->
      <el-table :data="laboratoryList" style="width: 100%" v-loading="loading">
        <el-table-column prop="name" label="实验室名称" min-width="150" />
        <el-table-column prop="type" label="实验室类型" width="120">
          <template #default="scope">
            <el-tag :type="getTypeTagType(scope.row.type)">
              {{ scope.row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="location" label="位置" width="120" />
        <el-table-column prop="capacity" label="容量" width="100">
          <template #default="scope">
            {{ scope.row.capacity }}人
          </template>
        </el-table-column>
        <el-table-column prop="equipmentCount" label="设备数量" width="100" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="manager" label="管理员" width="100" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleViewLaboratory(scope.row)"
            >
              查看
            </el-button>
            <el-button 
              type="warning" 
              size="small" 
              @click="handleEditLaboratory(scope.row)"
            >
              编辑
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="handleDeleteLaboratory(scope.row)"
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

    <!-- 实验室详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      title="实验室详情"
      width="800px"
    >
      <div v-if="currentLaboratory" class="laboratory-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="实验室名称">{{ currentLaboratory.name }}</el-descriptions-item>
          <el-descriptions-item label="实验室类型">{{ currentLaboratory.type }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ currentLaboratory.location }}</el-descriptions-item>
          <el-descriptions-item label="容量">{{ currentLaboratory.capacity }}人</el-descriptions-item>
          <el-descriptions-item label="设备数量">{{ currentLaboratory.equipmentCount }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTagType(currentLaboratory.status)">
              {{ currentLaboratory.status }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="管理员">{{ currentLaboratory.manager }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(currentLaboratory.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="描述" :span="2">{{ currentLaboratory.description }}</el-descriptions-item>
        </el-descriptions>

        <!-- 设备列表 -->
        <div class="equipment-list" style="margin-top: 20px;">
          <h4>设备列表</h4>
          <el-table :data="currentLaboratory.equipments" style="width: 100%">
            <el-table-column prop="name" label="设备名称" />
            <el-table-column prop="model" label="型号" />
            <el-table-column prop="status" label="状态">
              <template #default="scope">
                <el-tag :type="scope.row.status === 'normal' ? 'success' : 'danger'">
                  {{ scope.row.status === 'normal' ? '正常' : '故障' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后维护时间">
              <template #default="scope">{{ formatDate(scope.row.lastMaintenance) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>

    <!-- 新增/编辑实验室对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      :title="isEdit ? '编辑实验室' : '新增实验室'"
      width="600px"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="实验室名称" prop="name">
          <el-input v-model="editForm.name" placeholder="请输入实验室名称" />
        </el-form-item>
        
        <el-form-item label="实验室类型" prop="type">
          <el-select v-model="editForm.type" placeholder="请选择实验室类型" style="width: 100%">
            <el-option label="力学实验室" value="力学实验室" />
            <el-option label="电学实验室" value="电学实验室" />
            <el-option label="光学实验室" value="光学实验室" />
            <el-option label="热学实验室" value="热学实验室" />
            <el-option label="综合实验室" value="综合实验室" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="位置" prop="location">
          <el-input v-model="editForm.location" placeholder="请输入位置" />
        </el-form-item>
        
        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="editForm.capacity" :min="1" :max="100" style="width: 100%" />
        </el-form-item>
        
        <el-form-item label="管理员" prop="manager">
          <el-input v-model="editForm.manager" placeholder="请输入管理员姓名" />
        </el-form-item>
        
        <el-form-item label="状态" prop="status">
          <el-select v-model="editForm.status" placeholder="请选择状态" style="width: 100%">
            <el-option label="正常" value="正常" />
            <el-option label="维护中" value="维护中" />
            <el-option label="停用" value="停用" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="editForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入实验室描述"
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveLaboratory">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { formatDate, formatDateTime } from '@/utils/date'
import { ElMessage, ElMessageBox } from 'element-plus'

const loading = ref(false)
const detailDialogVisible = ref(false)
const editDialogVisible = ref(false)
const currentLaboratory = ref(null)
const isEdit = ref(false)
const editFormRef = ref()

// 筛选表单
const filterForm = reactive({
  name: '',
  type: '',
  status: ''
})

// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 编辑表单
const editForm = reactive({
  id: null,
  name: '',
  type: '',
  location: '',
  capacity: 30,
  manager: '',
  status: '正常',
  description: ''
})

// 表单验证规则
const editRules = {
  name: [
    { required: true, message: '请输入实验室名称', trigger: 'blur' }
  ],
  type: [
    { required: true, message: '请选择实验室类型', trigger: 'change' }
  ],
  location: [
    { required: true, message: '请输入位置', trigger: 'blur' }
  ],
  capacity: [
    { required: true, message: '请输入容量', trigger: 'blur' }
  ],
  manager: [
    { required: true, message: '请输入管理员姓名', trigger: 'blur' }
  ]
}

// 实验室列表数据
const laboratoryList = ref([
  {
    id: 1,
    name: '力学实验室A',
    type: '力学实验室',
    location: '教学楼A101',
    capacity: 30,
    equipmentCount: 15,
    status: '正常',
    manager: '张老师',
    description: '主要用于力学相关实验，配备完整的力学实验设备。',
    createTime: '2024-01-01 10:00:00',
    equipments: [
      { name: '单摆实验装置', model: 'MP-001', status: 'normal', lastMaintenance: '2024-01-10' },
      { name: '弹簧测力计', model: 'SP-002', status: 'normal', lastMaintenance: '2024-01-08' },
      { name: '斜面实验台', model: 'IP-003', status: 'normal', lastMaintenance: '2024-01-05' }
    ]
  },
  {
    id: 2,
    name: '电学实验室B',
    type: '电学实验室',
    location: '教学楼A102',
    capacity: 25,
    equipmentCount: 12,
    status: '正常',
    manager: '李老师',
    description: '专门用于电学实验，配备各种电学测量仪器。',
    createTime: '2024-01-02 11:00:00',
    equipments: [
      { name: '万用表', model: 'MM-001', status: 'normal', lastMaintenance: '2024-01-12' },
      { name: '示波器', model: 'OS-002', status: 'normal', lastMaintenance: '2024-01-09' },
      { name: '电源供应器', model: 'PS-003', status: 'normal', lastMaintenance: '2024-01-07' }
    ]
  },
  {
    id: 3,
    name: '光学实验室C',
    type: '光学实验室',
    location: '教学楼A103',
    capacity: 20,
    equipmentCount: 10,
    status: '维护中',
    manager: '王老师',
    description: '用于光学实验，配备精密光学仪器。',
    createTime: '2024-01-03 12:00:00',
    equipments: [
      { name: '分光计', model: 'SP-001', status: 'normal', lastMaintenance: '2024-01-11' },
      { name: '激光器', model: 'LA-002', status: 'fault', lastMaintenance: '2024-01-06' },
      { name: '干涉仪', model: 'IN-003', status: 'normal', lastMaintenance: '2024-01-04' }
    ]
  }
])

// 获取类型标签类型
const getTypeTagType = (type) => {
  const typeMap = {
    '力学实验室': 'primary',
    '电学实验室': 'success',
    '光学实验室': 'warning',
    '热学实验室': 'danger',
    '综合实验室': 'info'
  }
  return typeMap[type] || 'info'
}

// 获取状态标签类型
const getStatusTagType = (status) => {
  const statusMap = {
    '正常': 'success',
    '维护中': 'warning',
    '停用': 'danger'
  }
  return statusMap[status] || 'info'
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

// 新增实验室
const handleAddLaboratory = () => {
  isEdit.value = false
  Object.assign(editForm, {
    id: null,
    name: '',
    type: '',
    location: '',
    capacity: 30,
    manager: '',
    status: '正常',
    description: ''
  })
  editDialogVisible.value = true
}

// 查看实验室
const handleViewLaboratory = (row) => {
  currentLaboratory.value = row
  detailDialogVisible.value = true
}

// 编辑实验室
const handleEditLaboratory = (row) => {
  isEdit.value = true
  Object.assign(editForm, row)
  editDialogVisible.value = true
}

// 删除实验室
const handleDeleteLaboratory = (row) => {
  ElMessageBox.confirm(
    `确定要删除实验室"${row.name}"吗？此操作不可恢复！`,
    '警告',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(() => {
    const index = laboratoryList.value.findIndex(l => l.id === row.id)
    if (index > -1) {
      laboratoryList.value.splice(index, 1)
      ElMessage.success('删除成功')
    }
  })
}

// 保存实验室
const handleSaveLaboratory = async () => {
  if (!editFormRef.value) return
  
  try {
    await editFormRef.value.validate()
    
    if (isEdit.value) {
      // 编辑
      const laboratory = laboratoryList.value.find(l => l.id === editForm.id)
      if (laboratory) {
        Object.assign(laboratory, editForm)
      }
      ElMessage.success('编辑成功')
    } else {
      // 新增
      const newLaboratory = {
        ...editForm,
        id: Date.now(),
        equipmentCount: 0,
        createTime: new Date().toLocaleString(),
        equipments: []
      }
      laboratoryList.value.unshift(newLaboratory)
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
  pagination.total = laboratoryList.value.length
})
</script>

<style scoped>
.laboratory-management {
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

.pagination-wrapper {
  margin-top: 20px;
  text-align: right;
}

.laboratory-detail {
  padding: 20px 0;
}

.equipment-list h4 {
  margin: 20px 0 10px 0;
  color: #303133;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style> 