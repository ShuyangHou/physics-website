<template>
  <div class="student-schedule">
    <el-card>
      <template #header>
        <span>我的实验课表</span>
      </template>
      
      <div v-if="loading" class="loading-container">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      
      <div v-else-if="error" class="error-container">
        <el-alert
          :title="error"
          type="error"
          :closable="false"
          show-icon
        />
        <el-button type="primary" @click="loadSchedule" style="margin-top: 16px;">
          重新加载
            </el-button>
          </div>
      
      <div v-else-if="scheduleList.length === 0">
        <el-empty description="暂无实验安排" />
        </div>
      
      <div v-else>
        <el-table :data="scheduleList" stripe>
          <el-table-column prop="experimentName" label="实验名称" />
          <el-table-column prop="combinedTime" label="实验时间" />
          <el-table-column prop="location" label="实验地点" />
          <el-table-column prop="teacherName" label="任课教师" />
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import { getGroupExperimentByGroup } from '@/api/groupExperiment'
import { getUserInfo } from '@/api/auth'
import { getScheduleList } from '@/api/schedule'

const loading = ref(false)
const error = ref('')
const scheduleList = ref([])

const getCurrentUser = async () => {
  try {
    const response = await getUserInfo()
    if (response.code === 200) {
      return response.data
    } else {
      return null
    }
  } catch (e) {
    return null
  }
}

// 从课表中查该组所在的日时段（周几上午/下午）
const resolveDaySlotForGroup = async (groupName) => {
  try {
    const resp = await getScheduleList({ current: 1, size: 1000 })
    const records = resp?.data?.records || []
    for (const s of records) {
      const raw = s.groupIds
      if (!raw) continue
      const cleaned = String(raw).replace(/^\[|\]$/g, '')
      const arr = cleaned.split(',').map(x => x && x.trim()).filter(Boolean)
      if (arr.includes(groupName)) {
        try {
          const data = typeof s.experimentTime === 'string' ? JSON.parse(s.experimentTime) : s.experimentTime
          if (data && data.weekday && data.timeSlot) return `${data.weekday}${data.timeSlot}`
        } catch (e) {
          return s.experimentTime || ''
        }
      }
    }
  } catch (e) {
    // ignore
  }
  return ''
}

const loadSchedule = async () => {
  loading.value = true
  error.value = ''
  
  try {
    const currentUser = await getCurrentUser()
    if (!currentUser) {
      error.value = '无法获取用户信息，请重新登录'
      loading.value = false
      return
    }
    const groupName = currentUser.groupName
    if (!groupName) {
      error.value = '您还没有被分配到任何实验小组，请联系管理员'
      loading.value = false
      return
    }

    // 1) 获取小组安排
    const response = await getGroupExperimentByGroup(groupName)
    let items = (response.code === 200 ? (response.data || []) : [])

    // 2) 仅保留“第X周”记录，去掉早期“周一上午”格式
    items = items.filter(e => typeof e.experimentTime === 'string' && /第\d+周/.test(e.experimentTime))

    // 3) 去重（按 experimentId）
    const seen = new Set()
    items = items.filter(e => {
      const key = String(e.experimentId)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })

    // 4) 解析日时段并合并显示
    const daySlot = await resolveDaySlotForGroup(groupName)
    scheduleList.value = items.map(e => ({
      experimentName: e.experimentName,
      combinedTime: `${e.experimentTime || ''}${daySlot ? daySlot : ''}`,
      location: e.location || '',
      teacherName: e.teacherName || ''
    }))
  } catch (err) {
    error.value = '网络错误，请检查网络连接后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadSchedule()
})
</script>

<style scoped>
.student-schedule {
  padding: 20px;
}

.loading-container {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  color: #667085;
}

.loading-container .el-icon {
  margin-right: 8px;
  font-size: 20px;
}

.error-container {
  text-align: center;
  padding: 20px;
}
</style> 
