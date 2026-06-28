<template>
  <div class="profile">
    <!-- 修改密码提示 -->
    <el-alert
      v-if="showPasswordAlert"
      :title="passwordAlertTitle"
      :type="passwordAlertType"
      :description="passwordAlertDescription"
      show-icon
      :closable="false"
      style="margin-bottom: 20px;"
    >
      <template #default>
        <el-button :type="passwordAlertType" size="small" @click="handleChangePassword">立即修改密码</el-button>
      </template>
    </el-alert>
    
    <!-- 管理员强制密码修改提示 -->
    <el-alert
      v-if="showPasswordAlert && userInfo.userType === 'admin'"
      title="⚠️ 强制要求"
      type="error"
      description="管理员账户必须修改默认密码才能正常使用系统功能！"
      show-icon
      :closable="false"
      style="margin-bottom: 20px; border: 2px solid #f56c6c;"
    />
    
    <el-row :gutter="20">
      <!-- 个人信息卡片 -->
      <el-col :span="8">
        <el-card class="profile-card">
          <div class="profile-header">
            <div class="avatar-info-row">
              <div class="avatar-container">
                <el-avatar :size="80" src="https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png" />
              </div>
              <div class="profile-info">
                <h3 class="user-name">{{ userInfo.realName }}</h3>
                <p class="user-type">{{ getUserTypeText() }}</p>
                <el-tag :type="getUserTypeTag()" class="user-tag">{{ userInfo.userType }}</el-tag>
              </div>
            </div>
          </div>
          
          <div class="profile-actions">
            <el-button type="primary" @click="handleEdit" class="action-btn">
              <el-icon><Edit /></el-icon>
              编辑信息
            </el-button>
            <el-button type="warning" @click="handleChangePassword" class="action-btn">
              <el-icon><Lock /></el-icon>
              修改密码
            </el-button>
          </div>
        </el-card>
      </el-col>

      <!-- 详细信息 -->
      <el-col :span="16">
        <el-card class="detail-card">
          <template #header>
            <div class="card-header">
              <span class="section-title">个人信息</span>
            </div>
          </template>

          <el-descriptions :column="2" border class="info-descriptions">
            <el-descriptions-item label="姓名">
              <span class="info-value">{{ userInfo.realName }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="用户类型">
              <span class="info-value">{{ getUserTypeText() }}</span>
            </el-descriptions-item>
            <!-- 移除学号/工号显示 -->
            <el-descriptions-item v-if="userInfo.className" label="班级">
              <span class="info-value">{{ userInfo.className }}</span>
            </el-descriptions-item>
            <el-descriptions-item v-if="userInfo.major" label="专业">
              <span class="info-value">{{ userInfo.major }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>

    <!-- 编辑个人信息对话框 -->
    <el-dialog
      v-model="editDialogVisible"
      title="编辑个人信息"
      width="600px"
    >
      <el-form :model="editForm" :rules="editRules" ref="editFormRef" label-width="100px">
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="editForm.realName" placeholder="请输入姓名" disabled />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 修改密码对话框 -->
    <el-dialog
      v-model="passwordDialogVisible"
      title="修改密码"
      width="500px"
    >
      <el-form :model="passwordForm" :rules="passwordRules" ref="passwordFormRef" label-width="100px">
        <el-form-item label="原密码" prop="oldPassword">
          <el-input 
            v-model="passwordForm.oldPassword" 
            type="password" 
            placeholder="请输入原密码"
            show-password
          />
        </el-form-item>
        
        <el-form-item label="新密码" prop="newPassword">
          <el-input 
            v-model="passwordForm.newPassword" 
            type="password" 
            placeholder="请输入新密码"
            show-password
          />
        </el-form-item>
        
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input 
            v-model="passwordForm.confirmPassword" 
            type="password" 
            placeholder="请确认新密码"
            show-password
          />
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="passwordDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSavePassword">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { formatDateTime } from '@/utils/date'
import { ElMessage } from 'element-plus'
import { changePassword, checkPasswordStatus } from '@/api/auth'
import { Edit, Lock } from '@element-plus/icons-vue'

const editDialogVisible = ref(false)
const passwordDialogVisible = ref(false)
const editFormRef = ref()
const passwordFormRef = ref()

// 用户信息
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

// 是否显示修改密码提示
const showPasswordAlert = ref(false)
let passwordReminderTimer = null

// 密码提示的动态内容
const passwordAlertTitle = computed(() => {
  return userInfo.value.userType === 'admin' ? '重要安全提醒' : '安全提示'
})

const passwordAlertType = computed(() => {
  return userInfo.value.userType === 'admin' ? 'error' : 'warning'
})

const passwordAlertDescription = computed(() => {
  if (userInfo.value.userType === 'admin') {
    return '管理员账户安全至关重要！请立即修改默认密码以保护系统安全。'
  }
  return '为了账户安全，建议您定期修改密码'
})



// 最近实验记录
const recentExperiments = ref([
  {
    experimentName: '力学实验一：测量重力加速度',
    experimentType: '力学实验',
    status: '已完成',
    score: 92.5,
    date: '2024-01-15'
  },
  {
    experimentName: '电学实验二：欧姆定律验证',
    experimentType: '电学实验',
    status: '已完成',
    score: 85.0,
    date: '2024-01-14'
  },
  {
    experimentName: '光学实验三：光的干涉',
    experimentType: '光学实验',
    status: '进行中',
    score: null,
    date: '2024-01-13'
  },
  {
    experimentName: '热学实验四：比热容测量',
    experimentType: '热学实验',
    status: '已安排',
    score: null,
    date: '2024-01-20'
  }
])

// 编辑表单
const editForm = reactive({
  realName: ''
})

// 修改密码表单
const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

// 编辑表单验证规则
const editRules = {
  realName: []
}

// 修改密码表单验证规则
const passwordRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' }
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== passwordForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 获取用户类型文本
const getUserTypeText = () => {
  const typeMap = {
    student: '学生',
    teacher: '教师',
    admin: '管理员'
  }
  return typeMap[userInfo.value.userType] || '未知'
}

// 获取用户类型标签
const getUserTypeTag = () => {
  const tagMap = {
    student: 'success',
    teacher: 'warning',
    admin: 'danger'
  }
  return tagMap[userInfo.value.userType] || 'info'
}

// 获取实验类型标签类型
const getTypeTagType = (type) => {
  const typeMap = {
    '力学实验': 'primary',
    '电学实验': 'success',
    '光学实验': 'warning',
    '热学实验': 'danger'
  }
  return typeMap[type] || 'info'
}

// 获取状态标签类型
const getStatusTagType = (status) => {
  const statusMap = {
    '已安排': 'info',
    '进行中': 'warning',
    '已完成': 'success',
    '已取消': 'danger'
  }
  return statusMap[status] || 'info'
}

// 获取成绩颜色
const getScoreColor = (score) => {
  if (score >= 90) return '#67C23A'
  if (score >= 80) return '#409EFF'
  if (score >= 70) return '#E6A23C'
  if (score >= 60) return '#F56C6C'
  return '#909399'
}

// 获取实验图标
const getExperimentIcon = (type) => {
  const iconMap = {
    '力学实验': '⚖️',
    '电学实验': '⚡',
    '光学实验': '🔆',
    '热学实验': '🌡️'
  }
  return iconMap[type] || '🧪'
}

// 编辑个人信息
const handleEdit = () => {
  editForm.realName = userInfo.value.realName || ''
  
  editDialogVisible.value = true
}

// 修改密码
const handleChangePassword = () => {
  passwordForm.oldPassword = ''
  passwordForm.newPassword = ''
  passwordForm.confirmPassword = ''
  passwordDialogVisible.value = true
}

// 保存个人信息
const handleSave = async () => {
  if (!editFormRef.value) return
  
  try {
    await editFormRef.value.validate()
    
    // 更新用户信息
    // 姓名不允许修改，不覆盖
    
    // 保存到本地存储
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
    
    ElMessage.success('个人信息更新成功')
    editDialogVisible.value = false
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

// 检查是否需要显示修改密码提示
const checkPasswordAlert = async () => {
  try {
    // 调用后端API检查密码是否为默认密码
    const response = await checkPasswordStatus()
    
    // 如果返回true，说明密码是默认密码0000，即未修改过密码
    const isDefaultPassword = response.data
    
    if (isDefaultPassword) {
      // 密码未修改，显示提示
      showPasswordAlert.value = true
      
      // 如果是管理员用户，强制要求修改密码
      if (userInfo.value.userType === 'admin') {
        ElMessage.error('管理员账户必须修改默认密码！')
      }
    } else {
      // 密码已修改，隐藏提示
      showPasswordAlert.value = false
    }
  } catch (error) {
    // 如果API调用失败，默认隐藏提示
    showPasswordAlert.value = false
    console.error('检查密码状态失败:', error)
  }
}

// 保存密码
const handleSavePassword = async () => {
  if (!passwordFormRef.value) return
  
  try {
    await passwordFormRef.value.validate()
    
    await changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码修改成功')
    passwordDialogVisible.value = false
    
    // 密码修改成功后，重新检查密码状态
    await checkPasswordAlert()
  } catch (error) {
    console.error('表单验证失败:', error)
  }
}

onMounted(() => {
  // 初始化用户信息
  if (!userInfo.value.realName) {
    userInfo.value = {
      id: 1,
      realName: '张三',
      userType: 'student',
      studentId: '2021001',
      classId: '物理1班',
      major: '物理学',
      createTime: '2024-01-01',
      lastLoginTime: '2024-01-15'
    }
    localStorage.setItem('userInfo', JSON.stringify(userInfo.value))
  }
  
  // 检查是否需要显示修改密码提示
  checkPasswordAlert()
  
  // 如果是管理员且未修改密码，设置定时提醒
  if (userInfo.value.userType === 'admin') {
    passwordReminderTimer = setInterval(async () => {
      // 每次提醒前都重新检查密码状态
      await checkPasswordAlert()
      if (showPasswordAlert.value) {
        ElMessage.warning('管理员账户安全提醒：请及时修改默认密码！')
      }
    }, 30000) // 每30秒提醒一次
  }
  
  // imakerlab完成
})

onUnmounted(() => {
  if (passwordReminderTimer) {
    clearInterval(passwordReminderTimer)
    passwordReminderTimer = null
  }
})
</script>

<style scoped>
.profile {
  padding: 20px;
  min-height: calc(100vh - 90px);
}

/* 卡片样式 */
.profile-card, .detail-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.profile-card {
  text-align: center;
}

.profile-header {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 20px 0;
}

.avatar-info-row {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
  margin-bottom: 30px;
}

.avatar-container {
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-size: 24px;
  font-weight: bold;
  margin: 0;
  color: #303133;
}

.user-type {
  font-size: 14px;
  color: #667085;
  margin: 0;
}

.user-tag {
  font-size: 14px;
  padding: 4px 12px;
}

.profile-actions {
  padding: 20px 0;
  display: flex;
  flex-direction: column;
  gap: 15px;
  width: 100%;
}

.action-btn {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border-radius: 8px;
  margin: 0;
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-descriptions {
  border-radius: 8px;
  overflow: hidden;
}

.info-descriptions :deep(.el-descriptions__label) {
  background: #f5f7fa;
  font-weight: 600;
  color: #606266;
}

.info-descriptions :deep(.el-descriptions__content) {
  background: #fff;
}

.info-value {
  color: #303133;
  font-weight: 500;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .profile {
    padding: 10px;
  }
  
  .profile .el-row {
    flex-direction: column;
  }
  
  .profile .el-col {
    margin-bottom: 20px;
  }
  
  .profile .el-col:last-child {
    margin-bottom: 0;
  }
  
  .profile-actions {
    gap: 10px;
  }
  
  .action-btn {
    padding: 10px 16px;
  }
  
  .info-descriptions {
    font-size: 14px;
  }
  
  .user-name {
    font-size: 20px;
  }
  
  .avatar-info-row {
    gap: 15px;
  }
}
</style> 
