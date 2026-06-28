import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建axios实例
const request = axios.create({
  baseURL: '/api',  // 使用相对路径，通过Vite代理转发
  timeout: 15000, // 普通页面请求最多等待15秒，避免后端异常时页面永久卡住
  withCredentials: true // 允许携带cookie
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    // 可以在这里添加token等认证信息
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    if (response?.config?.responseType === 'blob' || response?.config?.responseType === 'arraybuffer') {
      return response
    }
    const res = response.data
    
    if (res.code === 200) {
      return res
    } else {
      // 如果是"小组名称不能为空"的错误，不显示弹窗（用于获取班级和小组列表的场景）
      if (res.message !== '小组名称不能为空') {
        ElMessage.error(res.message || '请求失败')
      }
      return Promise.reject(new Error(res.message || '请求失败'))
    }
  },
  error => {
    if (error.response && error.response.status === 401) {
      // 未授权，清除用户信息并跳转到登录页
      localStorage.removeItem('userInfo')
      ElMessage.error('登录已过期，请重新登录')
      window.location.href = '/login'
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端服务或网络连接')
    } else if (!error.response) {
      ElMessage.error('无法连接后端服务，请确认 8080 端口已启动')
    } else {
      ElMessage.error(error.message || '网络错误')
    }
    return Promise.reject(error)
  }
)

export default request 
