<template>
  <div class="app-wrapper" :class="{ 'mobile': isMobile }">
    <!-- 侧边栏 -->
    <div class="sidebar-container" :class="{ 'is-collapse': isCollapse, 'mobile-sidebar': isMobile }">
      <div class="logo">
        <img src="@/assets/logo.png" alt="Logo">
        <span v-show="!isCollapse && !isMobile">物理实验系统</span>
      </div>
      
      <el-menu
        :default-active="$route.path"
        :collapse="isCollapse || isMobile"
        background-color="#1f2937"
        text-color="#cbd5e1"
        active-text-color="#93c5fd"
        @select="handleMenuSelect"
      >
        <sidebar-item
          v-for="route in filteredRoutes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </div>

    <!-- 主容器 -->
    <div class="main-container" :class="{ 'mobile-main': isMobile }">
      <!-- 顶部导航栏 -->
      <div class="navbar" :class="{ 'mobile-navbar': isMobile }">
        <div class="left">
          <div class="hamburger" @click="toggleSidebar">
            <el-icon><Fold v-if="!isCollapse" /><Expand v-else /></el-icon>
          </div>
          <breadcrumb v-if="!isMobile" />
        </div>
        
        <div class="right">
          <el-dropdown @command="handleCommand">
            <div class="user-info">
              <el-avatar :size="32" :src="userInfo.avatar">
                {{ userInfo.realName?.charAt(0) }}
              </el-avatar>
              <span v-if="!isMobile">{{ userInfo.realName }}</span>
              <el-icon v-if="!isMobile"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>

      <!-- 移除密码修改提示，只在首页显示 -->
      
      <!-- 主内容区域 -->
      <div class="app-main" :class="{ 'mobile-app-main': isMobile }">
        <router-view />
      </div>
      <!-- 全局角落署名 -->
      <div class="app-credit">Designed by Hou Shuyang and Liu Jihan from iMakerLab | 2025</div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { logout } from '@/api/auth'
import { getUserInfo, hasPermission } from '@/utils/permission'
import SidebarItem from './components/SidebarItem.vue'
import Breadcrumb from './components/Breadcrumb.vue'

const router = useRouter()
const route = useRoute()
const isCollapse = ref(false)

// 获取用户信息
const userInfo = computed(() => getUserInfo())

// 移除密码检查相关代码

// 响应式检测
const isMobile = ref(false)

const checkMobile = () => {
  isMobile.value = window.innerWidth <= 768
  if (isMobile.value && !isCollapse.value) {
    isCollapse.value = true
  }
}

// 移除密码检查相关代码

// 在mounted时检查路由
onMounted(() => {
  // 路由检查完成
  checkMobile()
  window.addEventListener('resize', checkMobile)
  
  // 移除密码检查相关代码
  
  // imakerlab完成
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

// 获取过滤后的路由菜单
const filteredRoutes = computed(() => {
  const mainRoute = router.getRoutes().find(route => route.path === '/')
  
  if (mainRoute && mainRoute.children) {
    const allVisibleRoutes = mainRoute.children.filter(child => !child.meta?.hidden)
    
    const filtered = allVisibleRoutes
      .filter(child => {
        const hasPermissionResult = hasPermission(child)
        return hasPermissionResult
      })
      .sort((a, b) => {
        // 首页永远在最上面
        if (a.path === 'dashboard') return -1
        if (b.path === 'dashboard') return 1
        // 其他路由按标题排序
        return a.meta.title.localeCompare(b.meta.title)
      })
    return filtered
  }
  return []
})

// 切换侧边栏
const toggleSidebar = () => {
  if (isMobile.value) {
    // 移动端显示/隐藏侧边栏
    isCollapse.value = !isCollapse.value
  } else {
    // 桌面端折叠/展开侧边栏
    isCollapse.value = !isCollapse.value
  }
}

// 处理下拉菜单命令
const handleCommand = async (command) => {
  if (command === 'profile') {
    router.push('/profile/index')
  } else if (command === 'logout') {
    try {
      // 调用后端登出接口
      await logout()
      // 清除本地存储
      localStorage.removeItem('userInfo')
      ElMessage.success('登出成功')
      router.push('/login')
    } catch (error) {
      console.error('登出失败:', error)
      // 即使后端登出失败，也要清除本地存储并跳转
      localStorage.removeItem('userInfo')
      router.push('/login')
    }
  }
}

// 处理菜单选择
const handleMenuSelect = (index) => {
  try {
    // index已经是完整路径（如 /dashboard），直接使用
    router.push(index).catch((error) => {
      console.error('路由跳转失败:', error)
      ElMessage.error('页面跳转失败，请检查控制台错误信息')
    })
  } catch (error) {
    console.error('路由跳转异常:', error)
    ElMessage.error('页面跳转异常')
  }
}
</script>

<style scoped>
.app-wrapper {
  display: flex;
  height: 100vh;
}

.sidebar-container {
  width: 224px;
  background-color: #1f2937;
  transition: width 0.2s ease;
  overflow: hidden;
}

.sidebar-container.is-collapse {
  width: 64px;
}

.logo {
  height: 58px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  background-color: #111827;
  color: #fff;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

.logo img {
  width: 32px;
  height: 32px;
  margin-right: 12px;
}

.main-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  position: relative;
}

.navbar {
  height: 62px;
  background-color: #fff;
  border-bottom: 1px solid var(--color-border-light);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
}

.left {
  display: flex;
  align-items: center;
}

.hamburger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 6px;
  color: var(--color-text-secondary);
  font-size: 22px;
  cursor: pointer;
  margin-right: 20px;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.hamburger:hover {
  color: var(--color-primary);
  background: #eef5fc;
}

.user-info {
  display: flex;
  align-items: center;
  cursor: pointer;
  min-height: 40px;
  padding: 4px 8px;
  border-radius: 6px;
  color: var(--color-text);
  font-size: var(--base-font-size);
  transition: background-color 0.2s ease;
}

.user-info:hover {
  background: #f3f6fa;
}

.user-info span {
  margin: 0 8px;
}

.app-main {
  flex: 1;
  padding: 24px;
  background-color: var(--color-page);
  overflow-y: auto;
}

.app-credit {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 4px;
  font-size: 13px;
  color: #687182;
  user-select: none;
  pointer-events: none;
}

/* 移除密码提示条样式 */

/* 移动端响应式样式 */
/* imakerlab完成 */
@media (max-width: 768px) {
  .app-wrapper.mobile {
    position: relative;
  }
  
  .sidebar-container.mobile-sidebar {
    position: fixed;
    top: 0;
    left: 0;
    height: 100vh;
    z-index: 1000;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
  }
  
  .sidebar-container.mobile-sidebar:not(.is-collapse) {
    transform: translateX(0);
  }
  
  .main-container.mobile-main {
    margin-left: 0;
    width: 100%;
  }
  
  .navbar.mobile-navbar {
    padding: 0 10px;
  }
  
  .app-main.mobile-app-main {
    padding: 12px;
  }
  
  .hamburger {
    margin-right: 10px;
  }
  
  .user-info span {
    display: none;
  }
}
</style> 
