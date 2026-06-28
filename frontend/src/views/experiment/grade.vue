<template>
  <div class="experiment-grade">
    <template v-if="!isStudent">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
            <span>成绩查看（按时间表）</span>
          <div class="header-actions">
              <el-select v-model="selectedSemesterId" placeholder="选择学期" style="width: 200px" @change="reloadCalendar">
                <el-option v-for="s in semesterOptions" :key="s.semesterId" :label="s.semesterName" :value="s.semesterId" />
              </el-select>
              <el-select v-model="selectedSuiteId" placeholder="选择实验套" style="width: 240px" @change="reloadCalendar">
                <el-option v-for="s in suiteOptions" :key="s.experimentSuiteId || s.suiteId" :label="s.suiteName" :value="s.experimentSuiteId || s.suiteId" />
              </el-select>
              <el-button type="primary" @click="() => reloadCalendar(true)">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
            <el-button type="success" @click="handleExportExcel" :loading="exporting">
              导出Excel
            </el-button>
          </div>
        </div>
      </template>

      <!-- 快速筛选区域 -->
      <div class="filter-section">
        <el-form :inline="true" :model="filterForm" class="grade-filter-form">
          <el-form-item label="快速查找：">
            <el-input 
              v-model="filterForm.studentName" 
              placeholder="输入学生姓名" 
              style="width: 200px;"
              clearable
              @input="handleQuickSearch"
              @keyup.enter="handleQuickSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="学号：">
            <el-input 
              v-model="filterForm.studentId" 
              placeholder="输入学号" 
              style="width: 150px;"
              clearable
              @input="handleQuickSearch"
              @keyup.enter="handleQuickSearch"
            />
          </el-form-item>
          <el-form-item label="班级：">
            <el-select 
              v-model="filterForm.classId" 
              placeholder="选择班级" 
              style="width: 150px;"
              clearable
              @change="handleQuickSearch"
            >
              <el-option 
                v-for="classId in classList" 
                :key="classId" 
                :label="classId" 
                :value="classId" 
              />
            </el-select>
          </el-form-item>
          <el-form-item label="小组：">
            <el-select 
              v-model="filterForm.groupName" 
              placeholder="选择小组" 
              style="width: 120px;"
              clearable
              @change="handleQuickSearch"
            >
              <el-option 
                v-for="group in groupList" 
                :key="group" 
                :label="group" 
                :value="group" 
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="handleQuickSearch">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="handleClearFilter">
              <el-icon><RefreshRight /></el-icon>
              清空筛选
            </el-button>
          </el-form-item>
        </el-form>
        <div v-if="searchMatchCount > 0" class="search-result-tip">
          <el-icon><InfoFilled /></el-icon>
          找到 <strong>{{ searchMatchCount }}</strong> 个匹配的小组（已高亮显示）
        </div>
      </div>

        <!-- 单周课表 -->
        <div class="calendar-wrapper">
          <div class="calendar-title">单周</div>
          <div class="calendar-grid compact">
            <div class="calendar-header"></div>
            <div class="calendar-header" v-for="d in weekdays" :key="'odd-' + d">{{ d }}</div>
            <template v-for="slot in timeSlots" :key="'odd-' + slot">
              <div class="calendar-slot-label">{{ slot }}</div>
              <div v-for="d in weekdays" :key="'odd-' + d + '-' + slot" class="calendar-cell compact">
                <div class="class-box">
                  <div class="group-results compact">
                    <template v-if="(cellGroupsOdd[d]?.[slot] || []).length">
                      <el-tag 
                        v-for="g in cellGroupsOdd[d][slot]" 
                        :key="g" 
                        :type="isGroupHighlighted(g) ? 'success' : 'info'" 
                        :class="['group-chip', 'clickable', { 'highlighted': isGroupHighlighted(g) }]" 
                        @click="openGroupScores(g, false, 0)"
                      >
                        {{ g }}
                      </el-tag>
                    </template>
                    <template v-else>
                      <span class="empty">—</span>
                    </template>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 双周课表 -->
        <div class="calendar-wrapper">
          <div class="calendar-title">双周</div>
          <div class="calendar-grid compact">
            <div class="calendar-header"></div>
            <div class="calendar-header" v-for="d in weekdays" :key="'even-' + d">{{ d }}</div>
            <template v-for="slot in timeSlots" :key="'even-' + slot">
              <div class="calendar-slot-label">{{ slot }}</div>
              <div v-for="d in weekdays" :key="'even-' + d + '-' + slot" class="calendar-cell compact">
                <div class="class-box">
                  <div class="group-results compact">
                    <template v-if="(cellGroupsEven[d]?.[slot] || []).length">
                      <el-tag 
                        v-for="g in cellGroupsEven[d][slot]" 
                        :key="g" 
                        :type="isGroupHighlighted(g) ? 'success' : 'info'" 
                        :class="['group-chip', 'clickable', { 'highlighted': isGroupHighlighted(g) }]" 
                        @click="openGroupScores(g, false, 1)"
                      >
                        {{ g }}
                      </el-tag>
                    </template>
                    <template v-else>
                      <span class="empty">—</span>
                    </template>
                  </div>
                </div>
              </div>
            </template>
          </div>
        </div>
      </el-card>

      <el-dialog v-model="dialogVisible" :title="`小组 ${currentGroupName} 成绩`" width="900px">
        <div class="grade-details">
          <div class="experiment-info">
      </div>
          <el-table :data="groupScoreRows" style="width: 100%" v-loading="loadingDialog">
            <el-table-column prop="schoolId" label="学号" width="120" />
            <el-table-column prop="studentName" label="姓名" width="100" />
            <el-table-column label="总成绩(均分)" width="120">
              <template #default="scope">
                <strong>{{ computeRowAvg(scope.row) }}</strong>
              </template>
            </el-table-column>
            <el-table-column v-for="e in suiteExperiments" :key="e.experimentId" :label="e.experimentName" min-width="100">
              <template #default="scope">
                <span>{{ scope.row.scores[String(e.experimentId)] ?? '-' }}</span>
              </template>
            </el-table-column>
      </el-table>
        </div>
        <template #footer>
          <span class="dialog-footer">
            <el-button type="primary" @click="dialogVisible = false">关闭</el-button>
          </span>
        </template>
      </el-dialog>
    </template>

    <!-- 学生端视图：按课表排列（列表） -->
    <template v-else>
      <el-card shadow="hover">
        <template #header>
          <div class="card-header">
            <span>我的成绩</span>
            <div class="header-actions">
              <el-button type="primary" @click="() => loadStudentData(true)">
                <el-icon><Refresh /></el-icon>
                刷新数据
              </el-button>
              <el-button type="success" @click="handleExportExcel" :loading="exporting">
                导出Excel
              </el-button>
            </div>
          </div>
        </template>
        <div class="summary-bar" v-if="studentScoreRows.length">
          总成绩(均分)：<strong>{{ studentAvgScore }}</strong>
      </div>
        <el-table :data="studentScoreRows" style="width: 100%" v-loading="loading">
          <el-table-column prop="experimentName" label="实验名称" min-width="240" />
          <el-table-column label="实验时间" width="220">
            <template #default="scope">
              {{ formatDateTime(scope.row.experimentTime) }}
            </template>
          </el-table-column>
          <el-table-column prop="location" label="实验地点" width="160" />
          <el-table-column prop="teacherName" label="任课教师" width="140" />
          <el-table-column prop="score" label="成绩" width="120" />
        </el-table>
    </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, RefreshRight, Refresh, InfoFilled } from '@element-plus/icons-vue'
import { getAllSuites } from '@/api/suite'
import { getScheduleList } from '@/api/schedule'
import { getExperimentsBySuite } from '@/api/experiment'
import { getStudentsByGroup, getGradeDetail, getGroupSuiteGrades, exportGradesXlsx } from '@/api/grade'
import { getStudentList } from '@/api/user'
import { getGroupExperimentByGroup } from '@/api/groupExperiment'
import { getUserInfo } from '@/api/auth'
import { getSemesterList, getCurrentSemester } from '@/api/semester'

const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isStudent = ref(userInfo.value.userType === 'student')

// 教师/管理员视图数据
const weekdays = ['周一','周二','周三','周四','周五']
const timeSlots = ['上午','下午']
const suiteOptions = ref([])
const selectedSuiteId = ref(null)
const semesterOptions = ref([])
const selectedSemesterId = ref(null)
const scheduleList = ref([])
let scheduleListSeq = 0
const cellGroups = reactive({})
const cellGroupsOdd = reactive({})
const cellGroupsEven = reactive({})
const dialogVisible = ref(false)
const currentGroupName = ref('')
const suiteExperiments = ref([])
const groupScoreRows = ref([])
const loading = ref(false)
const loadingDialog = ref(false)
const exporting = ref(false)

const handleExportExcel = async () => {
  exporting.value = true
  try {
    const params = {}
    if (!isStudent.value) {
      if (selectedSemesterId.value) params.semesterId = selectedSemesterId.value
      if (selectedSuiteId.value) params.suiteId = selectedSuiteId.value
    }
    const resp = await exportGradesXlsx(params)
    const blob = new Blob([resp.data], { type: resp.headers?.['content-type'] || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = isStudent.value ? 'my-grades.xlsx' : 'grades.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    ElMessage.success('开始导出')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

// 筛选相关数据
const filterForm = reactive({
  studentName: '',
  studentId: '',
  classId: '',
  groupName: ''
})
const classList = ref([])
const groupList = ref([])
const filteredGroups = ref([])

// 学生数据缓存和映射
const allStudentsData = ref([]) // 所有学生数据
const studentToGroupsMap = ref(new Map()) // 学生ID/姓名 -> 小组列表的映射
const highlightedGroups = ref(new Set()) // 需要高亮显示的小组集合
const searchMatchCount = ref(0) // 匹配的小组数量

// ===== 成绩数据缓存机制 =====
const gradeDataCache = ref({
  // 成绩数据缓存（全局，已废弃，改为按小组缓存）
  grades: null,
  gradesLastLoadTime: 0,
  // 小组安排数据缓存（按小组名存储）
  groupExperiments: new Map(), // Map<groupName, {data: [], timestamp: number}>
  // 学生列表缓存（按小组名存储）
  studentsByGroup: new Map(), // Map<groupName, {data: [], timestamp: number}>
  // 实验列表缓存（按实验套ID存储）
  experimentsBySuite: new Map(), // Map<suiteId, {data: [], timestamp: number}>
  // 小组成绩缓存（按小组名+学期+实验套存储，使用 getGradeDetail API 查询）
  groupGrades: new Map(), // Map<`${groupName}_${semesterId}_${suiteId}`, {data: [], timestamp: number}>
  // 课表数据缓存
  scheduleList: null,
  scheduleListLastLoadTime: 0,
  // 当前缓存对应的学期和实验套
  semesterId: null,
  suiteId: null,
  // 学生端缓存（按用户ID和小组名存储）
  studentData: new Map(), // Map<`${userId}_${groupName}`, {data: [], timestamp: number}>
  // 学生成绩缓存（按用户ID和小组名存储，使用 getGradeDetail API 查询）
  studentGrades: new Map(), // Map<`${userId}_${groupName}`, {data: [], timestamp: number}>
  // 班级和小组列表缓存（用于筛选功能）
  classList: null,
  groupList: null,
  allStudentsData: null,
  studentToGroupsMap: null,
  classAndGroupListLastLoadTime: 0
})

// 缓存有效期（30分钟）
const CACHE_DURATION = 30 * 60 * 1000

// 检查缓存是否有效
const isCacheValid = (timestamp, semesterId, suiteId) => {
  if (!timestamp || timestamp === 0) return false
  const isValidTime = (Date.now() - timestamp) < CACHE_DURATION
  // 如果 semesterId 和 suiteId 为 null，则不检查上下文（学生端场景）
  if (semesterId === null && suiteId === null) {
    return isValidTime
  }
  // 教师/管理员端需要检查上下文
  const isValidContext = gradeDataCache.value.semesterId === semesterId && 
                         gradeDataCache.value.suiteId === suiteId
  return isValidTime && isValidContext
}

// 清除缓存
const clearGradeCache = () => {
  gradeDataCache.value = {
    grades: null,
    gradesLastLoadTime: 0,
    groupExperiments: new Map(),
    studentsByGroup: new Map(),
    experimentsBySuite: new Map(),
    groupGrades: new Map(),
    scheduleList: null,
    scheduleListLastLoadTime: 0,
    semesterId: null,
    suiteId: null,
    studentData: new Map(),
    studentGrades: new Map(),
    classList: null,
    groupList: null,
    allStudentsData: null,
    studentToGroupsMap: null,
    classAndGroupListLastLoadTime: 0
  }
}

// 更新缓存上下文（学期和实验套）
const updateCacheContext = (semesterId, suiteId) => {
  // 只有当缓存中已经有值（不为 null/undefined），且新值与缓存值不同时，才清除缓存
  // 这样可以避免第一次加载时因为从 null 变为有值而清除缓存
  const currentSemesterId = gradeDataCache.value.semesterId
  const currentSuiteId = gradeDataCache.value.suiteId
  
  const semesterChanged = currentSemesterId != null && 
                          semesterId != null && 
                          currentSemesterId !== semesterId
  const suiteChanged = currentSuiteId != null && 
                       suiteId != null && 
                       currentSuiteId !== suiteId
  
  if (semesterChanged || suiteChanged) {
    clearGradeCache()
  }
  
  // 更新缓存上下文（允许从 null 变为有值，但不清除缓存）
  if (semesterId != null) {
    gradeDataCache.value.semesterId = semesterId
  }
  if (suiteId != null) {
    gradeDataCache.value.suiteId = suiteId
  }
}

// 预加载成绩数据（已废弃：不再预加载所有成绩，改为按需加载）
// 现在使用 getGradeDetail API 按小组和实验查询，不需要预加载
const preloadGradeData = async () => {
  // 不再预加载所有成绩，改为按需加载
  // 这样可以避免查询大量不必要的数据
}

const buildKey = (d, s) => `${d}${s}`
const ensureCellState = () => {
  weekdays.forEach(d => {
    if (!cellGroups[d]) cellGroups[d] = {}
    if (!cellGroupsOdd[d]) cellGroupsOdd[d] = {}
    if (!cellGroupsEven[d]) cellGroupsEven[d] = {}
    timeSlots.forEach(s => {
      if (!cellGroups[d][s]) cellGroups[d][s] = []
      if (!cellGroupsOdd[d][s]) cellGroupsOdd[d][s] = []
      if (!cellGroupsEven[d][s]) cellGroupsEven[d][s] = []
    })
  })
}

const loadSuites = async () => {
  try { const resp = await getAllSuites(); suiteOptions.value = resp?.data || []; if (!selectedSuiteId.value && suiteOptions.value.length) selectedSuiteId.value = suiteOptions.value[0].suiteId || suiteOptions.value[0].experimentSuiteId } catch { suiteOptions.value = [] }
}
const loadSemesters = async () => {
  try {
    // 优先获取“当前学期”作为默认值
    try {
      const cur = await getCurrentSemester()
      const cid = cur?.data?.semesterId
      if (cid) selectedSemesterId.value = cid
    } catch {}
    // 再加载列表，若当前学期不在列表中则回退到第一个
    const resp = await getSemesterList({ current: 1, size: 100 })
    const list = resp?.data?.records || resp?.data || []
    semesterOptions.value = list
    if (!selectedSemesterId.value && list.length) selectedSemesterId.value = list[0].semesterId
  } catch { semesterOptions.value = [] }
}
const parseScheduleTimeKey = (experimentTime) => { try { const data = typeof experimentTime === 'string' ? JSON.parse(experimentTime) : experimentTime; if (data && data.weekday && data.timeSlot) { return `${data.weekday}${data.timeSlot}` } } catch (e) { if (typeof experimentTime === 'string') { const m = experimentTime.match(/周[一二三四五][上下]午/); if (m) return m[0] } } return '' }
const parseGroupIds = (raw) => { if (!raw) return []; const cleaned = String(raw).replace(/^\[|\]$/g, ''); return cleaned.split(',').map(s => s && s.trim()).filter(Boolean) }
const loadSchedule = async (forceRefresh = false) => {
  const seq = ++scheduleListSeq
  try {
    ensureCellState()
    // 更新缓存上下文
    updateCacheContext(selectedSemesterId.value, selectedSuiteId.value)
    
    // 检查缓存
    if (!forceRefresh && isCacheValid(
      gradeDataCache.value.scheduleListLastLoadTime,
      selectedSemesterId.value,
      selectedSuiteId.value
    ) && gradeDataCache.value.scheduleList) {
      // 使用缓存数据（静默使用）
      const cachedList = gradeDataCache.value.scheduleList
      // 重新处理缓存数据填充到界面
      weekdays.forEach(d => timeSlots.forEach(s => { cellGroups[d][s] = []; cellGroupsOdd[d][s] = []; cellGroupsEven[d][s] = [] }))
      cachedList.forEach(item => {
        const key = parseScheduleTimeKey(item.experimentTime)
        if (!key) return
        const wt = item.weekType
        const weekday = key.slice(0, 2)
        const slot = key.slice(2)
        const groups = parseGroupIds(item.groupIds)
        if (wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined') {
          if (cellGroupsOdd[weekday] && cellGroupsOdd[weekday][slot]) {
            cellGroupsOdd[weekday][slot] = Array.from(new Set([ ...(cellGroupsOdd[weekday][slot] || []), ...groups ]))
          }
        } else if (wt === 1 || wt === '1') {
          if (cellGroupsEven[weekday] && cellGroupsEven[weekday][slot]) {
            cellGroupsEven[weekday][slot] = Array.from(new Set([ ...(cellGroupsEven[weekday][slot] || []), ...groups ]))
          }
        }
      })
      scheduleList.value = cachedList
      return
    }
    
    // 清空
    weekdays.forEach(d => timeSlots.forEach(s => { cellGroups[d][s] = []; cellGroupsOdd[d][s] = []; cellGroupsEven[d][s] = [] }))
    const params = { current: 1, size: 1000 }
    if (selectedSuiteId.value) params.suiteId = selectedSuiteId.value
    if (selectedSemesterId.value) params.semesterId = selectedSemesterId.value
    const scheduleResp = await getScheduleList(params)
    if (seq !== scheduleListSeq) return
    const allScheduleList = scheduleResp?.data?.records || []
    const oddList = allScheduleList.filter(item => {
      const wt = item.weekType
      return wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined'
    })
    oddList.forEach(item => {
      const key = parseScheduleTimeKey(item.experimentTime)
      if (!key) return
      // 仅接收 weekType=0 或未设置的记录到单周表
      const wt = item.weekType
      if (!(wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined')) return
      const weekday = key.slice(0, 2)
      const slot = key.slice(2)
      const groups = parseGroupIds(item.groupIds)
      if (cellGroupsOdd[weekday] && cellGroupsOdd[weekday][slot]) {
        cellGroupsOdd[weekday][slot] = Array.from(new Set([ ...(cellGroupsOdd[weekday][slot] || []), ...groups ]))
      }
    })
    // 双周
    const evenList = allScheduleList.filter(item => item.weekType === 1 || item.weekType === '1')
    evenList.forEach(item => {
      const key = parseScheduleTimeKey(item.experimentTime)
      if (!key) return
      // 仅接收 weekType=1 的记录到双周表
      const wt = item.weekType
      if (!(wt === 1 || wt === '1')) return
      const weekday = key.slice(0, 2)
      const slot = key.slice(2)
      const groups = parseGroupIds(item.groupIds)
      if (cellGroupsEven[weekday] && cellGroupsEven[weekday][slot]) {
        cellGroupsEven[weekday][slot] = Array.from(new Set([ ...(cellGroupsEven[weekday][slot] || []), ...groups ]))
      }
    })
    
    // 缓存课表数据（合并单周和双周）
    gradeDataCache.value.scheduleList = allScheduleList
    gradeDataCache.value.scheduleListLastLoadTime = Date.now()
    scheduleList.value = allScheduleList
  } catch {
    if (seq !== scheduleListSeq) return
    // 清空状态
    weekdays.forEach(d => timeSlots.forEach(s => { cellGroups[d][s] = []; cellGroupsOdd[d][s] = []; cellGroupsEven[d][s] = [] }))
  }
}
const reloadCalendar = async (forceRefresh = false) => {
  // 如果强制刷新，清除缓存
  if (forceRefresh) {
    clearGradeCache()
  }
  // 更新缓存上下文（学期/实验套变更时会自动清除缓存）
  updateCacheContext(selectedSemesterId.value, selectedSuiteId.value)
  
  await Promise.all([
    loadSchedule(forceRefresh),
    loadClassAndGroupList(forceRefresh)
  ])
  // 只在必要时加载班级和小组列表（有缓存且未过期时跳过）
  // 班级和小组列表不依赖于学期/实验套，所以可以使用长期缓存
  // 清空筛选条件（不显示消息）
  handleClearFilter(false)
  
  if (forceRefresh) {
    ElMessage.success('数据已刷新')
  }
}

const openGroupScores = async (groupName, forceRefresh = false, weekType = null) => {
  currentGroupName.value = groupName
  dialogVisible.value = true
  
  const startTime = Date.now()
  
  // 检查是否有完整的缓存可用（检查该小组的缓存）
  const groupGradesCacheKey = `${groupName}_${selectedSemesterId.value}_${selectedSuiteId.value}`
  const cachedGroupGrades = gradeDataCache.value.groupGrades?.get(groupGradesCacheKey)
  const hasFullCache = !forceRefresh && cachedGroupGrades && 
                       isCacheValid(cachedGroupGrades.timestamp, selectedSemesterId.value, selectedSuiteId.value)
  
  // 如果有完整缓存，不显示加载动画
  if (hasFullCache) {
    loadingDialog.value = false
  } else {
  loadingDialog.value = true
  }
  
  try {
    // 更新缓存上下文
    updateCacheContext(selectedSemesterId.value, selectedSuiteId.value)
    
    // 1. 加载实验列表（使用缓存）
    let all = []
    const suiteCacheKey = String(selectedSuiteId.value || '')
    const cachedExperiments = gradeDataCache.value.experimentsBySuite.get(suiteCacheKey)
    
    if (!forceRefresh && cachedExperiments && 
        isCacheValid(cachedExperiments.timestamp, selectedSemesterId.value, selectedSuiteId.value)) {
      // 使用缓存
      all = cachedExperiments.data
    } else {
      // 加载新数据
      const expStartTime = Date.now()
    const resp = await getExperimentsBySuite(selectedSuiteId.value)
      all = resp?.data || []
      // 更新缓存
      gradeDataCache.value.experimentsBySuite.set(suiteCacheKey, {
        data: all,
        timestamp: Date.now()
      })
    }
    
    // 2. 加载小组安排（使用缓存）
    let geList = []
    const groupExpCacheKey = groupName
    const cachedGroupExp = gradeDataCache.value.groupExperiments.get(groupExpCacheKey)
    
    if (!forceRefresh && cachedGroupExp && 
        isCacheValid(cachedGroupExp.timestamp, selectedSemesterId.value, selectedSuiteId.value)) {
      // 使用缓存
      geList = cachedGroupExp.data
    } else {
      // 加载新数据
      const geStartTime = Date.now()
    const ge = await getGroupExperimentByGroup(groupName)
      geList = Array.isArray(ge?.data) ? ge.data : []
      // 更新缓存
      gradeDataCache.value.groupExperiments.set(groupExpCacheKey, {
        data: geList,
        timestamp: Date.now()
      })
    }
    
    const allowedIds = new Set(geList.map(x => Number(x.experimentId)).filter(Boolean))
    // 若有过滤条件，则只保留在该小组安排中的实验
    if (allowedIds.size > 0) {
      all = all.filter(e => allowedIds.has(Number(e.experimentId)))
    }
    suiteExperiments.value = all
    
    // 3. 加载学生列表（使用缓存）
    let students = []
    // 缓存键需要包含 weekType，以区分单周和双周的学生列表
    const studentsCacheKey = weekType !== null && weekType !== undefined 
      ? `${groupName}_${weekType}` 
      : groupName
    const cachedStudents = gradeDataCache.value.studentsByGroup.get(studentsCacheKey)
    
    if (!forceRefresh && cachedStudents && 
        isCacheValid(cachedStudents.timestamp, selectedSemesterId.value, selectedSuiteId.value)) {
      // 使用缓存
      students = cachedStudents.data
    } else {
      // 加载新数据
      const stuResp = await getStudentsByGroup(groupName, selectedSemesterId.value, selectedSuiteId.value, weekType)
    
      if (stuResp?.code !== 200) {
        throw new Error(`API返回错误: ${stuResp?.message || '未知错误'}`)
      }
    
      students = stuResp?.data?.students || []

      if ((!students || students.length === 0) && stuResp?.data?.reason) {
        ElMessage.warning(stuResp.data.reason)
      }
    
    if (!Array.isArray(students)) {
      throw new Error('学生数据格式错误: 期望数组格式')
    }
      
      // 更新缓存
      gradeDataCache.value.studentsByGroup.set(studentsCacheKey, {
        data: students,
        timestamp: Date.now()
      })
    }
    
    // 4. 加载成绩数据（优化：使用 getGradeDetail API 按实验并行查询，参考成绩录入页面）
    // 这样可以避免查询所有成绩（5000条），只查询该小组相关实验的成绩
    // 获取需要查询成绩的实验ID：优先使用当前实验套下的全部实验，避免学生换组后因小组实验配置不同导致“成绩空”。
    const suiteExperimentIds = (suiteExperiments.value || []).map(e => Number(e.experimentId)).filter(Boolean)
    const experimentIds = suiteExperimentIds.length > 0
      ? suiteExperimentIds
      : (allowedIds.size > 0 ? Array.from(allowedIds) : [])
    
    let grades = []
    // 使用函数开头定义的 groupGradesCacheKey 和 cachedGroupGrades
    // 先尝试使用该小组的缓存
    if (!forceRefresh && cachedGroupGrades && 
        isCacheValid(cachedGroupGrades.timestamp, selectedSemesterId.value, selectedSuiteId.value)) {
      // 使用该小组的缓存
      grades = cachedGroupGrades.data
    } else {
      // 优化策略：使用 getGradeDetail API 按实验并行查询（参考成绩录入页面）
      // ⚠️ 重要：不再使用 getGradeList 查询所有成绩，改为按实验查询
      const gradeStartTime = Date.now()
      
      if (experimentIds.length > 0) {
        const result = await getGroupSuiteGrades({
          groupName,
          semesterId: selectedSemesterId.value,
          suiteId: selectedSuiteId.value
        })
        grades = (result?.data || [])
          .filter(item => item.score !== null && item.score !== undefined)
          .map(item => ({
            userId: item.userId,
            experimentId: item.experimentId,
            groupName,
            score: item.score,
            isLocked: item.isLocked || false
          }))
      } else {
        // 如果没有实验ID，返回空数组
        console.warn(`[缓存调试] ⚠️ 没有实验ID，无法查询成绩`)
        grades = []
      }
      
      // 缓存该小组的成绩（按小组缓存）
      if (!gradeDataCache.value.groupGrades) {
        gradeDataCache.value.groupGrades = new Map()
      }
      gradeDataCache.value.groupGrades.set(groupGradesCacheKey, {
        data: grades,
        timestamp: Date.now()
      })
    }
    
    // 5. 组合数据（优化：grades 已经是该小组的成绩，不需要再次过滤）
    const combineStartTime = Date.now()
    const rows = students.map(s => ({ userId: s.userId, schoolId: s.schoolId, studentName: s.studentName, groupName: groupName, scores: {} }))
    const userIdToRow = new Map(rows.map(r => [String(r.userId), r]))
    
    // grades 已经是该小组的成绩，直接组合即可
    grades.forEach(g => { 
      if (userIdToRow.has(String(g.userId))) { 
        const row = userIdToRow.get(String(g.userId)); 
        if (!row.scores) row.scores = {}; 
        // 过滤掉"N"成绩，不显示在教师查看界面
        if (g.score !== 'N' && g.score !== null && g.score !== undefined) {
          row.scores[String(g.experimentId)] = g.score 
        }
      } 
    })
    groupScoreRows.value = rows
  } catch (e) { 
    console.error('加载小组成绩失败:', e)
    ElMessage.error('加载小组成绩失败')
    groupScoreRows.value = [] 
  } finally { 
    loadingDialog.value = false 
  }
}

// 学生视图数据
const studentScoreRows = ref([])
const loadStudentData = async (forceRefresh = false) => {
  if (!userInfo.value || !userInfo.value.userId) return
  loading.value = true
  try {
    let groupName = userInfo.value.groupName || ''
    if (!groupName) {
      const resp = await getUserInfo()
      const ui = resp?.data || {}
      if (ui && ui.groupName) {
        userInfo.value = ui
        localStorage.setItem('userInfo', JSON.stringify(ui))
        groupName = ui.groupName
      }
    }
    if (!groupName) {
      studentScoreRows.value = []
      loading.value = false
      return
    }

    // 检查学生数据缓存
    const studentCacheKey = `${userInfo.value.userId}_${groupName}`
    const cachedStudentData = gradeDataCache.value.studentData.get(studentCacheKey)
    
    if (!forceRefresh && cachedStudentData && 
        isCacheValid(cachedStudentData.timestamp, null, null)) {
      // 使用缓存数据（静默使用，不显示提示）
      studentScoreRows.value = cachedStudentData.data
      loading.value = false
      return
    }

    // 1) 小组安排（使用缓存）
    let groupExps = []
    const groupExpCacheKey = groupName
    const cachedGroupExp = gradeDataCache.value.groupExperiments.get(groupExpCacheKey)
    
    if (!forceRefresh && cachedGroupExp && 
        isCacheValid(cachedGroupExp.timestamp, null, null)) {
      groupExps = cachedGroupExp.data
    } else {
    const geResp = await getGroupExperimentByGroup(groupName)
      groupExps = geResp?.data || []
      // 更新缓存
      gradeDataCache.value.groupExperiments.set(groupExpCacheKey, {
        data: groupExps,
        timestamp: Date.now()
      })
    }
    
    // 只保留"第X周"格式的记录，避免早期数据里的"周一上午"混入
    groupExps = groupExps.filter(e => typeof e.experimentTime === 'string' && /第\d+周/.test(e.experimentTime))
    // 去重（按 experimentId）
    const seen = new Set()
    groupExps = groupExps.filter(e => {
      const key = String(e.experimentId)
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })

    // 2) 该小组所在时段（周一上午/周二下午）从课表获取（使用缓存）
    let schedules = []
    if (!forceRefresh && isCacheValid(
      gradeDataCache.value.scheduleListLastLoadTime,
      null,
      null
    ) && gradeDataCache.value.scheduleList) {
      schedules = gradeDataCache.value.scheduleList
    } else {
    const schResp = await getScheduleList({ current: 1, size: 1000 })
      schedules = schResp?.data?.records || []
      // 更新缓存
      gradeDataCache.value.scheduleList = schedules
      gradeDataCache.value.scheduleListLastLoadTime = Date.now()
    }
    
    let daySlot = ''
    for (const s of schedules) {
      const raw = s.groupIds
      if (!raw) continue
      const cleaned = String(raw).replace(/^\[|\]$/g, '')
      const arr = cleaned.split(',').map(x => x && x.trim()).filter(Boolean)
      if (arr.includes(groupName)) {
        // s.experimentTime 形如 周一上午
        try {
          const data = typeof s.experimentTime === 'string' ? JSON.parse(s.experimentTime) : s.experimentTime
          if (data && data.weekday && data.timeSlot) daySlot = `${data.weekday}${data.timeSlot}`
          else if (typeof s.experimentTime === 'string') daySlot = s.experimentTime
        } catch (e) {
          daySlot = s.experimentTime || ''
        }
        break
      }
    }

    // 3) 该学生成绩（优化：使用 getGradeDetail API 按实验查询，而不是查询所有成绩）
    // 获取该小组的所有实验ID
    const experimentIds = groupExps.map(e => Number(e.experimentId)).filter(Boolean)
    
    let grades = []
    const studentGradesCacheKey = `${userInfo.value.userId}_${groupName}`
    const cachedStudentGrades = gradeDataCache.value.studentGrades?.get(studentGradesCacheKey)
    
    if (!forceRefresh && cachedStudentGrades && 
        isCacheValid(cachedStudentGrades.timestamp, null, null)) {
      // 使用缓存
      grades = cachedStudentGrades.data
    } else if (experimentIds.length > 0) {
      // 使用 getGradeDetail API 按实验并行查询（只查询该学生的成绩）
      const gradeStartTime = Date.now()
      
      const gradePromises = experimentIds.map(experimentId => 
        getGradeDetail({
          groupName: groupName,
          experimentId: experimentId,
          semesterId: null, // 学生端不需要学期过滤
          suiteId: null
        }).catch(error => {
          console.warn(`[缓存调试-学生端] 查询实验 ${experimentId} 的成绩失败:`, error)
          return { code: 500, data: { students: [] } }
        })
      )
      
      const gradeResults = await Promise.all(gradePromises)
      
      // 合并所有实验的成绩数据，只保留该学生的成绩
      grades = []
      gradeResults.forEach((result, index) => {
        if (result?.code === 200 && result.data?.students) {
          const experimentId = experimentIds[index]
          const studentGrade = result.data.students.find(s => 
            Number(s.userId) === Number(userInfo.value.userId)
          )
          if (studentGrade && studentGrade.score !== null && studentGrade.score !== undefined) {
            grades.push({
              userId: userInfo.value.userId,
              experimentId: experimentId,
              score: studentGrade.score
            })
          }
        }
      })
      
      // 缓存学生成绩
      if (!gradeDataCache.value.studentGrades) {
        gradeDataCache.value.studentGrades = new Map()
      }
      gradeDataCache.value.studentGrades.set(studentGradesCacheKey, {
        data: grades,
        timestamp: Date.now()
      })
    }
    
    const scoreMap = new Map(grades.map(g => [String(g.experimentId), g.score]))

    // 4) 组合行：时间为 第X周 + 周X上午，过滤掉"N"成绩的实验
    const rows = groupExps
      .filter(e => {
        const score = scoreMap.get(String(e.experimentId))
        return score !== 'N' && score !== null && score !== undefined
      })
      .map(e => ({
        experimentName: e.experimentName,
        experimentTime: `${e.experimentTime || ''}${daySlot ? daySlot : ''}`,
        location: e.location || '',
        teacherName: e.teacherName || '',
        score: scoreMap.get(String(e.experimentId)) ?? '-'
      }))
    
    studentScoreRows.value = rows
    
    // 缓存学生数据
    gradeDataCache.value.studentData.set(studentCacheKey, {
      data: rows,
      timestamp: Date.now()
    })
    
    if (forceRefresh) {
      ElMessage.success('数据已刷新')
    }
  } catch (e) { 
    ElMessage.error('加载我的成绩失败'); 
    studentScoreRows.value = [] 
  } finally { 
    loading.value = false 
  }
}

const computeRowAvg = (row) => {
  if (!row || !suiteExperiments.value || !suiteExperiments.value.length) return '-'
  const scores = suiteExperiments.value
    .map(e => {
      const score = row.scores?.[String(e.experimentId)]
      // 过滤掉"N"成绩，不参与计算
      return score === 'N' ? null : Number(score)
    })
    .filter(v => !Number.isNaN(v) && v !== null)
  if (!scores.length) return '-'
  const avg = scores.reduce((a, b) => a + b, 0) / scores.length
  return Math.round(avg * 10) / 10
}

const studentAvgScore = computed(() => {
  const nums = studentScoreRows.value
    .map(r => Number(r.score))
    .filter(v => !Number.isNaN(v) && v !== 'N')
  if (!nums.length) return '-'
  const avg = nums.reduce((a, b) => a + b, 0) / nums.length
  return Math.round(avg * 10) / 10
})

// 统一时间格式化：传入可能为 ISO 字符串或自定义字符串
const formatDateTime = (val) => {
  if (!val) return ''
  // 如果已经是“第X周周X上午”这类文本，直接返回
  if (typeof val === 'string' && (val.includes('第') || /周[一二三四五六日][上下]午/.test(val))) return val
  try {
    const d = new Date(val)
    if (!isNaN(d.getTime())) {
      const y = d.getFullYear()
      const m = String(d.getMonth() + 1).padStart(2, '0')
      const day = String(d.getDate()).padStart(2, '0')
      const hh = String(d.getHours()).padStart(2, '0')
      const mm = String(d.getMinutes()).padStart(2, '0')
      const ss = String(d.getSeconds()).padStart(2, '0')
      return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
    }
  } catch (e) { /* ignore */ }
  return String(val)
}

// 防抖函数
const debounce = (fn, wait = 300) => {
  let timer = null
  return function(...args) {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn.apply(this, args), wait)
  }
}

// 筛选相关函数
let studentSearchSeq = 0
const loadStudentsForFilter = async () => {
  const needsStudentLookup = filterForm.studentName || filterForm.studentId || filterForm.classId
  if (!needsStudentLookup) {
    allStudentsData.value = []
    return
  }

  const seq = ++studentSearchSeq
  try {
    const response = await getStudentList({
      current: 1,
      size: 500,
      realName: filterForm.studentName || undefined,
      schoolId: filterForm.studentId || undefined,
      classId: filterForm.classId || undefined
    })
    if (seq !== studentSearchSeq) return
    const students = response?.data?.records || []
    allStudentsData.value = students.map(student => ({
      userId: student.userId,
      schoolId: student.schoolId,
      studentName: student.realName || student.studentName,
      realName: student.realName || student.studentName,
      classId: student.classId,
      groupName: student.groupName
    }))
  } catch {
    if (seq === studentSearchSeq) allStudentsData.value = []
  }
}

const handleQuickSearch = debounce(async () => {
  await loadStudentsForFilter()
  applyFilter(false)
}, 300)

const handleAdvancedSearch = () => {
  // 可以扩展为打开高级搜索对话框
  applyFilter()
}

const handleClearFilter = (showMessage = true) => {
  filterForm.studentName = ''
  filterForm.studentId = ''
  filterForm.classId = ''
  filterForm.groupName = ''
  highlightedGroups.value.clear()
  searchMatchCount.value = 0
  if (showMessage) {
    ElMessage.info('已清空筛选条件')
  }
}

// 判断小组是否应该高亮
const isGroupHighlighted = (groupName) => {
  return highlightedGroups.value.has(groupName)
}

// 应用筛选逻辑
const applyFilter = (showMessage = false) => {
  // 清空之前的高亮
  highlightedGroups.value.clear()
  searchMatchCount.value = 0

  // 如果没有筛选条件，直接返回
  const hasFilter = filterForm.studentName || filterForm.studentId || filterForm.classId || filterForm.groupName
  if (!hasFilter) {
    return
  }

  // 用于收集匹配的小组
  const matchedGroups = new Set()

  // 1. 如果直接选择了小组名称，直接高亮该小组
  if (filterForm.groupName) {
    matchedGroups.add(filterForm.groupName)
  }

  // 2. 根据学生信息查找小组
  if (filterForm.studentName || filterForm.studentId || filterForm.classId) {
    allStudentsData.value.forEach(student => {
      let isMatch = true

      // 按姓名筛选
      if (filterForm.studentName) {
        const studentName = String(student.studentName || student.realName || '').trim()
        const searchName = filterForm.studentName.trim()
        if (!studentName.includes(searchName)) {
          isMatch = false
        }
      }

      // 按学号筛选
      if (filterForm.studentId && isMatch) {
        const studentId = String(student.schoolId || '').trim()
        const searchId = filterForm.studentId.trim()
        if (!studentId.includes(searchId)) {
          isMatch = false
        }
      }

      // 按班级筛选
      if (filterForm.classId && isMatch) {
        const studentClass = String(student.classId || '').trim()
        const searchClass = filterForm.classId.trim()
        if (studentClass !== searchClass) {
          isMatch = false
        }
      }

      // 如果学生匹配，添加到匹配的小组
      if (isMatch && student.groupName) {
        matchedGroups.add(student.groupName)
      }
    })
  }

  // 更新高亮小组集合
  highlightedGroups.value = matchedGroups
  searchMatchCount.value = matchedGroups.size

  // 显示匹配结果提示（避免在初始化时显示）
  if (showMessage && matchedGroups.size > 0) {
    ElMessage.success(`找到 ${matchedGroups.size} 个匹配的小组`)
  } else if (showMessage && hasFilter) {
    ElMessage.warning('未找到匹配的小组')
  }
}

// 加载班级和小组列表，同时加载所有学生数据（添加缓存机制和防重复调用）
let isLoadingClassAndGroupList = false // 防止重复调用

const loadClassAndGroupList = async (forceRefresh = false) => {
  // 防止重复调用
  if (isLoadingClassAndGroupList) {
    return
  }
  
  // 检查缓存（班级和小组列表缓存1小时，因为数据变化不频繁）
  const CACHE_DURATION_CLASS_GROUP = 60 * 60 * 1000 // 1小时
  const cacheAge = Date.now() - (gradeDataCache.value.classAndGroupListLastLoadTime || 0)
  
  // 即使没有学生数据，只要有班级和小组列表，也可以使用缓存
  if (!forceRefresh && 
      gradeDataCache.value.classList && 
      gradeDataCache.value.groupList &&
      cacheAge < CACHE_DURATION_CLASS_GROUP) {
    // 使用缓存数据
    classList.value = gradeDataCache.value.classList
    groupList.value = gradeDataCache.value.groupList
    filteredGroups.value = [...groupList.value]
    // 如果有学生数据缓存，也使用
    if (gradeDataCache.value.allStudentsData) {
      allStudentsData.value = gradeDataCache.value.allStudentsData
      studentToGroupsMap.value = gradeDataCache.value.studentToGroupsMap || new Map()
    }
    return
  }
  
  isLoadingClassAndGroupList = true
  const loadStartTime = Date.now()
  
  try {
    // 使用 /user/studentList API 获取所有学生数据（不传groupName参数）
    // 这个API返回分页数据，设置较大的size来获取所有学生
    const response = await getStudentList({
      current: 1,
      size: 1, // 首屏只取筛选选项；学生数据在实际搜索时按需加载
      // 不传groupName参数，获取所有学生
    })
    
    if (response.code === 200 && response.data) {
      // 获取班级和小组列表（即使学生数据为空，也要缓存这些）
      const newClassList = response.data.classList || []
      const newGroupList = response.data.groupList || []
      
      // 先更新班级和小组列表的缓存和响应式数据（即使学生数据为空）
      gradeDataCache.value.classList = newClassList
      gradeDataCache.value.groupList = newGroupList
      classList.value = newClassList
      groupList.value = newGroupList
      filteredGroups.value = [...newGroupList]
      
      // 获取学生列表（从分页数据中）
      const students = response.data.records || []
      
      if (students.length > 0) {
        // 转换学生数据格式，确保字段名称一致
        const newAllStudentsData = students.map(student => ({
          userId: student.userId,
          schoolId: student.schoolId,
          studentName: student.realName || student.studentName,
          realName: student.realName || student.studentName,
          classId: student.classId,
          groupName: student.groupName
        }))
        
        // 建立学生到小组的映射
        const newStudentToGroupsMap = new Map()
        newAllStudentsData.forEach(student => {
          if (student.groupName) {
            const studentKey = String(student.schoolId || student.userId || '')
            const studentName = String(student.studentName || student.realName || '').trim().toLowerCase()
            
            // 按学号/ID映射
            if (studentKey) {
              if (!newStudentToGroupsMap.has(studentKey)) {
                newStudentToGroupsMap.set(studentKey, new Set())
              }
              newStudentToGroupsMap.get(studentKey).add(student.groupName)
            }
            
            // 按姓名映射（转换为小写便于搜索）
            if (studentName) {
              if (!newStudentToGroupsMap.has(studentName)) {
                newStudentToGroupsMap.set(studentName, new Set())
              }
              newStudentToGroupsMap.get(studentName).add(student.groupName)
            }
          }
        })
        
        // 更新学生数据缓存和响应式数据
        gradeDataCache.value.allStudentsData = newAllStudentsData
        gradeDataCache.value.studentToGroupsMap = newStudentToGroupsMap
        allStudentsData.value = newAllStudentsData
        studentToGroupsMap.value = newStudentToGroupsMap
        
        // 更新缓存时间
        gradeDataCache.value.classAndGroupListLastLoadTime = Date.now()
      } else {
        console.warn(`[缓存调试] 未获取到学生数据，但已缓存班级和小组列表（${newClassList.length} 个班级，${newGroupList.length} 个小组）`)
        // 即使学生数据为空，也更新缓存时间（这样下次就不会重复调用）
        gradeDataCache.value.classAndGroupListLastLoadTime = Date.now()
        
        // 只有在确实需要学生数据时才调用备用方案（如果缓存中没有学生数据）
        if (!gradeDataCache.value.allStudentsData && newGroupList.length > 0) {
          try {
            await loadAllStudentsByGroups(forceRefresh)
            // 备用方案成功后，更新缓存时间
            if (allStudentsData.value && allStudentsData.value.length > 0) {
              gradeDataCache.value.allStudentsData = allStudentsData.value
              gradeDataCache.value.studentToGroupsMap = studentToGroupsMap.value
              gradeDataCache.value.classAndGroupListLastLoadTime = Date.now()
            }
          } catch (backupError) {
            console.warn(`[缓存调试] 备用方案失败，但班级和小组列表已缓存:`, backupError)
          }
        } else if (gradeDataCache.value.allStudentsData) {
          // 如果缓存中有学生数据，直接使用
          allStudentsData.value = gradeDataCache.value.allStudentsData
          studentToGroupsMap.value = gradeDataCache.value.studentToGroupsMap || new Map()
        }
      }
    } else {
      console.warn('API返回数据格式异常，尝试使用缓存或备用方案...')
      // 如果有缓存，先使用缓存
      if (gradeDataCache.value.classList && gradeDataCache.value.groupList) {
        classList.value = gradeDataCache.value.classList
        groupList.value = gradeDataCache.value.groupList
        filteredGroups.value = [...groupList.value]
        if (gradeDataCache.value.allStudentsData) {
          allStudentsData.value = gradeDataCache.value.allStudentsData
          studentToGroupsMap.value = gradeDataCache.value.studentToGroupsMap || new Map()
        }
        return
      }
      // 如果缓存也没有，尝试备用方案
      await loadAllStudentsByGroups(forceRefresh)
    }
  } catch (error) {
    console.error('加载班级和小组列表失败:', error)
    // 如果主方案失败，但缓存中有数据，继续使用缓存
    if (gradeDataCache.value.classList && gradeDataCache.value.groupList) {
      console.warn('使用缓存的班级和小组列表数据（API调用失败）')
      classList.value = gradeDataCache.value.classList
      groupList.value = gradeDataCache.value.groupList
      filteredGroups.value = [...groupList.value]
      if (gradeDataCache.value.allStudentsData) {
        allStudentsData.value = gradeDataCache.value.allStudentsData
        studentToGroupsMap.value = gradeDataCache.value.studentToGroupsMap || new Map()
      }
    } else {
      // 如果缓存也没有，尝试备用方案
    console.warn('尝试使用备用方案加载学生数据...')
      try {
        await loadAllStudentsByGroups(forceRefresh)
        // 备用方案成功后，更新缓存
        if (allStudentsData.value && allStudentsData.value.length > 0) {
          gradeDataCache.value.allStudentsData = allStudentsData.value
          gradeDataCache.value.studentToGroupsMap = studentToGroupsMap.value
          gradeDataCache.value.classAndGroupListLastLoadTime = Date.now()
        }
    } catch (backupError) {
      console.error('备用方案也失败:', backupError)
      ElMessage.warning('加载学生数据失败，快速查找功能可能不可用')
    }
    }
  } finally {
    isLoadingClassAndGroupList = false
  }
}

// 通过遍历所有小组来加载学生数据（备用方案，尽量避免使用）
const loadAllStudentsByGroups = async (forceRefresh = false) => {
  // ⚠️ 警告：这个函数会为每个小组调用一次API，非常慢！
  // 只有在主方案失败且没有缓存时才使用
  console.warn('[性能警告] 使用备用方案加载学生数据，这会为每个小组调用一次API，可能很慢！')
  
  try {
    const studentsList = []
    
    // 先尝试获取小组列表（如果没有的话）
    if (groupList.value.length === 0) {
      try {
        const listResp = await getStudentList({ current: 1, size: 1 })
        if (listResp?.code === 200 && listResp?.data) {
          const newGroupList = listResp.data.groupList || []
          const newClassList = listResp.data.classList || []
          groupList.value = newGroupList
          classList.value = newClassList
        }
      } catch (e) {
        console.warn('获取小组列表失败:', e)
      }
    }
    
    // 遍历所有小组加载学生（这是性能瓶颈！）
    if (groupList.value.length > 0) {
      const backupStartTime = Date.now()
      
      // 使用并行请求来加速（但仍然很慢）
      const groupPromises = groupList.value.map(async (groupName) => {
        try {
          const resp = await getStudentsByGroup(groupName)
          if (resp?.code === 200 && resp?.data) {
            // 处理不同的返回格式
            let students = []
            if (resp.data.students && Array.isArray(resp.data.students)) {
              students = resp.data.students
            } else if (Array.isArray(resp.data)) {
              students = resp.data
            }
            
            return students.map(s => {
              // 确保每个学生都有groupName字段
              if (!s.groupName) {
                s.groupName = groupName
              }
              // 统一字段名称
              s.studentName = s.studentName || s.realName
              s.realName = s.realName || s.studentName
              return s
            })
          }
          return []
        } catch (e) {
          console.warn(`加载小组 ${groupName} 的学生数据失败:`, e)
          return []
        }
      })
      
      const allGroupsStudents = await Promise.all(groupPromises)
      allGroupsStudents.forEach(students => {
        studentsList.push(...students)
      })
      
      // 建立学生到小组的映射
      const newStudentToGroupsMap = new Map()
      studentsList.forEach(student => {
        if (student.groupName) {
          const studentKey = String(student.schoolId || student.userId || '')
          const studentName = String(student.studentName || student.realName || '').trim().toLowerCase()
          
          // 按学号/ID映射
          if (studentKey) {
            if (!newStudentToGroupsMap.has(studentKey)) {
              newStudentToGroupsMap.set(studentKey, new Set())
            }
            newStudentToGroupsMap.get(studentKey).add(student.groupName)
          }
          
          // 按姓名映射（转换为小写便于搜索）
          if (studentName) {
            if (!newStudentToGroupsMap.has(studentName)) {
              newStudentToGroupsMap.set(studentName, new Set())
            }
            newStudentToGroupsMap.get(studentName).add(student.groupName)
          }
        }
      })
      
      // 更新数据
      allStudentsData.value = studentsList
      studentToGroupsMap.value = newStudentToGroupsMap
      console.warn('[性能警告] 建议检查主API是否正常工作，避免使用此备用方案')
    } else {
      console.warn('没有小组列表，无法加载学生数据')
    }
  } catch (error) {
    console.error('加载所有学生数据失败:', error)
    throw error
  }
}

onMounted(async () => {
  if (!isStudent.value) { 
    await Promise.all([
      loadSemesters(),
      loadSuites()
    ])
    await Promise.all([
      loadClassAndGroupList(),
      loadSchedule()
    ])
    
    // 预加载成绩数据（在后台立即开始，不阻塞界面）
    // 使用 nextTick 确保主要数据加载完成后再预加载
    setTimeout(() => {
      preloadGradeData()
    }, 500) // 减少延迟到500ms，让预加载更快开始
  } else { 
    await loadStudentData() 
  }
})
</script>

<style scoped>
.experiment-grade { 
  padding: 16px; 
  height: calc(100vh - 120px);
  overflow-y: auto;
}

/* 优化卡片间距 */
.experiment-grade .el-card {
  margin-bottom: 0;
}

/* 优化日历容器布局 */
.calendar-wrapper {
  margin-bottom: 12px;
  content-visibility: auto;
  contain-intrinsic-size: 320px;
}

.calendar-wrapper:last-child {
  margin-bottom: 0;
}
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; gap: 10px; }

.calendar-title { font-weight: 700; margin: 8px 0; color: #303133; }
.calendar-grid { display: grid; grid-template-columns: 120px repeat(5, 1fr); gap: 6px; }
.calendar-grid.compact { gap: 6px; }
.calendar-header { font-weight: 600; text-align: center; padding: 8px 0; font-size: 14px; }
.calendar-slot-label { display: flex; align-items: center; justify-content: center; font-weight: 600; background: #fafafa; border: 1px solid #eee; font-size: 14px; padding: 8px 0; }
.calendar-cell { border: 1px solid #e6e8eb; padding: 6px; min-height: 100px; background: #fff; border-radius: 6px; }
.calendar-cell.compact { padding: 6px; min-height: 100px; }

.class-box { min-width: 0; height: 100%; overflow: hidden; }
.group-results { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 6px; }
.group-results.compact { gap: 6px; }
.group-chip { border: 1px solid #dcdfe6; border-radius: 16px; padding: 6px 12px; line-height: 24px; text-align: center; background: #f6f8fb; color: #2f3640; }
.group-chip.clickable { cursor: pointer; }

.dialog-footer { display: flex; justify-content: flex-end; gap: 10px; }
.exp-tags { margin-top: 6px; }
.summary-bar { margin-bottom: 10px; color: #303133; }

/* 成绩查看页面字体优化 - 增大字体 */
.experiment-grade .card-header span {
  font-size: 20px;
  font-weight: 600;
}

.experiment-grade .calendar-title {
  font-size: 18px;
  font-weight: 700;
  margin: 12px 0;
}

.experiment-grade .calendar-header {
  font-size: 16px;
  font-weight: 600;
  padding: 10px 0;
}

.experiment-grade .calendar-slot-label {
  font-size: 16px;
  font-weight: 600;
  padding: 12px 0;
}

.experiment-grade .group-chip {
  font-size: 15px;
  font-weight: 500;
  padding: 8px 14px;
  line-height: 26px;
}

/* 成绩查看表格字体优化 - 增大字体 */
.experiment-grade .el-table {
  font-size: 18px;
}

.experiment-grade .el-table th {
  font-size: 18px;
  font-weight: 600;
  height: 50px;
}

.experiment-grade .el-table td {
  font-size: 18px;
  height: 55px;
}

/* 学号列加粗显示 */
.experiment-grade .el-table td:first-child {
  font-weight: 600;
}

/* 姓名列加粗显示 */
.experiment-grade .el-table td:nth-child(2) {
  font-weight: 600;
  color: #2c3e50;
}

/* 总成绩列特别突出 */
.experiment-grade .el-table td:nth-child(3) {
  font-size: 18px;
  font-weight: 700;
  color: #e6a23c;
}

/* 成绩数值加粗显示 */
.experiment-grade .el-table td span {
  font-weight: 500;
}

/* 对话框标题字体优化 */
.experiment-grade .el-dialog__title {
  font-size: 18px;
  font-weight: 600;
}

/* 筛选区域样式 */
.filter-section {
  background: #f8f9fa;
  padding: 16px;
  margin-bottom: 16px;
  border-radius: 8px;
  border: 1px solid #e9ecef;
}

.grade-filter-form {
  margin: 0;
}

.grade-filter-form .el-form-item {
  margin-bottom: 0;
  margin-right: 16px;
}

.grade-filter-form .el-form-item:last-child {
  margin-right: 0;
}

.grade-filter-form .el-form-item__label {
  font-weight: 600;
  color: #495057;
}

/* 筛选按钮样式 */
.grade-filter-form .el-button {
  margin-left: 8px;
}

.grade-filter-form .el-button:first-child {
  margin-left: 0;
}

/* 搜索结果显示 */
.search-result-tip {
  margin-top: 12px;
  padding: 8px 12px;
  background: #e8f5e9;
  border: 1px solid #81c784;
  border-radius: 4px;
  color: #2e7d32;
  font-size: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.search-result-tip .el-icon {
  font-size: 16px;
}

.search-result-tip strong {
  color: #1b5e20;
  font-weight: 600;
}

/* 高亮的小组标签样式 */
.group-chip.highlighted {
  background: #eaf7ee;
  border-color: #67c23a;
  color: #287443;
  font-weight: 600;
}
</style> 
