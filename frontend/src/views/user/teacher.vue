<template>
  <div class="teacher-management">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>教师管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="success" @click="handleAddTeacher">
              <el-icon><Plus /></el-icon>
              新增教师
            </el-button>
          </div>
        </div>
      </template>

      <div class="filter-section">
        <el-form :inline="true" :model="filterForm">
          <el-form-item label="教师姓名">
            <el-input v-model="filterForm.realName" placeholder="请输入教师姓名" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleFilter">筛选</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-table :data="teacherList" style="width: 100%" v-loading="loading">
        <el-table-column prop="username" label="用户名" width="140" />
        <el-table-column prop="realName" label="教师姓名" width="140" />
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="scope">
            <el-button type="warning" size="small" @click="handleEditTeacher(scope.row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDeleteTeacher(scope.row)">删除</el-button>
            <el-button v-if="isAdmin" type="info" size="small" @click="handleResetPassword(scope.row)">重置密码</el-button>
          </template>
        </el-table-column>
      </el-table>

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

    <el-dialog v-model="teacherDialogVisible" :title="dialogTitle" width="500px">
      <el-form :model="teacherForm" label-width="100px" ref="teacherFormRef" :rules="teacherRules">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="teacherForm.username" placeholder="请输入用户名" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="教师姓名" prop="realName">
          <el-input v-model="teacherForm.realName" placeholder="请输入教师姓名" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="teacherDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveTeacher">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getTeacherList, addUser, updateUser, deleteUser, resetPassword } from '@/api/user'
import { formatDateTime } from '@/utils/date'

const loading = ref(false)
const teacherDialogVisible = ref(false)
const isEdit = ref(false)
const teacherFormRef = ref()

const filterForm = reactive({ realName: '' })
const pagination = reactive({ current: 1, size: 10, total: 0 })
const teacherList = ref([])

// 当前用户是否管理员
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isAdmin = computed(() => userInfo.value.userType === 'admin')

// 表单验证规则
const teacherRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在 2 到 20 个字符', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入教师姓名', trigger: 'blur' }
  ]
}

const teacherForm = reactive({ userId: null, username: '', realName: '' })
const dialogTitle = computed(() => (isEdit.value ? '编辑教师' : '新增教师'))

const loadTeachers = async () => {
  loading.value = true
  try {
    const params = { current: pagination.current, size: pagination.size }
    if (filterForm.realName) params.realName = filterForm.realName
    const resp = await getTeacherList(params)
    teacherList.value = resp?.data?.records || []
    pagination.total = resp?.data?.total || 0
  } catch (e) {
    ElMessage.error('加载教师列表失败')
  } finally {
    loading.value = false
  }
}

const handleFilter = () => { pagination.current = 1; loadTeachers() }
const handleReset = () => { filterForm.realName = ''; pagination.current=1; loadTeachers() }
const handleRefresh = () => loadTeachers()

const handleAddTeacher = () => { 
  isEdit.value = false; 
  Object.assign(teacherForm, { 
    userId: null, 
    username: '', 
    realName: '' 
  }); 
  teacherDialogVisible.value = true 
}

const handleEditTeacher = (row) => { 
  isEdit.value = true; 
  Object.assign(teacherForm, { 
    userId: row.userId, 
    username: row.username, 
    realName: row.realName 
  }); 
  teacherDialogVisible.value = true 
}

const handleSaveTeacher = async () => {
  try {
    if (!teacherFormRef.value) return
    
    await teacherFormRef.value.validate()
    
    if (isEdit.value) {
      await updateUser({ 
        userId: teacherForm.userId, 
        realName: teacherForm.realName 
      })
      ElMessage.success('编辑成功')
    } else {
      const payload = { 
        username: teacherForm.username, 
        realName: teacherForm.realName, 
        userType: 'teacher', 
        password: '0000' 
      }
      await addUser(payload)
      ElMessage.success('新增成功')
    }
    teacherDialogVisible.value = false
    loadTeachers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('保存失败')
    }
  }
}

const handleDeleteTeacher = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除教师“${row.realName}”？`, '删除确认', { type: 'warning' })
    await deleteUser(row.userId)
    ElMessage.success('删除成功')
    loadTeachers()
  } catch {}
}

const handleResetPassword = async (row) => {
  try {
    await ElMessageBox.confirm(`确定将教师“${row.realName}”的密码重置为默认值？`, '重置确认', { type: 'warning' })
    await resetPassword(row.userId)
    ElMessage.success('已重置为默认密码：0000')
  } catch {}
}

const handleSizeChange = (size) => { pagination.size = size; pagination.current = 1; loadTeachers() }
const handleCurrentChange = (current) => { pagination.current = current; loadTeachers() }

onMounted(() => { loadTeachers() })
</script>

<style scoped>
.teacher-management { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 10px; }
.filter-section { margin-bottom: 20px; padding: 20px; background-color: #f5f7fa; border-radius: 4px; }
.pagination-wrapper { margin-top: 20px; text-align: right; }
.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; }
</style> 