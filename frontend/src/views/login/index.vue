<template>
  <div class="login-container">
    <div class="login-box">
      <div class="login-header">
        <h2>物理实验管理系统</h2>
        <p>欢迎使用物理实验管理系统</p>
      </div>
      
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
      >
        <el-form-item prop="userType">
          <el-select
            v-model="loginForm.userType"
            placeholder="请选择用户类型"
            size="large"
            style="width: 100%"
          >
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            :placeholder="getUsernamePlaceholder()"
            prefix-icon="User"
            size="large"
          />
        </el-form-item>
        
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            prefix-icon="Lock"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            @click="handleLogin"
            style="width: 100%"
          >
            登录
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const router = useRouter()
const loginFormRef = ref()
const loading = ref(false)

const loginForm = reactive({
  userType: 'student',
  username: '',
  password: ''
})

const loginRules = {
  userType: [
    { required: true, message: '请选择用户类型', trigger: 'change' }
  ],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' }
  ]
}

// 根据用户类型获取用户名占位符
const getUsernamePlaceholder = () => {
  const placeholders = {
    student: '请输入学号',
    teacher: '请输入工号',
    admin: '请输入用户名'
  }
  return placeholders[loginForm.userType] || '请输入用户名'
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  
  try {
    await loginFormRef.value.validate()
    loading.value = true
    
    const response = await login({
      username: loginForm.username,
      password: loginForm.password
    })
    
    const userInfo = response.data
    
    // 登录成功，cookie由后端自动设置
    // 只需要存储用户信息到localStorage用于前端显示
    localStorage.setItem('userInfo', JSON.stringify(userInfo))
    
    ElMessage.success('登录成功')
    router.push('/')
    
    // imakerlab完成
    
  } catch (error) {
    console.error('登录失败:', error)
    ElMessage.error(error.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  background: #eaf1f8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-box {
  width: 400px;
  padding: 40px;
  background: white;
  border-radius: 12px;
  box-shadow: 0 8px 24px rgba(31, 45, 61, 0.14);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;
}

.login-header h2 {
  color: var(--color-text);
  font-size: 26px;
  line-height: 1.4;
  margin-bottom: 10px;
}

.login-header p {
  color: var(--color-text-secondary);
  font-size: 16px;
}

.login-form {
  margin-top: 20px;
}

.test-accounts {
  margin-top: 30px;
  padding: 20px;
  background-color: #f5f7fa;
  border-radius: 6px;
}

.test-accounts h4 {
  margin: 0 0 15px 0;
  color: #333;
  font-size: 14px;
}

.account-list {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.account-item {
  margin-bottom: 8px;
}

.account-item strong {
  color: #333;
}

/* 移动端响应式样式 */
@media (max-width: 768px) {
  .login-container {
    padding: 20px;
  }
  
  .login-box {
    width: 100%;
    max-width: 350px;
    padding: 30px 20px;
  }
  
  .login-header h2 {
    font-size: 23px;
  }
  
  .login-header p {
    font-size: 15px;
  }
  
  .login-form {
    margin-top: 15px;
  }
}

@media (max-width: 480px) {
  .login-box {
    padding: 25px 15px;
  }
  
  .login-header h2 {
    font-size: 22px;
  }
}
</style> 
