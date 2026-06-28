<template>
  <div class="student-management">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>学生管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleAddStudent">
              <el-icon><Plus /></el-icon>
              新增学生
            </el-button>
            <el-button type="success" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 筛选条件 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="小组">
            <el-select v-model="filterForm.groupName" placeholder="请选择小组" clearable filterable>
              <el-option
                v-for="group in groupList"
                :key="group.groupId"
                :label="group.groupName"
                :value="group.groupName"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="班级">
            <el-select v-model="filterForm.classId" placeholder="请选择班级" clearable filterable>
              <el-option
                v-for="classId in classList"
                :key="classId"
                :label="classId"
                :value="classId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="学生姓名">
            <el-input v-model="filterForm.realName" placeholder="请输入学生姓名" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleFilter">筛选</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 学生列表 -->
      <el-table :data="studentList" style="width: 100%" v-loading="loading" @sort-change="handleSortChange">
        <el-table-column prop="schoolId" label="学号" width="120" sortable="custom" />
        <el-table-column prop="realName" label="姓名" width="100" sortable="custom" />
        <el-table-column label="小组" width="100" sortable="custom">
          <template #default="scope">
            {{ getGroupName(scope.row.groupName) }}
          </template>
        </el-table-column>
        <el-table-column prop="classId" label="班级" width="120" sortable="custom" />

        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button 
              type="primary" 
              size="small" 
              @click="handleEditStudent(scope.row)"
            >
              编辑
            </el-button>
            <el-button 
              type="warning" 
              size="small" 
              @click="handleResetPassword(scope.row)"
            >
              重置密码
            </el-button>
            <el-button 
              v-if="userInfo.userType === 'admin'"
              type="danger" 
              size="small" 
              @click="handleDeleteStudent(scope.row)"
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

    <!-- 新增/编辑学生对话框 -->
    <el-dialog
      v-model="studentDialogVisible"
      :title="dialogTitle"
      width="500px"
    >
      <el-form :model="studentForm" label-width="100px" :rules="studentRules" ref="studentFormRef">
        <el-form-item label="学号" prop="schoolId">
          <el-input v-model="studentForm.schoolId" placeholder="请输入学号" />
        </el-form-item>
        
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="studentForm.realName" placeholder="请输入姓名" />
        </el-form-item>
        
        <el-form-item label="小组" prop="groupName">
          <el-select v-model="studentForm.groupName" placeholder="请选择小组" style="width: 100%">
            <el-option
              v-for="group in groupList"
              :key="group.groupId"
              :label="group.groupName"
              :value="group.groupName"
            />
          </el-select>
        </el-form-item>
        
        <el-form-item label="班级" prop="classId">
          <el-select v-model="studentForm.classId" placeholder="请选择班级" style="width: 100%">
            <el-option
              v-for="classId in classList"
              :key="classId"
              :label="classId"
              :value="classId"
            />
          </el-select>
        </el-form-item>
        
        <!-- 移除手机号与邮箱编辑项 -->
        

      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button :disabled="saving" @click="studentDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSaveStudent">{{ saving ? '保存中...' : '保存' }}</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getStudentList, addUser, updateUser, deleteUser, resetPassword, checkDuplicateStudent, mergeStudentGrades } from '@/api/user'

const userInfo = reactive(JSON.parse(localStorage.getItem('userInfo') || '{}'))

const loading = ref(false)
const saving = ref(false)
const studentDialogVisible = ref(false)
const isEdit = ref(false)
const studentFormRef = ref()

// 筛选表单
const filterForm = reactive({
  groupName: '',
  classId: '',
  realName: ''
})

// 班级列表 - 从学生数据中提取
const classList = ref([])

// 小组列表 - 从后端获取
const groupList = ref([])


// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 学生表单
const studentForm = reactive({
  userId: null,
  schoolId: '',
  realName: '',
  groupName: '',
  classId: '',
  
  userType: 'student'
})

// 表单验证规则
const studentRules = {
  schoolId: [
    { required: true, message: '请输入学号', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入姓名', trigger: 'blur' }
  ],
  groupName: [
    { required: true, message: '请选择小组', trigger: 'change' }
  ],
  classId: [
    { required: true, message: '请选择班级', trigger: 'change' }
  ]
}

// 学生列表数据
const studentList = ref([])

// 排序参数
const sortParams = ref({
  field: 'schoolId',
  order: 'asc'
})

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑学生' : '新增学生'
})

// 加载学生列表
const loadStudentList = async () => {
  try {
    loading.value = true
    const params = {
      current: pagination.current,
      size: pagination.size
    }
    
    // 添加筛选条件
    if (filterForm.classId) params.classId = filterForm.classId
    if (filterForm.realName) params.realName = filterForm.realName
    if (filterForm.groupName) params.groupName = filterForm.groupName
    
    // 添加排序参数
    if (sortParams.value.field) {
      params.sortField = sortParams.value.field
      params.sortOrder = sortParams.value.order
    }
    
    const response = await getStudentList(params)
    if (response.code === 200) {
      // 新的响应格式包含学生列表、班级列表和小组列表
      studentList.value = response.data.studentList.records || []
      pagination.total = response.data.studentList.total || 0
      
      // 从后端获取的班级和小组列表
      classList.value = response.data.classList || []
      groupList.value = (response.data.groupList || []).map(groupName => ({
        groupId: groupName,
        groupName: groupName
      }))
    } else {
      ElMessage.error(response.message || '加载学生列表失败')
    }
  } catch (error) {
    console.error('加载学生列表失败:', error)
    ElMessage.error('加载学生列表失败')
  } finally {
    loading.value = false
  }
}

// 从学生数据中更新小组列表（不再需要，因为从后端获取）
const updateGroupList = () => {
  // 这个方法现在不再使用，因为小组列表直接从后端获取
}

// 从学生数据中更新班级列表（不再需要，因为从后端获取）
const updateClassList = () => {
  // 这个方法现在不再使用，因为班级列表直接从后端获取
}

// 根据小组名称获取小组名称（保持兼容性）
const getGroupName = (groupName) => {
  return groupName || ''
}

// 新增学生
const handleAddStudent = () => {
  isEdit.value = false
  resetStudentForm()
  studentDialogVisible.value = true
}

// 编辑学生
const handleEditStudent = (row) => {
  isEdit.value = true
  Object.assign(studentForm, {
    userId: row.userId,
    schoolId: row.schoolId,
    realName: row.realName,
    groupName: row.groupName,
    classId: row.classId,
    phone: row.phone,
    email: row.email
  })
  studentDialogVisible.value = true
}

// 重置密码
const handleResetPassword = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要重置学生"${row.realName}"的密码吗？`,
      '重置密码确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    await resetPassword(row.userId)
    ElMessage.success('密码重置成功')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('密码重置失败')
    }
  }
}

// 删除学生
const handleDeleteStudent = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除学生"${row.realName}"吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await deleteUser(row.userId)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadStudentList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 保存学生
const handleSaveStudent = async () => {
  if (!studentFormRef.value) return

  try {
    await studentFormRef.value.validate()
  } catch (error) {
    // 表单校验未通过，直接返回（不进入保存中状态）
    return
  }

  saving.value = true
  try {
    const data = {
      schoolId: studentForm.schoolId,
      realName: studentForm.realName,
      groupName: studentForm.groupName,
      classId: studentForm.classId,
      
      userType: 'student'
    }
    
    if (isEdit.value) {
      data.userId = studentForm.userId
    } else {
      // 新增学生时设置默认值
      data.username = studentForm.schoolId
      data.password = '0000'
    }
    
    if (isEdit.value) {
      const userInfo = JSON.parse(localStorage.getItem('userInfo') || '{}')
      const dupResp = await checkDuplicateStudent({
        schoolId: data.schoolId,
        excludeUserId: data.userId
      })
      if (dupResp?.code === 200 && dupResp.data?.duplicate) {
        if (userInfo?.userType !== 'admin') {
          ElMessage.error(`学号已存在：${dupResp.data.realName || ''}（ID:${dupResp.data.userId}），请联系管理员处理`)
          return
        }

        const keepWhich = await ElMessageBox.confirm(
          `检测到学号重复：\n` +
            `- 现有学生：${dupResp.data.realName || ''}（ID:${dupResp.data.userId}）\n` +
            `- 你正在编辑：${studentForm.realName || ''}（ID:${data.userId}）\n\n` +
            `请选择要“保留”的账号：`,
          '学号重复处理',
          {
            confirmButtonText: '保留当前编辑账号',
            cancelButtonText: '保留现有账号',
            distinguishCancelAndClose: true,
            type: 'warning'
          }
        ).then(() => 'keep_current').catch((e) => {
          if (e === 'cancel') return 'keep_existing'
          return 'close'
        })

        if (keepWhich === 'close') return

        if (keepWhich === 'keep_current') {
          const action = await ElMessageBox.confirm(
            `是否将“现有学生(ID:${dupResp.data.userId})”的成绩合并到“当前编辑学生(ID:${data.userId})”？\n` +
              `（若两边同一实验已有成绩，将跳过重复项）`,
            '成绩合并',
            {
              confirmButtonText: '合并后继续保存',
              cancelButtonText: '不合并，直接保存',
              distinguishCancelAndClose: true,
              type: 'warning'
            }
          ).then(() => 'merge').catch((e) => {
            if (e === 'cancel') return 'save_only'
            return 'close'
          })

          if (action === 'close') return
          if (action === 'merge') {
            const mergeResp = await mergeStudentGrades({
              fromUserId: dupResp.data.userId,
              toUserId: data.userId
            })
            if (mergeResp?.code === 200) {
              ElMessage.success(`成绩合并完成：迁移${mergeResp.data?.migrated || 0}条，跳过${mergeResp.data?.skipped || 0}条`)
            } else {
              ElMessage.error(mergeResp?.message || '合并成绩失败')
              return
            }
          }
        }

        if (keepWhich === 'keep_existing') {
          const action = await ElMessageBox.confirm(
            `是否将“当前编辑学生(ID:${data.userId})”的成绩合并到“现有学生(ID:${dupResp.data.userId})”？\n` +
              `（若两边同一实验已有成绩，将跳过重复项）`,
            '成绩合并',
            {
              confirmButtonText: '合并并覆盖到现有账号',
              cancelButtonText: '不合并，直接覆盖到现有账号',
              distinguishCancelAndClose: true,
              type: 'warning'
            }
          ).then(() => 'merge').catch((e) => {
            if (e === 'cancel') return 'save_only'
            return 'close'
          })

          if (action === 'close') return
          if (action === 'merge') {
            const mergeResp = await mergeStudentGrades({
              fromUserId: data.userId,
              toUserId: dupResp.data.userId
            })
            if (mergeResp?.code === 200) {
              ElMessage.success(`成绩合并完成：迁移${mergeResp.data?.migrated || 0}条，跳过${mergeResp.data?.skipped || 0}条`)
            } else {
              ElMessage.error(mergeResp?.message || '合并成绩失败')
              return
            }
          }

          const overwriteResp = await updateUser({
            ...data,
            userId: dupResp.data.userId
          })
          if (overwriteResp?.code === 200) {
            ElMessage.success(`已覆盖到现有账号（ID:${dupResp.data.userId}）。原账号（ID:${data.userId}）未自动删除，请按需手动处理`)
            studentDialogVisible.value = false
            loadStudentList()
            return
          } else {
            ElMessage.error(overwriteResp?.message || '覆盖保存失败')
            return
          }
        }
      }
    }

    const response = isEdit.value
      ? await updateUser(data)
      : await addUser(data)
    
    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '编辑成功' : '新增成功')
      studentDialogVisible.value = false
      loadStudentList()
    } else {
      ElMessage.error(response.message || (isEdit.value ? '编辑失败' : '新增失败'))
    }
  } catch (error) {
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

// 重置学生表单
const resetStudentForm = () => {
  Object.assign(studentForm, {
    userId: null,
    schoolId: '',
    realName: '',
    groupName: '',
    classId: '',
    
    userType: 'student'
  })
}

// 处理排序变化
const handleSortChange = ({ column, prop, order }) => {
  if (prop && order) {
    sortParams.value.field = prop
    sortParams.value.order = order === 'ascending' ? 'asc' : 'desc'
  } else {
    sortParams.value.field = 'schoolId'
    sortParams.value.order = 'asc'
  }
  
  // 重新加载数据
  pagination.current = 1
  loadStudentList()
}

// 筛选
const handleFilter = () => {
  pagination.current = 1
  loadStudentList()
}


// 重置筛选
const handleReset = () => {
  filterForm.classId = ''
  filterForm.realName = ''
  filterForm.groupName = ''
  pagination.current = 1
  loadStudentList()
}

// 刷新
const handleRefresh = () => {
  loadStudentList()
}

// 分页大小改变
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadStudentList()
}

// 当前页改变
const handleCurrentChange = (current) => {
  pagination.current = current
  loadStudentList()
}

// 监听导入成功的标志
let refreshFallbackTimer = null

onMounted(() => {
  // 设置默认按学号升序排序
  sortParams.value.field = 'schoolId'
  sortParams.value.order = 'asc'
  
  loadStudentList()
  
  // 检查是否有导入成功的标志，如果有则自动刷新
  checkImportSuccess()

  window.addEventListener('storage', handleStudentImportRefresh)
  window.addEventListener('student-import-success', handleStudentImportRefresh)

  // 兜底检查一次，处理从导入页跳转回来时 storage 事件不触发的情况
  refreshFallbackTimer = setTimeout(() => {
    checkImportSuccess()
  }, 1000)
})

onUnmounted(() => {
  window.removeEventListener('storage', handleStudentImportRefresh)
  window.removeEventListener('student-import-success', handleStudentImportRefresh)
  if (refreshFallbackTimer) {
    clearTimeout(refreshFallbackTimer)
    refreshFallbackTimer = null
  }
})

const handleStudentImportRefresh = (event) => {
  if (!event || event.type === 'student-import-success' || event.key === 'refreshStudentList' || event.key === 'importResult') {
    checkImportSuccess()
  }
}

// 安全的localStorage操作
const safeLocalStorageGet = (key, defaultValue = null) => {
  try {
    const value = localStorage.getItem(key)
    return value !== null ? value : defaultValue
  } catch (error) {
    console.error(`读取localStorage键 ${key} 失败:`, error)
    return defaultValue
  }
}

const safeLocalStorageRemove = (key) => {
  try {
    localStorage.removeItem(key)
  } catch (error) {
    console.error(`删除localStorage键 ${key} 失败:`, error)
  }
}

// 检查导入成功标志
const checkImportSuccess = () => {
  try {
    const refreshFlag = safeLocalStorageGet('refreshStudentList')
    if (refreshFlag === 'true') {
      // 清除标志
      safeLocalStorageRemove('refreshStudentList')
      
      // 显示导入结果
      const importResult = safeLocalStorageGet('importResult')
      if (importResult) {
        try {
          const result = JSON.parse(importResult)
          ElMessage.success(`导入成功！共导入 ${result.successCount} 名学生`)
          safeLocalStorageRemove('importResult')
        } catch (e) {
          console.error('解析导入结果失败:', e)
        }
      }
      
      // 自动刷新学生列表
      loadStudentList()
    }
  } catch (error) {
    console.error('检查导入成功标志时出错:', error)
    // 出错时不要影响正常功能
  }
}
</script>

<style scoped>
.student-management {
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

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style> 
