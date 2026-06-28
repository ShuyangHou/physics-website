<template>
  <div class="semester-management">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>学期管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleAddSemester">
              <el-icon><Plus /></el-icon>
              新增学期
            </el-button>
            <el-button type="success" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 学期列表 -->
      <el-table :data="semesterList" style="width: 100%" v-loading="loading">
        <el-table-column prop="semesterName" label="学期名称" width="160" />
        <el-table-column label="开始日期" width="140">
          <template #default="scope">{{ formatDate(scope.row.startDate) }}</template>
        </el-table-column>
        <el-table-column label="结束日期" width="140">
          <template #default="scope">{{ formatDate(scope.row.endDate) }}</template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'active' ? 'success' : 'info'" style="white-space: nowrap;">
              {{ scope.row.status === 'active' ? '当前学期' : '非当前学期' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.updateTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="320" fixed="right">
          <template #default="scope">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleEditSemester(scope.row)"
            >
              编辑
            </el-button>
            <el-button 
              v-if="scope.row.status !== 'active'"
              type="success" 
              size="small" 
              @click="handleSetActive(scope.row)"
            >
              设为当前
            </el-button>
            <el-button 
              type="danger" 
              size="small" 
              @click="handleDeleteSemester(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑学期对话框 -->
    <el-dialog
      v-model="semesterDialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <el-form :model="semesterForm" label-width="100px" :rules="semesterRules" ref="semesterFormRef">
        <el-form-item label="学期名称" prop="semesterName">
          <el-input v-model="semesterForm.semesterName" placeholder="请输入学期名称，如：2024春季学期" />
        </el-form-item>
        
        <el-form-item label="开始日期" prop="startDate">
          <el-date-picker
            v-model="semesterForm.startDate"
            type="date"
            placeholder="选择开始日期"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="结束日期" prop="endDate">
          <el-date-picker
            v-model="semesterForm.endDate"
            type="date"
            placeholder="选择结束日期"
            style="width: 100%"
          />
        </el-form-item>
        
        <el-form-item label="是否当前学期">
          <el-switch v-model="semesterForm.isActive" />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="semesterDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveSemester">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getSemesterList, addSemester, updateSemester, deleteSemester } from '@/api/semester'
import { formatDate, formatDateTime } from '@/utils/date'

const loading = ref(false)
const semesterDialogVisible = ref(false)
const isEdit = ref(false)
const semesterFormRef = ref()

// 学期表单
const semesterForm = reactive({
  semesterId: null,
  semesterName: '',
  startDate: '',
  endDate: '',
  isActive: false
})

// 表单验证规则
const semesterRules = {
  semesterName: [
    { required: true, message: '请输入学期名称', trigger: 'blur' }
  ],
  startDate: [
    { required: true, message: '请选择开始日期', trigger: 'change' }
  ],
  endDate: [
    { required: true, message: '请选择结束日期', trigger: 'change' }
  ]
}

// 学期列表数据
const semesterList = ref([])

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑学期' : '新增学期'
})

// 加载学期列表
const loadSemesterList = async () => {
  try {
    loading.value = true
    const response = await getSemesterList({ current: 1, size: 100 })
    if (response.code === 200) {
      semesterList.value = response.data.records || []
    } else {
      ElMessage.error(response.message || '加载学期列表失败')
    }
  } catch (error) {
    console.error('加载学期列表失败:', error)
    ElMessage.error('加载学期列表失败')
  } finally {
    loading.value = false
  }
}

// 新增学期
const handleAddSemester = () => {
  isEdit.value = false
  resetSemesterForm()
  semesterDialogVisible.value = true
}

// 编辑学期
const handleEditSemester = (row) => {
  isEdit.value = true
  Object.assign(semesterForm, {
    semesterId: row.semesterId,
    semesterName: row.semesterName,
    startDate: row.startDate,
    endDate: row.endDate,
    isActive: row.status === 'active'
  })
  semesterDialogVisible.value = true
}

// 设为当前学期
const handleSetActive = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要将"${row.semesterName}"设为当前学期吗？`,
      '确认操作',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    // 先更新其他学期为非当前
    for (let semester of semesterList.value) {
      if (semester.semesterId !== row.semesterId) {
        await updateSemester({ ...semester, status: 'inactive' })
      }
    }
    
    // 再更新当前学期为active
    await updateSemester({ ...row, status: 'active' })
    
    // 更新localStorage中的当前学期ID
    localStorage.setItem('semesterId', String(row.semesterId))
    localStorage.setItem('currentSemesterId', String(row.semesterId))
    
    ElMessage.success('设置成功')
    loadSemesterList() // 重新加载列表
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('设置失败')
    }
  }
}

// 删除学期
const handleDeleteSemester = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除学期"${row.semesterName}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await deleteSemester(row.semesterId)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadSemesterList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 保存学期
const handleSaveSemester = async () => {
  if (!semesterFormRef.value) return
  
  try {
    await semesterFormRef.value.validate()
    
    const data = {
      semesterName: semesterForm.semesterName,
      startDate: semesterForm.startDate,
      endDate: semesterForm.endDate,
      status: semesterForm.isActive ? 'active' : 'inactive'
    }
    
    if (isEdit.value) {
      data.semesterId = semesterForm.semesterId
    }
    
    // 如果设为当前学期，需要先将其他学期设为非当前
    if (semesterForm.isActive) {
      for (let semester of semesterList.value) {
        if (semester.semesterId !== data.semesterId) {
          await updateSemester({ ...semester, status: 'inactive' })
        }
      }
      // 更新localStorage中的当前学期ID
      localStorage.setItem('semesterId', String(data.semesterId))
      localStorage.setItem('currentSemesterId', String(data.semesterId))
    }
    
    const response = isEdit.value 
      ? await updateSemester(data)
      : await addSemester(data)
    
    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      semesterDialogVisible.value = false
      loadSemesterList()
    } else {
      ElMessage.error(response.message || (isEdit.value ? '编辑失败' : '新增失败'))
    }
  } catch (error) {
    console.error('保存失败:', error)
  }
}

// 重置学期表单
const resetSemesterForm = () => {
  Object.assign(semesterForm, {
    semesterId: null,
    semesterName: '',
    startDate: '',
    endDate: '',
    isActive: false
  })
}

// 刷新
const handleRefresh = () => {
  loadSemesterList()
}

onMounted(() => {
  loadSemesterList()
})
</script>

<style scoped>
.semester-management {
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style> 