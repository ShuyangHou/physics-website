<template>
  <div class="dashboard">
    <!-- 密码修改提示 -->
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
        <el-button :type="passwordAlertType" size="small" @click="goToProfile">去个人中心修改密码</el-button>
      </template>
    </el-alert>
    
    <el-row :gutter="20">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>欢迎使用物理实验系统</span>
            </div>
          </template>
          
          <div class="welcome-content">
            <h2>您好，{{ userInfo.realName }}！</h2>
            <p>您的身份是：{{ getUserTypeText(userInfo.userType) }}</p>
            
            <div class="quick-actions">
              <h3>快速操作</h3>
              <el-row :gutter="20">
                <!-- 所有用户都可以看到 -->
                <el-col :span="6">
                  <el-card shadow="hover" @click="$router.push('/experiment/schedule')">
                    <div class="action-item">
                      <el-icon size="24"><Calendar /></el-icon>
                      <span>查看课表</span>
                    </div>
                  </el-card>
                </el-col>
                
                <!-- 只有教师可以看到 -->
                <el-col :span="6" v-teacher>
                  <el-card shadow="hover" @click="$router.push('/experiment/grade-entry')">
                    <div class="action-item">
                      <el-icon size="24"><Edit /></el-icon>
                      <span>成绩录入</span>
                    </div>
                  </el-card>
                </el-col>
                
                <!-- 只有管理员可以看到 -->
                <el-col :span="6" v-admin>
                  <el-card shadow="hover" @click="$router.push('/user/import')">
                    <div class="action-item">
                      <el-icon size="24"><Upload /></el-icon>
                      <span>导入学生</span>
                    </div>
                  </el-card>
                </el-col>
                
                <!-- 教师和管理员可以看到 -->
                <el-col :span="6" v-permission="['teacher', 'admin']">
                  <el-card shadow="hover" @click="$router.push('/system/semester')">
                    <div class="action-item">
                      <el-icon size="24"><Setting /></el-icon>
                      <span>学期管理</span>
                    </div>
                  </el-card>
                </el-col>
              </el-row>
            </div>
            
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { getUserInfo } from '@/utils/permission'
import { checkPasswordStatus } from '@/api/auth'
import { ElMessage } from 'element-plus'

const router = useRouter()

// 获取用户信息
const userInfo = computed(() => getUserInfo())

// 是否显示修改密码提示
const showPasswordAlert = ref(false)
let passwordReminderTimer = null

// 密码提示的动态内容
const passwordAlertTitle = computed(() => {
  return '安全提示'
})

const passwordAlertType = computed(() => {
  return 'warning'
})

const passwordAlertDescription = computed(() => {
  return '为了账户安全，建议您定期修改密码'
})

// 获取用户类型文本
const getUserTypeText = (userType) => {
  const typeMap = {
    'admin': '管理员',
    'teacher': '教师',
    'student': '学生'
  }
  return typeMap[userType] || '未知'
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

// 跳转到个人中心
const goToProfile = () => {
  router.push('/profile/index')
}

// 页面加载时检查密码状态
onMounted(() => {
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
})

onUnmounted(() => {
  if (passwordReminderTimer) {
    clearInterval(passwordReminderTimer)
    passwordReminderTimer = null
  }
})
</script>

<style scoped>
.dashboard {
  max-width: 1280px;
  margin: 0 auto;
  padding: 4px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.welcome-content {
  text-align: center;
}

.welcome-content h2 {
  color: var(--color-text);
  margin-bottom: 8px;
  font-size: 26px;
  line-height: 1.4;
}

.welcome-content p {
  color: var(--color-text-secondary);
  font-size: var(--base-font-size);
  margin-bottom: 30px;
}

.quick-actions {
  margin: 30px 0;
}

.quick-actions h3 {
  margin-bottom: 20px;
  color: var(--color-text);
  font-size: 20px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-height: 132px;
  padding: 24px 18px;
  cursor: pointer;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.action-item:hover {
  transform: translateY(-2px);
}

.action-item .el-icon {
  margin-bottom: 10px;
  color: #409EFF;
}

.action-item span {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text);
}

.system-info {
  margin-top: 30px;
}

.system-info h3 {
  margin-bottom: 20px;
  color: #333;
}

@media (max-width: 768px) {
  .welcome-content h2 {
    font-size: 22px;
  }

  .quick-actions :deep(.el-col) {
    max-width: 100%;
    flex: 0 0 100%;
    margin-bottom: 12px;
  }
}
</style> 
