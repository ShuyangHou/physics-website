<template>
  <div class="grade-entry">
    <el-card shadow="hover" v-loading="initializing" element-loading-text="正在加载成绩录入页面...">
      <template #header>
        <div class="card-header">
          <span>成绩录入（按时间表）</span>
          <div class="header-actions">
            <el-button 
              v-if="userInfo.userType === 'admin'"
              plain
              @click="handleLockAllGrades" 
              :loading="lockingAll"
              :disabled="!currentSemester || !selectedSuiteId"
              style="margin-right: 10px;"
            >
              <el-icon><Lock /></el-icon>
              冻结所有成绩
            </el-button>
            <el-button 
              v-if="userInfo.userType === 'admin'"
              plain
              @click="handleUnlockAllGrades" 
              :loading="unlockingAll"
              :disabled="!currentSemester || !selectedSuiteId"
              style="margin-right: 10px;"
            >
              <el-icon><Unlock /></el-icon>
              解冻所有成绩
            </el-button>
            <el-select v-model="currentSemester" placeholder="选择学期" style="width: 200px; margin-right: 10px;" @change="handleSemesterChange">
              <el-option 
                v-for="semester in semesterList" 
                :key="semester.semesterId" 
                :label="semester.semesterName" 
                :value="semester.semesterId" 
              />
            </el-select>
            <el-select v-model="selectedSuiteId" placeholder="选择实验套" style="width: 220px; margin-right: 10px;" @change="handleSuiteChange">
              <el-option v-for="s in suiteOptions" :key="s.experimentSuiteId || s.suiteId" :label="s.suiteName" :value="s.experimentSuiteId || s.suiteId" />
            </el-select>
            <el-button type="primary" @click="handleRefresh" :loading="loading">
              <el-icon><Refresh /></el-icon>
              刷新数据
            </el-button>
            
          </div>
        </div>
      </template>

      <el-alert
        v-if="initializationError"
        class="initialization-alert"
        type="warning"
        :closable="false"
        show-icon
        title="部分数据加载失败"
        description="页面已显示，可点击“刷新数据”重试。请同时确认后端 8080 服务正常。"
      />

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
                      type="info" 
                      class="group-chip clickable" 
                      @click="openGradeEntry(g, 0)"
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
                      type="info" 
                      class="group-chip clickable" 
                      @click="openGradeEntry(g, 1)"
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

    <!-- 成绩详情对话框 -->
    <el-dialog
      v-model="gradeDialogVisible"
      title="学生成绩录入"
      width="95%"
      top="3vh"
      :close-on-click-modal="false"
      class="grade-entry-dialog"
    >
      <div class="grade-details" v-loading="dialogLoading" :element-loading-text="dialogLoadingText">
        <div class="experiment-info">
          <div class="experiment-info-head">
            <h4>小组与实验选择</h4>
            <el-tooltip placement="bottom-start" effect="dark">
              <template #content>
                <div style="line-height: 1.8;">
                  选择“N (不计分)”表示该实验不计入学生总成绩<br />
                  学生端不会看到标记为“N”的实验成绩<br />
                  总成绩计算时会自动排除“N”标记的实验
                </div>
              </template>
              <span class="grade-tips-inline"><el-icon><InfoFilled /></el-icon> 录入说明</span>
            </el-tooltip>
            <span class="grade-tips-inline keyboard-hint">
              <el-icon><Sort /></el-icon> 回车跳下一个 · Shift+回车上一个 · ↑/↓ 移动
            </span>
          </div>
          <el-row :gutter="20" class="selection-row">
            <el-col :span="8">
              <div class="selection-item">
                <label class="selection-label">选择小组</label>
                <el-select 
                  v-model="selectedGroupName" 
                  placeholder="请选择小组" 
                  style="width:100%" 
                  size="large"
                  @change="handleDialogGroupChange"
                >
                  <el-option v-for="g in dialogGroupOptions" :key="g" :label="g" :value="g" />
                </el-select>
              </div>
            </el-col>
            <el-col :span="12">
              <div class="selection-item">
                <label class="selection-label">选择实验</label>
                <el-select 
                  v-model="selectedExperimentId" 
                  placeholder="请选择要录入成绩的实验" 
                  filterable 
                  style="width:100%" 
                  size="large"
                  @change="handleExperimentChange"
                >
                  <el-option v-for="e in experimentList" :key="e.experimentId" :label="e.experimentName" :value="e.experimentId" />
                </el-select>
              </div>
            </el-col>
            <el-col :span="4">
              <div class="selection-item">
                <label class="selection-label">&nbsp;</label>
                <el-button type="primary" size="large" style="width: 100%" @click="loadGroupStudents" :loading="dialogLoading" :disabled="!selectedGroupName || !selectedExperimentId">
                  <el-icon><Refresh /></el-icon>
                  刷新数据
                </el-button>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <div class="table-actions">
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <div class="action-buttons">
              <el-button plain @click="handleDownloadTemplate" :disabled="!selectedGroupName || !selectedExperimentId" size="default">
                下载模板
              </el-button>
              <el-upload
                :show-file-list="false"
                :auto-upload="false"
                accept=".xlsx"
                :on-change="handleTemplateFileChange"
              >
                <el-button plain :disabled="!selectedGroupName || !selectedExperimentId" size="default">
                  导入模板
                </el-button>
              </el-upload>
              <el-dropdown trigger="click" @command="handleBatchCommand" :disabled="!selectedGroupName || !selectedExperimentId">
                <el-button plain :disabled="!selectedGroupName || !selectedExperimentId" size="default">
                  批量操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="setN"><el-icon><RemoveFilled /></el-icon>全部设为N (不计分)</el-dropdown-item>
                    <el-dropdown-item command="clear"><el-icon><Clock /></el-icon>清空所有成绩</el-dropdown-item>
                    <el-dropdown-item v-if="userInfo.userType === 'admin'" command="lock" divided><el-icon><Lock /></el-icon>全部冻结</el-dropdown-item>
                    <el-dropdown-item v-if="userInfo.userType === 'admin'" command="unlock"><el-icon><Unlock /></el-icon>全部解冻</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
            <div class="table-info" style="color: #606266; font-size: 14px; display: flex; align-items: center; gap: 10px; flex-wrap: wrap;">
              <span>共 {{ displayedStudentGrades.length }} 名</span>
              <el-divider direction="vertical" />
              <span>已录入 <strong style="color:#303133;">{{ gradeStats.completed }}</strong></span>
              <el-divider direction="vertical" />
              <span>待录入 <strong style="color:#303133;">{{ gradeStats.pending }}</strong></span>
              <el-divider direction="vertical" />
              <span>不计分 {{ gradeStats.notCounted }}</span>
              <el-divider direction="vertical" />
              <span>完成度 <strong style="color:#303133;">{{ gradeStats.percentage }}%</strong></span>
              <el-divider direction="vertical" />
              <el-button link type="primary" @click="showLocalFilters = !showLocalFilters">{{ showLocalFilters ? '收起筛选' : '展开筛选' }}</el-button>
            </div>
          </div>
          <div v-if="showLocalFilters" style="margin-top: 12px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap;">
            <el-input v-model="searchSchoolId" placeholder="按学号筛选" size="small" clearable style="width: 180px;" />
            <el-input v-model="searchRealName" placeholder="按姓名筛选" size="small" clearable style="width: 180px;" />
            <el-input v-model="searchClassId" placeholder="按班级筛选" size="small" clearable style="width: 180px;" />
            <el-button size="small" @click="clearLocalFilters">清空筛选</el-button>
          </div>
        </div>
         
         <el-table 
           :data="displayedStudentGrades" 
           style="width: 100%" 
           :row-class-name="getRowClassName"
           height="66vh"
           border
           class="grade-table"
         >
           <el-table-column prop="schoolId" label="学号" width="140" sortable>
             <template #default="scope">
                <span :class="getStudentIdClass(scope.row)">
                  {{ scope.row.schoolId }}
                </span>
             </template>
           </el-table-column>
           <el-table-column prop="studentName" label="姓名" width="140" sortable>
             <template #default="scope">
               <span :class="getStudentNameClass(scope.row)">
                 <el-icon v-if="hasGrade(scope.row)" class="grade-icon"><CircleCheckFilled /></el-icon>
                 <el-icon v-else-if="scope.row.score === 'N'" class="grade-icon"><RemoveFilled /></el-icon>
                 <el-icon v-else class="grade-icon"><Clock /></el-icon>
                 {{ scope.row.studentName }}
               </span>
             </template>
           </el-table-column>
           <el-table-column prop="score" label="成绩" width="200">
             <template #default="scope">
                <el-input
                  v-model="scope.row.score"
                  placeholder="输入(0-100或N)"
                  size="large"
                  clearable
                  class="score-input"
                  style="width: 150px;"
                  :class="getScoreInputClass(scope.row)"
                  :disabled="scope.row.isLocked || scope.row.isReadOnly"
                  :ref="(el) => setScoreRef(scope.$index, el)"
                  @keydown="(e) => onScoreKeydown(e, scope.$index)"
                  @blur="() => validateScore(scope.row)"
                />
             </template>
           </el-table-column>
           <el-table-column label="状态" width="100">
             <template #default="scope">
               <el-tag v-if="scope.row.isReadOnly" type="info" size="small">
                 他人录入
               </el-tag>
               <el-tag v-else-if="scope.row.isLocked" type="danger" size="small">
                 <el-icon><Lock /></el-icon>
                 已冻结
               </el-tag>
               <el-tag v-else type="info" size="small">
                 <el-icon><Unlock /></el-icon>
                 可编辑
               </el-tag>
             </template>
           </el-table-column>
           <el-table-column prop="classId" label="班级" width="140" sortable />
           <el-table-column prop="groupName" label="小组" width="120" />
           <el-table-column prop="remark" label="备注" width="220">
             <template #default="scope">
               <el-input
                 v-model="scope.row.remark"
                  placeholder="备注"
                  size="small"
                  clearable
                  :disabled="scope.row.isLocked || scope.row.isReadOnly"
                />
              </template>
            </el-table-column>
          <el-table-column label="操作" width="120" v-if="userInfo.userType === 'admin'">
            <template #default="scope">
              <el-button 
                v-if="!scope.row.isLocked && hasGrade(scope.row)" 
                plain
                size="small" 
                @click="handleLockGrade(scope.row)"
                :loading="scope.row.locking"
              >
                冻结
              </el-button>
              <el-button 
                v-else-if="scope.row.isLocked" 
                plain
                size="small" 
                @click="handleUnlockGrade(scope.row)"
                :loading="scope.row.unlocking"
              >
                解冻
              </el-button>
            </template>
          </el-table-column>
         </el-table>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="gradeDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSaveGrades" :loading="saving">保存成绩</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { CircleCheckFilled, RemoveFilled, Clock, Refresh, Lock, Unlock, ArrowDown, InfoFilled, Sort } from '@element-plus/icons-vue'

import { getStudentsByGroup, batchUpsertGrades, lockGrade, unlockGrade, getGradeDetail, batchLockGrades, downloadGradeTemplateXlsx, importGradeTemplateXlsx } from '@/api/grade'
import { getSemesterList, getWeekInfo } from '@/api/semester'
import { getExperimentList, getExperimentsBySuite } from '@/api/experiment'
import { getGroupExperimentByGroup } from '@/api/groupExperiment'
import { getAllSuites, getSuiteDetail } from '@/api/suite'
import { getScheduleList } from '@/api/schedule'

const currentSemester = ref(null)
const selectedSuiteId = ref(null)
const suiteOptions = ref([])
const loading = ref(false)
const initializing = ref(true)
const initializationError = ref(false)
let scheduleLoadSeq = 0
const DATA_CACHE_TTL = 5 * 60 * 1000
const scheduleCache = new Map()
const studentsCache = new Map()
const groupExperimentCache = new Map()
const gradeDetailCache = new Map()
let gradeDetailLoadSeq = 0
const PAGE_INIT_TIMEOUT = 6000

const defaultWeekType = ref(0) // 0 单周，1 双周
const activeWeekType = ref(null) // 打开成绩录入弹窗时的小组所属周类型
const saving = ref(false)
const batchLocking = ref(false)
const batchUnlocking = ref(false)
const lockingAll = ref(false)
const unlockingAll = ref(false)
const dialogLoading = ref(false)
const dialogLoadingText = ref('正在加载录入数据...')
const gradeDialogVisible = ref(false)
const semesterList = ref([])
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))

// 日历相关数据
const weekdays = ['周一','周二','周三','周四','周五']
const timeSlots = ['上午','下午']
const cellGroupsOdd = reactive({})

const downloadBlob = (blob, fileName) => {
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

const handleDownloadTemplate = async () => {
  try {
    if (!selectedGroupName.value || !selectedExperimentId.value) {
      ElMessage.warning('请先选择小组与实验')
      return
    }
    if (!currentSemester.value || !selectedSuiteId.value) {
      ElMessage.warning('请先选择学期和实验套')
      return
    }
    const wt = (activeWeekType.value === 0 || activeWeekType.value === 1) ? activeWeekType.value : defaultWeekType.value
    const resp = await downloadGradeTemplateXlsx({
      groupName: selectedGroupName.value,
      experimentId: selectedExperimentId.value,
      semesterId: currentSemester.value,
      suiteId: selectedSuiteId.value,
      weekType: wt
    })
    const blob = new Blob([resp.data], {
      type: resp.headers?.['content-type'] || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    downloadBlob(blob, 'grade-template.xlsx')
  } catch (e) {
    ElMessage.error(e?.message || '下载模板失败')
  }
}

const handleTemplateFileChange = async (uploadFile) => {
  try {
    const file = uploadFile?.raw
    if (!file) return
    if (!selectedGroupName.value || !selectedExperimentId.value) {
      ElMessage.warning('请先选择小组与实验')
      return
    }
    if (!currentSemester.value || !selectedSuiteId.value) {
      ElMessage.warning('请先选择学期和实验套')
      return
    }
    const wt = (activeWeekType.value === 0 || activeWeekType.value === 1) ? activeWeekType.value : defaultWeekType.value

    const loadingInstance = ElLoading.service({ text: '正在导入模板...' })
    try {
      const resp = await importGradeTemplateXlsx(file, {
        groupName: selectedGroupName.value,
        experimentId: selectedExperimentId.value,
        semesterId: currentSemester.value,
        suiteId: selectedSuiteId.value,
        weekType: wt
      })
      if (resp?.code === 200) {
        const sc = resp.data?.successCount ?? 0
        const fc = resp.data?.failCount ?? 0
        const errs = resp.data?.errorMessages || []
        if (errs && errs.length) {
          ElMessageBox.alert(errs.join('\n'), `导入完成：成功${sc}，失败${fc}`, { confirmButtonText: '知道了' })
        } else {
          ElMessage.success(`导入完成：成功${sc}，失败${fc}`)
        }
        await handleExperimentChange()
      } else {
        ElMessage.error(resp?.message || '导入失败')
      }
    } finally {
      loadingInstance.close()
    }
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  }
}
const cellGroupsEven = reactive({})

// 学生成绩数据
const studentGrades = ref([])

// 成绩输入的键盘导航：上下键跳转到下一位/上一位学生
const scoreRefs = ref([])
const setScoreRef = (index, el) => {
  if (index == null) return
  scoreRefs.value[index] = el
}

const focusScoreAt = (index) => {
  if (index == null || index < 0) return
  const inst = scoreRefs.value[index]
  if (inst && typeof inst.focus === 'function') {
    inst.focus()
    try {
      const root = inst?.$el
      const input = root ? root.querySelector('input') : null
      if (input && typeof input.select === 'function') input.select()
    } catch (e) {
      // ignore
    }
    return
  }
  // 兜底：尝试从组件根元素找到 input
  try {
    const root = inst?.$el
    const input = root ? root.querySelector('input') : null
    if (input && typeof input.focus === 'function') {
      input.focus()
      if (typeof input.select === 'function') input.select()
    }
  } catch (e) {
    // ignore
  }
}

const onScoreKeydown = (e, index) => {
  const key = e?.key
  const isEnter = key === 'Enter'
  if (key !== 'ArrowDown' && key !== 'ArrowUp' && !isEnter) return
  e.preventDefault()
  e.stopPropagation()

  // 回车=下一个待录入学生；Shift+回车=上一个。方向键照旧上下移动。
  const step = (key === 'ArrowUp' || (isEnter && e.shiftKey)) ? -1 : 1
  const list = displayedStudentGrades.value || []
  let i = Number(index)
  if (Number.isNaN(i)) return
  i += step

  while (i >= 0 && i < list.length) {
    const row = list[i]
    if (row && !row.isLocked && !row.isReadOnly) {
      focusScoreAt(i)
      break
    }
    i += step
  }
}

const normalizeScoreValue = (v) => {
  if (v === null || v === undefined) return ''
  const s = String(v).trim()
  if (!s) return ''
  return s
}

const normalizeRemarkValue = (v) => {
  if (v === null || v === undefined) return ''
  return String(v)
}

const isScoreChanged = (row) => {
  if (!row) return false
  return normalizeScoreValue(row.score) !== normalizeScoreValue(row._initialScore)
}

const isRemarkChanged = (row) => {
  if (!row) return false
  return normalizeRemarkValue(row.remark) !== normalizeRemarkValue(row._initialRemark)
}

const getScoreInputClass = (row) => {
  if (!row || row.isLocked || row.isReadOnly) return ''
  return isScoreChanged(row) ? 'changed-input' : ''
}

const getRemarkInputClass = (row) => {
  if (!row || row.isLocked || row.isReadOnly) return ''
  return isRemarkChanged(row) ? 'changed-input' : ''
}

// 对话框内的选择
const dialogGroupOptions = ref([])
const selectedGroupName = ref('')
const selectedExperimentId = ref(null)
// 本地筛选（默认隐藏，不影响点击行为）
const showLocalFilters = ref(false)
const searchSchoolId = ref('')
const searchRealName = ref('')
const searchClassId = ref('')
const displayedStudentGrades = computed(() => {
  const sid = (searchSchoolId.value || '').trim()
  const name = (searchRealName.value || '').trim()
  const cls = (searchClassId.value || '').trim()
  const list = studentGrades.value || []
  if (!sid && !name && !cls) return list
  return list.filter(s => {
    const okSid = sid ? String(s.schoolId || '').includes(sid) : true
    const okName = name ? String(s.studentName || s.realName || '').includes(name) : true
    const okCls = cls ? String(s.classId || '').includes(cls) : true
    return okSid && okName && okCls
  })
})

const gradeStats = computed(() => {
  let completed = 0
  let notCounted = 0
  const list = studentGrades.value || []
  list.forEach(student => {
    if (student.score === 'N') {
      notCounted += 1
    } else if (hasGrade(student)) {
      completed += 1
    }
  })
  const pending = Math.max(0, list.length - completed - notCounted)
  const percentage = list.length
    ? Math.round(((completed + notCounted) / list.length) * 100)
    : 0
  return { completed, pending, notCounted, percentage }
})
const clearLocalFilters = () => {
  searchSchoolId.value = ''
  searchRealName.value = ''
  searchClassId.value = ''
}

// ===== 轻量优化：防抖与最近使用记忆 =====
const debounce = (fn, wait = 250) => {
  let t = null
  return (...args) => {
    if (t) clearTimeout(t)
    t = setTimeout(() => fn(...args), wait)
  }
}

// 以学期+套件为上下文记忆最近一次的小组与实验选择
const recentKey = computed(() => `grade_recent_${currentSemester.value || 'none'}_${selectedSuiteId.value || 'none'}`)
const getRecent = () => {
  try { return JSON.parse(localStorage.getItem(recentKey.value) || '{}') } catch { return {} }
}
const setRecent = (payload) => {
  try { localStorage.setItem(recentKey.value, JSON.stringify({ ...(getRecent()||{}), ...payload })) } catch {}
}

const withTimeout = (promise, timeout = PAGE_INIT_TIMEOUT) => {
  let timer = null
  const timeoutPromise = new Promise((resolve) => {
    timer = setTimeout(() => resolve({ __timeout: true }), timeout)
  })
  return Promise.race([promise, timeoutPromise]).finally(() => {
    if (timer) clearTimeout(timer)
  })
}

const getGradeDetailCacheKey = (
  groupName = selectedGroupName.value,
  experimentId = selectedExperimentId.value
) => `${currentSemester.value || 'none'}_${selectedSuiteId.value || 'none'}_${groupName || 'none'}_${experimentId || 'none'}`

const invalidateGradeDetailCache = (
  groupName = selectedGroupName.value,
  experimentId = selectedExperimentId.value
) => {
  gradeDetailCache.delete(getGradeDetailCacheKey(groupName, experimentId))
}

const normalizeGradeDetailStudents = (students = []) => {
  return students.map(student => {
    const rawScore = student.score
    const normalizedScore = rawScore === null || rawScore === undefined ? '' : (rawScore === 'N' ? 'N' : String(rawScore))
    const normalizedRemark = student.remark ?? ''
    const normalizedGradeId = student.gradeId ?? null
    return {
      ...student,
      score: normalizedScore,
      remark: normalizedRemark,
      isLocked: student.isLocked ?? false,
      isReadOnly: student.isReadOnly ?? false,
      gradeId: normalizedGradeId,
      _backendCreateTime: student.createTime ?? null,
      _backendUpdateTime: student.updateTime ?? null,
      _backendIsModified: student.isModified ?? false,
      _initialScore: normalizedScore,
      _initialRemark: normalizedRemark,
      _initialGradeId: normalizedGradeId,
      locking: false,
      unlocking: false
    }
  })
}

// 获取学期列表
const loadSemesterList = async () => {
  try {
    const response = await getSemesterList({ current: 1, size: 100 })
    if (response.code === 200) {
      semesterList.value = response.data.records
      const currentSemesterData = semesterList.value.find(s => s.status === 'active')
      if (currentSemesterData) {
        currentSemester.value = currentSemesterData.semesterId
      } else if (semesterList.value.length > 0) {
        currentSemester.value = semesterList.value[0].semesterId
      }

      getWeekInfo({ semesterId: currentSemester.value }).then(info => {
        if (info?.code === 200 && info?.data && (info.data.weekType === 0 || info.data.weekType === 1)) {
          defaultWeekType.value = Number(info.data.weekType)
        }
      }).catch(() => {})
    }
  } catch (error) {
    console.error('获取学期列表失败:', error)
    ElMessage.error('获取学期列表失败')
    throw error
  }
 }

const loadSuiteOptions = async () => {
  try {
    const resp = await getAllSuites()
    const list = resp?.data || []
    suiteOptions.value = list
    if (!selectedSuiteId.value && list.length) selectedSuiteId.value = list[0].suiteId || list[0].experimentSuiteId
  } catch (e) {
    suiteOptions.value = []
    throw e
  }
}

// 解析时间键值 - 使用和成绩查看页面一样的逻辑
const parseScheduleTimeKey = (experimentTime) => { 
  try { 
    const data = typeof experimentTime === 'string' ? JSON.parse(experimentTime) : experimentTime; 
    if (data && data.weekday && data.timeSlot) { 
      return `${data.weekday}${data.timeSlot}` 
    } 
  } catch (e) { 
    if (typeof experimentTime === 'string') { 
      const m = experimentTime.match(/周[一二三四五][上下]午/); 
      if (m) return m[0] 
    } 
  } 
  return '' 
}

// 解析小组ID列表
const parseGroupIds = (raw) => {
  if (!raw) {
    return []
  }
  
  try {
    let cleaned = String(raw)
    
    // 如果是JSON格式，先解析
    if (cleaned.startsWith('[') || cleaned.startsWith('{')) {
      try {
        const parsed = JSON.parse(cleaned)
        if (Array.isArray(parsed)) {
          const result = parsed.filter(s => s && String(s).trim()).map(s => String(s).trim())
          return result
        }
      } catch (e) {
        // JSON解析失败，使用字符串解析
      }
    }
    
    // 字符串解析
    cleaned = cleaned.replace(/^\[|\]$/g, '')
    const result = cleaned.split(',').map(s => s && s.trim()).filter(Boolean)
    return result
  } catch (e) {
    return []
  }
}

// 确保单元格状态存在
const ensureCellState = () => {
  weekdays.forEach(d => {
    if (!cellGroupsOdd[d]) cellGroupsOdd[d] = {}
    if (!cellGroupsEven[d]) cellGroupsEven[d] = {}
    timeSlots.forEach(s => {
      if (!cellGroupsOdd[d][s]) cellGroupsOdd[d][s] = []
      if (!cellGroupsEven[d][s]) cellGroupsEven[d][s] = []
    })
  })
}

const applyScheduleLists = (oddList, evenList) => {
  ensureCellState()
  weekdays.forEach(d => timeSlots.forEach(s => {
    cellGroupsOdd[d][s] = []
    cellGroupsEven[d][s] = []
  }))

  oddList.forEach(item => {
    const key = parseScheduleTimeKey(item.experimentTime)
    if (!key) return
    const wt = item.weekType
    if (!(wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined')) return
    const weekday = key.slice(0, 2)
    const slot = key.slice(2)
    const groups = parseGroupIds(item.groupIds)
    if (cellGroupsOdd[weekday]?.[slot]) {
      cellGroupsOdd[weekday][slot] = Array.from(new Set([...cellGroupsOdd[weekday][slot], ...groups]))
    }
  })

  evenList.forEach(item => {
    const key = parseScheduleTimeKey(item.experimentTime)
    if (!key) return
    const wt = item.weekType
    if (!(wt === 1 || wt === '1')) return
    const weekday = key.slice(0, 2)
    const slot = key.slice(2)
    const groups = parseGroupIds(item.groupIds)
    if (cellGroupsEven[weekday]?.[slot]) {
      cellGroupsEven[weekday][slot] = Array.from(new Set([...cellGroupsEven[weekday][slot], ...groups]))
    }
  })
}

// 加载课表数据
const loadSchedule = async (forceRefresh = false) => {
  if (!currentSemester.value) {
    return
  }

  const cacheKey = `${currentSemester.value}_${selectedSuiteId.value || 'all'}`
  const cached = scheduleCache.get(cacheKey)
  if (!forceRefresh && cached && Date.now() - cached.timestamp < DATA_CACHE_TTL) {
    applyScheduleLists(cached.oddList, cached.evenList)
    return
  }
  
  const seq = ++scheduleLoadSeq
  loading.value = true
  try {
    ensureCellState()
    // 清空
    weekdays.forEach(d => timeSlots.forEach(s => { cellGroupsOdd[d][s] = []; cellGroupsEven[d][s] = [] }))
    
    const params = { current: 1, size: 1000 }
    if (selectedSuiteId.value) params.suiteId = selectedSuiteId.value
    if (currentSemester.value) params.semesterId = currentSemester.value
    const scheduleResp = await getScheduleList(params)
    if (seq !== scheduleLoadSeq) return
    const allScheduleList = scheduleResp?.data?.records || []
    const oddList = allScheduleList.filter(item => {
      const wt = item.weekType
      return wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined'
    })
     
     oddList.forEach(item => {
       const key = parseScheduleTimeKey(item.experimentTime)
       
       if (!key) {
         return
       }
       
       // 仅接收 weekType=0 或未设置的记录到单周表
       const wt = item.weekType
       
       if (!(wt === 0 || wt === '0' || wt === null || typeof wt === 'undefined')) {
         return
       }
       
       const weekday = key.slice(0, 2)
       const slot = key.slice(2)
       
       const groups = parseGroupIds(item.groupIds)
       
       if (cellGroupsOdd[weekday] && cellGroupsOdd[weekday][slot]) {
         const oldGroups = cellGroupsOdd[weekday][slot]
         cellGroupsOdd[weekday][slot] = Array.from(new Set([ ...oldGroups, ...groups ]))
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

    scheduleCache.set(cacheKey, { oddList, evenList, timestamp: Date.now() })
    
         
   } catch (error) {
     if (seq !== scheduleLoadSeq) return
     // 加载课表失败
    // 清空状态
    weekdays.forEach(d => timeSlots.forEach(s => { cellGroupsOdd[d][s] = []; cellGroupsEven[d][s] = [] }))
  } finally {
    if (seq === scheduleLoadSeq) {
      loading.value = false
    }
  }
}

// 点击小组标签，打开成绩录入
const openGradeEntry = async (groupName, weekType = null) => {
  selectedGroupName.value = groupName
  activeWeekType.value = (weekType === 0 || weekType === 1) ? Number(weekType) : defaultWeekType.value
  dialogGroupOptions.value = [groupName]
  studentGrades.value = []
  dialogLoading.value = true
  dialogLoadingText.value = '正在加载录入数据...'
  // 记忆最近使用的小组
  try { setRecent({ groupName }) } catch {}
  
  // 立即打开对话框，提供即时反馈
  gradeDialogVisible.value = true
  
  // 使用 nextTick 确保界面立即渲染
  await nextTick()
  
  try {
    await loadExperimentsForGroup(groupName)
    if (selectedExperimentId.value) {
      await loadExistingGrades({ showLoading: false })
    }
  } finally {
    dialogLoading.value = false
  }
}

const allExperiments = ref([])
const experimentList = ref([])

// 只加载学生数据，不加载成绩
const loadStudentsOnly = async (groupName, weekType = null) => {
  try {
    const cacheKey = `${currentSemester.value}_${selectedSuiteId.value}_${groupName}_${weekType ?? 'default'}`
    const cached = studentsCache.get(cacheKey)
    const hasValidCache = cached && Date.now() - cached.timestamp < DATA_CACHE_TTL
    const response = hasValidCache
      ? cached.response
      : await getStudentsByGroup(groupName, currentSemester.value, selectedSuiteId.value, weekType)
    if (!hasValidCache) {
      studentsCache.set(cacheKey, { response, timestamp: Date.now() })
    }
    if (response.code === 200) {
      const students = response.data?.students || []
      if ((!students || students.length === 0) && response.data?.reason) {
        ElMessage.warning(response.data.reason)
      }
      studentGrades.value = students.map(s => ({ 
        ...s, 
        score: s.score ?? '',
        remark: s.remark ?? '',
        isLocked: false,
        isReadOnly: false,
        gradeId: null,
        _initialScore: '',
        _initialRemark: '',
        _initialGradeId: null,
        locking: false,
        unlocking: false
      }))
      return true
    }
  } catch (error) {
    console.error('获取学生列表失败:', error)
    studentGrades.value = []
  }
  return false
}

// 加载并设置实验列表
const loadExperimentsForGroup = async (groupName) => {
  try {
    // 如果实验列表未缓存，则加载；否则直接使用缓存
    const experimentsPromise = allExperiments.value.length > 0 
      ? Promise.resolve({ data: allExperiments.value })
      : (selectedSuiteId.value
          ? getExperimentsBySuite(selectedSuiteId.value)
          : getExperimentList({ current: 1, size: 300 }))
    
    // 并行加载实验列表和小组实验配置
    const groupCacheKey = `${currentSemester.value}_${selectedSuiteId.value}_${groupName}`
    const cachedGroupExperiments = groupExperimentCache.get(groupCacheKey)
    const hasValidGroupCache = cachedGroupExperiments && Date.now() - cachedGroupExperiments.timestamp < DATA_CACHE_TTL
    const groupExperimentsPromise = hasValidGroupCache
      ? Promise.resolve(cachedGroupExperiments.response)
      : getGroupExperimentByGroup(groupName)

    const [experimentsResp, groupExpResp] = await Promise.all([
      experimentsPromise,
      groupExperimentsPromise
    ])
    if (!hasValidGroupCache) {
      groupExperimentCache.set(groupCacheKey, { response: groupExpResp, timestamp: Date.now() })
    }
    
    const allExps = experimentsResp?.data?.records || experimentsResp?.data || []
    if (allExperiments.value.length === 0) {
      allExperiments.value = allExps
    }
    
    const groupExps = Array.isArray(groupExpResp?.data) ? groupExpResp.data : []
    const allowedIds = new Set(
      groupExps
        .filter(r => (
          (!currentSemester.value || String(r.semesterId) === String(currentSemester.value)) &&
          (!selectedSuiteId.value || String(r.suiteId) === String(selectedSuiteId.value)) &&
          r.experimentId
        ))
        .map(r => Number(r.experimentId))
    )
    
    await ensureSuiteOrder()
    const filtered = allExps.filter(e => allowedIds.has(Number(e.experimentId)))
    const sorted = sortExperimentsBySuiteOrder(filtered)
    experimentList.value = sorted
    
    // 优先使用“最近使用”的实验ID
    const recent = getRecent()
    const recentExp = Number(recent?.experimentId)
    const hasRecent = sorted.some(e => Number(e.experimentId) === recentExp)
    const currentStillAvailable = sorted.some(e => Number(e.experimentId) === Number(selectedExperimentId.value))
    if (!currentStillAvailable) {
      if (hasRecent) selectedExperimentId.value = recentExp
      else if (sorted.length > 0) selectedExperimentId.value = sorted[0].experimentId
      else selectedExperimentId.value = null
    }
  } catch (error) {
    console.error('加载实验列表失败:', error)
    allExperiments.value = []
    experimentList.value = []
  }
}

// 根据所选小组过滤实验：仅保留 group_experiment 中已配置的实验（按学期/套件）
const refreshExperimentsForGroup = async () => {
  if (!selectedGroupName.value) { experimentList.value = []; return }
  try {
    const resp = await getGroupExperimentByGroup(selectedGroupName.value)
    const rows = Array.isArray(resp?.data) ? resp.data : []
    const allowed = new Set(
      rows
        .filter(r => (
          (!currentSemester.value || String(r.semesterId) === String(currentSemester.value)) &&
          (!selectedSuiteId.value || String(r.suiteId) === String(selectedSuiteId.value)) &&
          r.experimentId
        ))
        .map(r => Number(r.experimentId))
    )
    await ensureSuiteOrder()
    const filtered = (allExperiments.value || []).filter(e => allowed.has(Number(e.experimentId)))
    const sorted = sortExperimentsBySuiteOrder(filtered)
    experimentList.value = sorted
    // 如果当前选择不在过滤后列表，尝试使用“最近使用”作为默认
    if (!sorted.some(e => Number(e.experimentId) === Number(selectedExperimentId.value))) {
      const recent = getRecent()
      const recentExp = Number(recent?.experimentId)
      if (sorted.some(e => Number(e.experimentId) === recentExp)) {
        selectedExperimentId.value = recentExp
      } else {
        selectedExperimentId.value = sorted.length ? sorted[0].experimentId : null
      }
    }
  } catch (e) {
    experimentList.value = []
  }
}

const handleDialogGroupChange = async () => {
  selectedExperimentId.value = null
  studentGrades.value = []
  await refreshExperimentsForGroup()
  if (selectedExperimentId.value) {
    await loadExistingGrades()
  }
}

// ===== 实验顺序：按套件的 experimentIds 排序（安全回退） =====
const suiteExperimentOrder = ref([])
const suiteOrderLoadedFor = ref(null)
const ensureSuiteOrder = async () => {
  try {
    if (!selectedSuiteId.value) {
      suiteExperimentOrder.value = []
      suiteOrderLoadedFor.value = null
      return
    }
    if (suiteOrderLoadedFor.value && String(suiteOrderLoadedFor.value) === String(selectedSuiteId.value)) return
    const detail = await getSuiteDetail(selectedSuiteId.value)
    let ids = []
    const raw = detail?.data?.experimentIds
    if (Array.isArray(raw)) ids = raw
    else if (typeof raw === 'string') {
      try { const parsed = JSON.parse(raw); if (Array.isArray(parsed)) ids = parsed } catch {}
    }
    suiteExperimentOrder.value = (ids || []).map(n => Number(n)).filter(n => !Number.isNaN(n))
    suiteOrderLoadedFor.value = selectedSuiteId.value
  } catch {
    suiteExperimentOrder.value = []
    suiteOrderLoadedFor.value = null
  }
}

const sortExperimentsBySuiteOrder = (list) => {
  const order = suiteExperimentOrder.value || []
  if (!order.length) return list
  const pos = new Map(order.map((id, idx) => [Number(id), idx]))
  return [...list].sort((a, b) => {
    const ia = pos.has(Number(a.experimentId)) ? pos.get(Number(a.experimentId)) : 9999
    const ib = pos.has(Number(b.experimentId)) ? pos.get(Number(b.experimentId)) : 9999
    if (ia !== ib) return ia - ib
    return Number(a.experimentId) - Number(b.experimentId)
  })
}

// 加载小组学生列表
const loadGroupStudents = async () => {
  if (!selectedGroupName.value) {
    studentGrades.value = []
    return
  }
  try {
    if (selectedExperimentId.value) {
      await loadExistingGrades({ forceRefresh: true })
      return
    }
    const wt = (activeWeekType.value === 0 || activeWeekType.value === 1) ? activeWeekType.value : defaultWeekType.value
    const response = await getStudentsByGroup(selectedGroupName.value, currentSemester.value, selectedSuiteId.value, wt)
    if (response.code === 200) {
      // 后端返回的data是对象，学生列表在data.students中
      const students = response.data?.students || []
      if ((!students || students.length === 0) && response.data?.reason) {
        ElMessage.warning(response.data.reason)
      }
      const arr = students.map(s => ({ ...s, score: s.score ?? '', remark: s.remark ?? '' }))
      studentGrades.value = arr
      // 如果已选择实验，则加载已录入成绩进行回填
      if (selectedExperimentId.value) {
        await loadExistingGrades({ forceRefresh: true })
      } else {
        // 初始化状态字段
        initializeGradeFields()
      }
    }
  } catch (error) {
    console.error('获取学生列表失败:', error)
    ElMessage.error('获取学生列表失败')
  }
}

// 加载已有成绩
const loadExistingGrades = async ({ forceRefresh = false, showLoading = true } = {}) => {
  if (!selectedGroupName.value || !selectedExperimentId.value) {
    initializeGradeFields()
    return false
  }

  const seq = ++gradeDetailLoadSeq
  const cacheKey = getGradeDetailCacheKey()
  const cached = gradeDetailCache.get(cacheKey)
  const hasValidCache = !forceRefresh && cached && Date.now() - cached.timestamp < DATA_CACHE_TTL
  if (showLoading) {
    dialogLoading.value = true
    dialogLoadingText.value = '正在加载成绩明细...'
  }

  try {
    const resp = hasValidCache
      ? cached.response
      : await getGradeDetail({
          groupName: selectedGroupName.value,
          experimentId: selectedExperimentId.value,
          semesterId: currentSemester.value,
          suiteId: selectedSuiteId.value
        })

    if (seq !== gradeDetailLoadSeq) {
      return false
    }
    if (!hasValidCache) {
      gradeDetailCache.set(cacheKey, { response: resp, timestamp: Date.now() })
    }
    
    if (resp?.code === 200 && resp.data?.students) {
      studentGrades.value = resp.data.students.map(student => {
        const rawScore = student.score
        const normalizedScore = rawScore === null || rawScore === undefined ? '' : (rawScore === 'N' ? 'N' : String(rawScore))
        const normalizedRemark = student.remark ?? ''
        const normalizedGradeId = student.gradeId ?? null
        return {
          ...student,
          score: normalizedScore,
          remark: normalizedRemark,
          isLocked: student.isLocked ?? false,
          isReadOnly: student.isReadOnly ?? false,
          gradeId: normalizedGradeId,
          _backendCreateTime: student.createTime ?? null,
          _backendUpdateTime: student.updateTime ?? null,
          _backendIsModified: student.isModified ?? false,
          _initialScore: normalizedScore,
          _initialRemark: normalizedRemark,
          _initialGradeId: normalizedGradeId,
          locking: false,
          unlocking: false
        }
      })
      return true
    } else {
      initializeGradeFields()
      return false
    }
  } catch (error) {
    if (seq !== gradeDetailLoadSeq) {
      return false
    }
    console.error('加载成绩失败:', error)
    initializeGradeFields()
    return false
  } finally {
    if (seq === gradeDetailLoadSeq && showLoading) {
      dialogLoading.value = false
    }
  }
}

// 防抖后的加载器
const debouncedLoadExistingGrades = debounce(() => { loadExistingGrades() }, 300)

// 初始化成绩字段
const initializeGradeFields = () => {
  studentGrades.value = studentGrades.value.map(s => ({
    ...s,
    isLocked: false,
    isReadOnly: false,
    gradeId: null,
    remark: s.remark ?? '',
    _initialScore: s._initialScore ?? '',
    _initialRemark: s._initialRemark ?? '',
    _initialGradeId: s._initialGradeId ?? null,
    locking: false,
    unlocking: false
  }))
}

// 保存成绩（批量覆盖）
const handleSaveGrades = async () => {
  if (!selectedGroupName.value) {
    ElMessage.warning('请先选择小组')
    return
  }
  if (!selectedExperimentId.value) {
    ElMessage.warning('请先选择实验')
    return
  }
  if (!currentSemester.value || !selectedSuiteId.value) {
    ElMessage.warning('请先选择学期和实验套')
    return
  }
  const invalidGrades = studentGrades.value.filter(student => {
    if (student.score !== null && student.score !== '' && student.score !== undefined && student.score !== 'N') {
      const score = parseFloat(student.score)
      return isNaN(score) || score < 0 || score > 100
    }
    return false
  })
  if (invalidGrades.length > 0) {
    ElMessage.error('成绩必须在0-100之间')
    return
  }
  saving.value = true
  try {
    const items = studentGrades.value
      .filter(s => !s.isReadOnly)
      .filter(s => (
        (s.score !== '' && s.score !== null && s.score !== undefined) ||
        ((s.remark || '').trim() !== '')
      ))
      .map(s => ({
        userId: s.userId,
        score: (s.score === '' || s.score === null || s.score === undefined) ? null : (s.score === 'N' ? 'N' : parseFloat(s.score)),
        remark: (s.remark || '').trim()
      }))
    
    await batchUpsertGrades({
      experimentId: selectedExperimentId.value,
      groupName: selectedGroupName.value,
      semesterId: currentSemester.value,
      suiteId: selectedSuiteId.value,
      items
    })
    
    ElMessage.success('成绩保存成功')
    // 只重新加载成绩，不重新加载整个学生列表
    invalidateGradeDetailCache()
    await loadExistingGrades({ forceRefresh: true })
    // 保存后不关闭对话框，继续编辑
  } catch (error) {
    console.error('保存成绩失败:', error)
    const errorMessage = error.response?.data?.message || error.message || '保存成绩失败'
    ElMessage.error(errorMessage)
  } finally {
    saving.value = false
  }
}

// 实验选择变化处理
const handleExperimentChange = async () => {
  // 记忆最近使用的实验
  try { setRecent({ experimentId: selectedExperimentId.value }) } catch {}
  if (!selectedExperimentId.value) return
  // 切换实验后：立刻拉取该实验已有成绩并回填锁定/状态
  // 如果当前还没加载学生列表，则先加载学生列表（会自动回填成绩）
  if (selectedGroupName.value && (!studentGrades.value || studentGrades.value.length === 0)) {
    await loadGroupStudents()
    return
  }
  if (studentGrades.value && studentGrades.value.length > 0) {
    await loadExistingGrades()
  }
}

const handleSemesterChange = async () => {
  try {
    const info = await getWeekInfo({ semesterId: currentSemester.value })
    if (info?.code === 200 && info?.data && (info.data.weekType === 0 || info.data.weekType === 1)) {
      defaultWeekType.value = Number(info.data.weekType)
    }
  } catch {}
  await loadSchedule()
}
const handleSuiteChange = async () => {
  allExperiments.value = []
  experimentList.value = []
  selectedExperimentId.value = null
  suiteExperimentOrder.value = []
  suiteOrderLoadedFor.value = null
  if (selectedSuiteId.value) {
    try {
      const resp = await getExperimentsBySuite(selectedSuiteId.value)
      allExperiments.value = resp?.data || []
    } catch {
      allExperiments.value = []
    }
  }
  await loadSchedule()
}

const handleRefresh = async () => {
  await loadSchedule(true)
  initializationError.value = false
  ElMessage.success('数据已刷新')
}

// 「批量操作」下拉菜单分发
const handleBatchCommand = (command) => {
  switch (command) {
    case 'setN': return setAllToN()
    case 'clear': return clearAllScores()
    case 'lock': return handleBatchLock()
    case 'unlock': return handleBatchUnlock()
  }
}

const setAllToN = () => {
  if (!selectedGroupName.value || !selectedExperimentId.value) {
    ElMessage.warning('请先选择小组和实验')
    return
  }
  
  studentGrades.value.forEach(student => {
    student.score = 'N'
  })
  
  ElMessage.success(`已将 ${studentGrades.value.length} 名学生全部设为N (不计分)`)
}

// 清空所有学生成绩
const clearAllScores = () => {
  if (!selectedGroupName.value || !selectedExperimentId.value) {
    ElMessage.warning('请先选择小组和实验')
    return
  }
  
  studentGrades.value.forEach(student => {
    student.score = ''
  })
  
  ElMessage.success(`已清空 ${studentGrades.value.length} 名学生的成绩`)
}

// 判断学生是否已录入成绩
const hasGrade = (student) => {
  return student.score !== null && 
         student.score !== undefined && 
         student.score !== '' &&
         student.score !== 'N'
}

// 冻结成绩
const handleLockGrade = async (student) => {
  if (!student.gradeId) {
    ElMessage.warning('该学生尚未录入成绩，无法冻结')
    return
  }
  
  student.locking = true
  try {
    await lockGrade(student.gradeId)
    student.isLocked = true
    ElMessage.success(`已冻结 ${student.studentName} 的成绩`)
  } catch (error) {
    console.error('冻结成绩失败:', error)
    ElMessage.error('冻结成绩失败')
  } finally {
    student.locking = false
  }
}

// 解冻成绩
const handleUnlockGrade = async (student) => {
  if (!student.gradeId) {
    ElMessage.warning('该学生尚未录入成绩，无法解冻')
    return
  }
  
  // 弹出密码输入对话框
  try {
    const password = await ElMessageBox.prompt('请输入管理员密码以解冻成绩', '密码验证', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPlaceholder: '请输入管理员密码'
    })
    
    student.unlocking = true
    await unlockGrade(student.gradeId, password.value)
    student.isLocked = false
    ElMessage.success(`已解冻 ${student.studentName} 的成绩`)
  } catch (error) {
    if (error === 'cancel') {
      return // 用户取消
    }
    console.error('解冻成绩失败:', error)
    ElMessage.error(error.response?.data?.message || '解冻成绩失败')
  } finally {
    student.unlocking = false
  }
}

// 批量冻结成绩
const handleBatchLock = async () => {
  if (!selectedGroupName.value || !selectedExperimentId.value) {
    ElMessage.warning('请先选择小组和实验')
    return
  }
  
  // 获取当前页面显示的学生（已筛选后的）
  const studentsToLock = displayedStudentGrades.value.filter(s => s.gradeId && !s.isLocked)
  
  if (studentsToLock.length === 0) {
    ElMessage.warning('当前没有可冻结的成绩')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定要冻结当前页面 ${studentsToLock.length} 名学生的成绩吗？`,
      '批量冻结确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    batchLocking.value = true
    let successCount = 0
    let failCount = 0

    // 并发分批冻结，避免逐个串行等待造成的卡顿
    const batchSize = 20
    for (let i = 0; i < studentsToLock.length; i += batchSize) {
      const batch = studentsToLock.slice(i, i + batchSize)
      const results = await Promise.all(batch.map(async (student) => {
        try {
          await lockGrade(student.gradeId)
          student.isLocked = true
          return true
        } catch (error) {
          console.error(`冻结 ${student.studentName} 的成绩失败:`, error)
          return false
        }
      }))
      results.forEach(ok => { ok ? successCount++ : failCount++ })
    }
    
    if (successCount > 0) {
      ElMessage.success(`成功冻结 ${successCount} 名学生的成绩${failCount > 0 ? `，${failCount} 名失败` : ''}`)
    } else {
      ElMessage.error('批量冻结失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('批量冻结失败:', error)
    }
  } finally {
    batchLocking.value = false
  }
}

// 批量解冻成绩
const handleBatchUnlock = async () => {
  if (!selectedGroupName.value || !selectedExperimentId.value) {
    ElMessage.warning('请先选择小组和实验')
    return
  }
  
  // 获取当前页面显示的学生（已筛选后的）
  const studentsToUnlock = displayedStudentGrades.value.filter(s => s.gradeId && s.isLocked)
  
  if (studentsToUnlock.length === 0) {
    ElMessage.warning('当前没有可解冻的成绩')
    return
  }
  
  // 弹出密码输入对话框
  try {
    const password = await ElMessageBox.prompt(
      `请输入管理员密码以解冻当前页面 ${studentsToUnlock.length} 名学生的成绩`,
      '密码验证',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入管理员密码'
      }
    )
    
    batchUnlocking.value = true
    let successCount = 0
    let failCount = 0

    // 并发分批解冻，避免逐个串行等待造成的卡顿
    const batchSize = 20
    for (let i = 0; i < studentsToUnlock.length; i += batchSize) {
      const batch = studentsToUnlock.slice(i, i + batchSize)
      const results = await Promise.all(batch.map(async (student) => {
        try {
          await unlockGrade(student.gradeId, password.value)
          student.isLocked = false
          return true
        } catch (error) {
          console.error(`解冻 ${student.studentName} 的成绩失败:`, error)
          return false
        }
      }))
      results.forEach(ok => { ok ? successCount++ : failCount++ })
    }
    
    if (successCount > 0) {
      ElMessage.success(`成功解冻 ${successCount} 名学生的成绩${failCount > 0 ? `，${failCount} 名失败` : ''}`)
    } else {
      ElMessage.error('批量解冻失败')
    }
  } catch (error) {
    if (error === 'cancel') {
      return // 用户取消
    }
    console.error('批量解冻失败:', error)
    ElMessage.error('批量解冻失败')
  } finally {
    batchUnlocking.value = false
  }
}

// 获取当前学期和实验套下的所有成绩（包含锁定状态）
const getAllGradesForSemesterAndSuite = async () => {
  if (!currentSemester.value || !selectedSuiteId.value) {
    return []
  }
  
  try {
    const startTime = Date.now()
    
    // 1. 获取实验套下的所有实验ID
    const experimentsResp = await getExperimentsBySuite(selectedSuiteId.value)
    const experiments = experimentsResp?.data || []
    
    if (experiments.length === 0) {
      console.warn(`[批量冻结/解冻] 实验套 ${selectedSuiteId.value} 下没有实验`)
      return []
    }
    
    const experimentIds = experiments.map(e => e.experimentId).filter(Boolean)
    
    // 2. 从课表中获取所有小组（单周+双周）
    const allGroups = new Set()
    
    const baseScheduleParams = {
      current: 1,
      size: 1000,
      suiteId: selectedSuiteId.value,
      semesterId: currentSemester.value
    }
    // 获取单周/双周课表
    const [oddResp, evenResp] = await Promise.all([
      getScheduleList({ ...baseScheduleParams, weekType: 0 }),
      getScheduleList({ ...baseScheduleParams, weekType: 1 })
    ])
    const oddList = oddResp?.data?.records || []
    oddList.forEach(item => {
      const groups = parseGroupIds(item.groupIds)
      groups.forEach(g => allGroups.add(g))
    })
    const evenList = evenResp?.data?.records || []
    evenList.forEach(item => {
      const groups = parseGroupIds(item.groupIds)
      groups.forEach(g => allGroups.add(g))
    })
    
    const groupList = Array.from(allGroups)
    
    if (groupList.length === 0 || experimentIds.length === 0) {
      return []
    }
    
    // 3. 对每个小组+实验的组合，调用 getGradeDetail 获取成绩详情（包含锁定状态）
    const allGrades = []
    const totalRequests = groupList.length * experimentIds.length
    
    // 创建所有查询任务
    const gradeDetailPromises = []
    for (const groupName of groupList) {
      for (const experimentId of experimentIds) {
        gradeDetailPromises.push(
          getGradeDetail({
            groupName: groupName,
            experimentId: experimentId,
            semesterId: currentSemester.value,
            suiteId: selectedSuiteId.value
          }).then(response => {
            if (response?.code === 200 && response.data?.students) {
              return response.data.students
                .filter(s => s.gradeId && s.score !== null && s.score !== undefined)
                .map(student => ({
                  gradeId: student.gradeId,
                  userId: student.userId,
                  experimentId: experimentId,
                  groupName: groupName,
                  score: student.score,
                  isLocked: student.isLocked || false,
                  studentName: student.studentName || student.realName
                }))
            }
            return []
          }).catch(error => {
            console.warn(`[批量冻结/解冻] 查询小组 ${groupName} 实验 ${experimentId} 的成绩失败:`, error)
            return []
          })
        )
      }
    }
    
    // 并行执行所有查询
    const gradeDetailResults = await Promise.all(gradeDetailPromises)
    
    // 合并所有成绩
    gradeDetailResults.forEach(grades => {
      allGrades.push(...grades)
    })
    
    // 去重（按 gradeId）
    const gradeMap = new Map()
    allGrades.forEach(grade => {
      if (grade.gradeId && !gradeMap.has(grade.gradeId)) {
        gradeMap.set(grade.gradeId, grade)
      }
    })

    const uniqueGrades = Array.from(gradeMap.values())
    
    return uniqueGrades
  } catch (error) {
    console.error('[批量冻结/解冻] 获取所有成绩失败:', error)
    ElMessage.error('获取成绩列表失败：' + (error.message || '未知错误'))
    return []
  }
}

// 批量冻结所有成绩
const handleLockAllGrades = async () => {
  if (!currentSemester.value || !selectedSuiteId.value) {
    ElMessage.warning('请先选择学期和实验套')
    return
  }
  
  // 检查用户权限
  if (userInfo.value.userType !== 'admin') {
    ElMessage.error('只有管理员可以批量冻结所有成绩')
    return
  }
  
  try {
    // 确认对话框
    await ElMessageBox.confirm(
      '确定要冻结当前学期和实验套下的所有成绩吗？此操作将冻结所有已录入的成绩，请谨慎操作！',
      '批量冻结确认',
      {
        confirmButtonText: '确定冻结',
        cancelButtonText: '取消',
        type: 'warning',
        dangerouslyUseHTMLString: false
      }
    )
    
    lockingAll.value = true
    
    // 显示加载提示
    const loadingInstance = ElLoading.service({
      lock: true,
      text: '正在获取成绩列表，请稍候...',
      background: 'rgba(0, 0, 0, 0.7)'
    })
    
    try {
      // 获取所有成绩
      const allGrades = await getAllGradesForSemesterAndSuite()
      
      loadingInstance.close()
      
      if (allGrades.length === 0) {
        ElMessage.warning('当前没有找到任何成绩记录')
        lockingAll.value = false
        return
      }
      
      // 筛选出未冻结的成绩
      const gradesToLock = allGrades.filter(g => g.gradeId && !g.isLocked)
      const lockedCount = allGrades.filter(g => g.gradeId && g.isLocked).length
      
      if (gradesToLock.length === 0) {
        ElMessage.info(`当前所有成绩都已冻结（共 ${allGrades.length} 个成绩，${lockedCount} 个已冻结）`)
        lockingAll.value = false
        return
      }
      
      // 再次确认
      await ElMessageBox.confirm(
        `找到 ${allGrades.length} 个成绩记录，其中 ${gradesToLock.length} 个未冻结，${lockedCount} 个已冻结。\n\n确定要冻结所有 ${gradesToLock.length} 个未冻结的成绩吗？`,
        '批量冻结确认',
        {
          confirmButtonText: '确定冻结',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      
      // 显示冻结进度
      const processingInstance = ElLoading.service({
        lock: true,
        text: `正在冻结 ${gradesToLock.length} 个成绩...`,
        background: 'rgba(0, 0, 0, 0.7)'
      })
      
      try {
        // 提取 gradeId 列表
        const gradeIds = gradesToLock.map(g => g.gradeId)
        
        // 调用批量冻结接口
        const result = await batchLockGrades(gradeIds)
        
        processingInstance.close()
        
        if (result?.code === 200) {
          const successCount = result.data?.successCount || 0
          const failCount = result.data?.failCount || 0
          const totalCount = result.data?.totalCount || 0
          
          if (successCount > 0) {
            ElMessage.success(`成功冻结 ${successCount} 个成绩${failCount > 0 ? `，${failCount} 个失败` : ''}`)
          } else {
            ElMessage.error('批量冻结失败')
          }
        } else {
          ElMessage.error(result?.message || '批量冻结失败')
        }
      } catch (processError) {
        processingInstance.close()
        throw processError
      } finally {
        lockingAll.value = false
      }
    } catch (error) {
      loadingInstance.close()
      lockingAll.value = false
      if (error === 'cancel') {
        return // 用户取消
      }
      console.error('批量冻结所有成绩失败:', error)
      ElMessage.error('批量冻结失败：' + (error.response?.data?.message || error.message || '未知错误'))
    }
  } catch (error) {
    if (error === 'cancel') {
      return // 用户取消确认对话框
    }
    console.error('批量冻结所有成绩失败:', error)
    ElMessage.error('批量冻结失败：' + (error.response?.data?.message || error.message || '未知错误'))
  }
}

// 批量解冻所有成绩
const handleUnlockAllGrades = async () => {
  if (!currentSemester.value || !selectedSuiteId.value) {
    ElMessage.warning('请先选择学期和实验套')
    return
  }
  
  // 检查用户权限
  if (userInfo.value.userType !== 'admin') {
    ElMessage.error('只有管理员可以批量解冻所有成绩')
    return
  }
  
  try {
    // 密码验证
    const password = await ElMessageBox.prompt(
      '请输入管理员密码以解冻所有成绩',
      '密码验证',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputType: 'password',
        inputPlaceholder: '请输入管理员密码'
      }
    )
    
    unlockingAll.value = true
    
    // 显示加载提示
    const loadingInstance = ElLoading.service({
      lock: true,
      text: '正在获取成绩列表，请稍候...',
      background: 'rgba(0, 0, 0, 0.7)'
    })
    
    try {
      // 获取所有成绩
      const allGrades = await getAllGradesForSemesterAndSuite()
      
      loadingInstance.close()
      
      if (allGrades.length === 0) {
        ElMessage.warning('当前没有找到任何成绩记录')
        unlockingAll.value = false
        return
      }
      
      // 筛选出已冻结的成绩
      const gradesToUnlock = allGrades.filter(g => g.gradeId && g.isLocked)
      const unlockedCount = allGrades.filter(g => g.gradeId && !g.isLocked).length
      
      if (gradesToUnlock.length === 0) {
        ElMessage.info(`当前没有已冻结的成绩（共 ${allGrades.length} 个成绩，${unlockedCount} 个未冻结）`)
        unlockingAll.value = false
        return
      }
      
      // 确认对话框
      await ElMessageBox.confirm(
        `找到 ${allGrades.length} 个成绩记录，其中 ${gradesToUnlock.length} 个已冻结，${unlockedCount} 个未冻结。\n\n确定要解冻所有 ${gradesToUnlock.length} 个已冻结的成绩吗？`,
        '批量解冻确认',
        {
          confirmButtonText: '确定解冻',
          cancelButtonText: '取消',
          type: 'warning'
        }
      )
      
      // 由于没有批量解冻接口，需要逐个调用
      // 分批处理，避免过多并发请求
      const batchSize = 20 // 每批处理20个
      let successCount = 0
      let failCount = 0
      const totalCount = gradesToUnlock.length
      
      // 显示进度加载
      const progressInstance = ElLoading.service({
        lock: true,
        text: `正在解冻成绩：0/${totalCount}`,
        background: 'rgba(0, 0, 0, 0.7)'
      })
      
      try {
        for (let i = 0; i < gradesToUnlock.length; i += batchSize) {
          const batch = gradesToUnlock.slice(i, i + batchSize)
          
          // 并行处理当前批次
          const batchPromises = batch.map(async (grade) => {
            try {
              await unlockGrade(grade.gradeId, password.value)
              return { success: true, gradeId: grade.gradeId }
            } catch (error) {
              console.error(`解冻成绩 ${grade.gradeId} 失败:`, error)
              return { success: false, gradeId: grade.gradeId, error }
            }
          })
          
          const batchResults = await Promise.all(batchPromises)
          
          // 统计结果
          batchResults.forEach(result => {
            if (result.success) {
              successCount++
            } else {
              failCount++
            }
          })
          
          // 更新进度
          const processed = Math.min(i + batchSize, totalCount)
          progressInstance.setText(`正在解冻成绩：${processed}/${totalCount}（成功：${successCount}，失败：${failCount}）`)
        }
        
        progressInstance.close()
        
        if (successCount > 0) {
          ElMessage.success(`成功解冻 ${successCount} 个成绩${failCount > 0 ? `，${failCount} 个失败` : ''}`)
        } else {
          ElMessage.error('批量解冻失败')
        }
      } catch (processError) {
        progressInstance.close()
        throw processError
      } finally {
        unlockingAll.value = false
      }
    } catch (error) {
      loadingInstance.close()
      unlockingAll.value = false
      if (error === 'cancel') {
        return // 用户取消
      }
      console.error('批量解冻所有成绩失败:', error)
      ElMessage.error('批量解冻失败：' + (error.response?.data?.message || error.message || '未知错误'))
    }
  } catch (error) {
    if (error === 'cancel') {
      return // 用户取消密码输入或确认对话框
    }
    console.error('批量解冻所有成绩失败:', error)
    ElMessage.error('批量解冻失败：' + (error.response?.data?.message || error.message || '未知错误'))
  }
}

// 验证成绩范围
const validateScore = (student) => {
  if (!student.score || student.score === 'N') {
    return true
  }
  
  const score = parseFloat(student.score)
  
  // 检查是否为有效数字
  if (isNaN(score)) {
    ElMessage.error('成绩必须是数字或N')
    student.score = ''
    return false
  }
  
  // 检查范围
  if (score < 0 || score > 100) {
    ElMessage.error('成绩必须在0-100之间')
    student.score = ''
    return false
  }
  
  // 保留一位小数
  if (score !== Math.round(score * 10) / 10) {
    student.score = (Math.round(score * 10) / 10).toString()
  }
  
  return true
}

// 获取学生姓名的样式类
const getStudentNameClass = (student) => {
  if (student.score === 'N') {
    return 'student-name grade-n'
  } else if (hasGrade(student)) {
    return 'student-name has-grade'
  } else {
    return 'student-name no-grade'
  }
}

// 获取学号的样式类
const getStudentIdClass = (student) => {
  if (student.score === 'N') {
    return 'student-id grade-n'
  } else if (hasGrade(student)) {
    return 'student-id has-grade'
  } else {
    return 'student-id no-grade'
  }
}

// 获取表格行的样式类
const getRowClassName = ({ row }) => {
  if (row.isReadOnly) {
    return 'grade-row-readonly'
  }
  if (row.score === 'N') {
    return 'grade-row-n'
  }
  // 持久化变色：后端返回是否修改过
  if (hasGrade(row)) {
    // 未保存的“修改已有成绩”也要即时变色（不用等保存）
    if (row._backendIsModified || (row._initialGradeId && isScoreChanged(row))) {
      return 'grade-row-modified'
    }
    // 已保存但未被修改过：视为“首次录入”保持绿色
    return 'grade-row-new-entry'
  }
  return 'grade-row-pending'
}

// 统计方法
onMounted(async () => {
  const results = await Promise.allSettled([
    withTimeout(loadSemesterList()),
    withTimeout(loadSuiteOptions())
  ])
  initializationError.value = results.some(result => (
    result.status === 'rejected' || result.value?.__timeout
  ))
  initializing.value = false

  await nextTick()
  if (currentSemester.value) {
    try {
      const scheduleResult = await withTimeout(loadSchedule())
      if (scheduleResult?.__timeout) {
        initializationError.value = true
        loading.value = false
      }
    } catch {
      initializationError.value = true
    }
  }
})
</script>

<style scoped>
.grade-entry {
  padding: 0;
  height: calc(100vh - 120px);
  overflow-y: auto;
}

.grade-entry > .el-card {
  margin-bottom: 0;
}

.initialization-alert {
  margin-bottom: 16px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 10px;
}

.calendar-wrapper {
  margin-bottom: 18px;
  content-visibility: auto;
  contain-intrinsic-size: 320px;
}

.calendar-title {
  font-weight: 700;
  margin: 10px 0;
  color: var(--color-text);
}

.calendar-grid {
  display: grid;
  grid-template-columns: 120px repeat(5, minmax(0, 1fr));
  gap: 6px;
}

.calendar-grid.compact {
  gap: 6px;
}

.calendar-header {
  font-weight: 600;
  text-align: center;
  padding: 8px 0;
  font-size: 14px;
  color: var(--color-text-secondary);
}

.calendar-slot-label {
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  background: #fafafa;
  border: 1px solid #eee;
  font-size: 14px;
  padding: 8px 0;
}

.calendar-cell {
  border: 1px solid #e6e8eb;
  padding: 6px;
  min-height: 100px;
  background: #fff;
  border-radius: 6px;
}

.calendar-cell.compact {
  padding: 6px;
  min-height: 100px;
}

.class-box {
  min-width: 0;
  height: 100%;
  overflow: hidden;
}

.group-results {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.group-results.compact {
  gap: 6px;
}

.group-chip {
  border: 1px solid #dcdfe6;
  border-radius: 16px;
  padding: 6px 12px;
  line-height: 24px;
  text-align: center;
  background: #f6f8fb;
  color: #2f3640;
  cursor: pointer;
  transition: background-color 0.18s ease, border-color 0.18s ease;
}

.group-chip:hover {
  background: #eff6ff;
  border-color: #93c5fd;
  color: var(--color-primary);
}

.clickable {
  cursor: pointer;
}

.empty {
  color: #737b8c;
  font-style: italic;
}

.grade-details {
  margin-top: 20px;
}

.experiment-info {
  margin-bottom: 14px;
  padding: 14px 16px;
  background-color: var(--color-surface-soft);
  border: 1px solid var(--color-border-light);
  border-radius: 10px;
}

.experiment-info-head {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.experiment-info h4 {
  margin: 0;
  color: #2c3e50;
}

.grade-tips-inline {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-text-secondary);
  cursor: help;
}

.grade-tips-inline.keyboard-hint {
  cursor: default;
  margin-left: auto;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.table-actions {
  margin: 12px 0;
  padding: 12px 16px;
  background: var(--color-surface-soft);
  border-radius: 10px;
  border: 1px solid var(--color-border-light);
}

/* 成绩输入框放大：更易点击与录入 */
.score-input :deep(.el-input__inner) {
  font-size: 17px;
  font-weight: 600;
  text-align: center;
}

.table-actions .el-button {
  flex-shrink: 0;
}

/* 成绩录入页面字体优化 */
.grade-entry .card-header span {
  font-size: 19px;
  font-weight: 600;
  color: var(--color-text);
}

.grade-entry .calendar-title {
  font-size: 18px;
  font-weight: 700;
  margin: 12px 0;
}

.grade-entry .calendar-header {
  font-size: 16px;
  font-weight: 600;
  padding: 10px 0;
}

.grade-entry .calendar-slot-label {
  font-size: 16px;
  font-weight: 600;
}

.grade-entry .experiment-info h4 {
  font-size: 16px;
  font-weight: 600;
}

/* 成绩录入表格字体优化 */
.grade-entry .el-table {
  font-size: 15px;
}

.grade-entry .el-table th {
  font-size: 15px;
  font-weight: 600;
}

.grade-entry .el-table td {
  font-size: 15px;
}

/* 学号列加粗显示 */
.grade-entry .el-table td:first-child {
  font-weight: 600;
}

/* 姓名列加粗显示 */
.grade-entry .el-table td:nth-child(2) {
  font-weight: 600;
  color: #2c3e50;
}

/* 成绩输入框字体优化 */
.grade-entry .el-select .el-input__inner {
  font-size: 16px;
  font-weight: 500;
  text-align: center;
}

/* 对话框标题字体优化 */
.grade-entry .el-dialog__title {
  font-size: 18px;
  font-weight: 600;
}

/* 成绩录入状态颜色区分 */
.student-name, .student-id {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 500;
  transition: all 0.3s ease;
}

/* 已录入成绩 */
.student-name.has-grade, .student-id.has-grade {
  color: var(--color-text);
  font-weight: 600;
}

/* 未录入成绩 */
.student-name.no-grade, .student-id.no-grade {
  color: var(--color-text);
  font-weight: 500;
}

/* 成绩为N */
.student-name.grade-n, .student-id.grade-n {
  color: var(--color-text-secondary);
  font-style: italic;
}

/* 状态图标样式（灰阶，靠图形区分状态） */
.grade-icon {
  font-size: 14px;
  margin-right: 4px;
  color: var(--color-text-secondary);
}

.student-name.has-grade .grade-icon,
.student-name.no-grade .grade-icon,
.student-name.grade-n .grade-icon {
  color: var(--color-text-secondary);
}

/* 表格行背景色区分 */
.grade-entry :deep(.grade-row-completed),
.grade-entry :deep(.grade-row-new-entry),
.grade-entry :deep(.grade-row-pending) {
  background-color: #fff;
}

/* 仅保留“未保存修改”的淡黄提示 */
.grade-entry :deep(.grade-row-modified) {
  background-color: #fffaef;
}

.grade-entry :deep(.grade-row-readonly),
.grade-entry :deep(.grade-row-n) {
  background-color: var(--color-surface-soft);
}

/* 鼠标悬停效果（统一中性高亮） */
.grade-entry :deep(.grade-row-completed:hover),
.grade-entry :deep(.grade-row-new-entry:hover),
.grade-entry :deep(.grade-row-pending:hover) {
  background-color: var(--color-surface-soft);
}

.grade-entry :deep(.grade-row-modified:hover) {
  background-color: #fff3d6;
}

.grade-entry :deep(.grade-row-readonly:hover),
.grade-entry :deep(.grade-row-n:hover) {
  background-color: #eeeeee;
}

/* 录入进度统计样式 */
.progress-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.grade-progress {
  margin-bottom: 14px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--color-border-light);
  border-radius: 10px;
}

.grade-tips {
  margin-bottom: 16px;
  padding: 12px 14px;
  background: var(--color-surface-soft);
  border: 1px solid var(--color-border-light);
  border-radius: 10px;
  color: var(--color-text-secondary);
  line-height: 1.65;
}

.progress-label {
  color: #525866;
  font-weight: 500;
}

.progress-value {
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 14px;
}

.progress-value.total,
.progress-value.completed,
.progress-value.pending,
.progress-value.grade-n {
  background-color: var(--color-surface-soft);
  color: var(--color-text);
}

.progress-value.percentage {
  background-color: var(--color-surface-soft);
  color: var(--color-text);
  font-weight: 700;
}

/* 对话框整体样式优化 */
.grade-entry-dialog :deep(.el-dialog) {
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.16);
}

.grade-entry-dialog :deep(.el-dialog__header) {
  background: #fff;
  border-bottom: 1px solid var(--color-border-light);
  border-radius: 10px 10px 0 0;
  padding: 18px 22px;
}

.grade-entry-dialog :deep(.el-dialog__title) {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
}

.grade-entry-dialog :deep(.el-dialog__headerbtn .el-dialog__close) {
  color: #606266;
  font-size: 18px;
}

.grade-entry-dialog :deep(.el-dialog__body) {
  padding: 20px 22px;
  max-height: 85vh;
  overflow-y: auto;
}

/* 选择区域样式 */
.selection-row {
  margin-bottom: 0;
}

.selection-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.selection-label {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
  margin-bottom: 4px;
}

/* 表格样式优化 */
.grade-table :deep(.el-table__header-wrapper) {
  background: var(--color-surface-soft);
}

.changed-input :deep(.el-input__wrapper),
.changed-input :deep(.el-select__wrapper),
.changed-input :deep(.el-input__inner),
.changed-input :deep(.el-select__selection) {
  background-color: #fffdf5;
  box-shadow: 0 0 0 1px #e6c068 inset;
}

.grade-table :deep(.el-table__header th) {
  background: var(--color-surface-soft);
  color: var(--color-text);
  font-weight: 600;
  border-bottom: 1px solid var(--color-border);
}

.grade-table :deep(.el-table__body tr:hover > td) {
  background-color: #f0f9ff !important;
}

/* 批量操作区域样式 */
.action-buttons {
  display: flex;
  gap: 12px;
}

.action-buttons .el-button {
  border-radius: 6px;
  font-weight: 500;
}

/* 响应式设计 */
@media (max-width: 1200px) {
  .grade-entry-dialog :deep(.el-dialog) {
    width: 98% !important;
    margin: 1vh auto !important;
  }
  
  .selection-row .el-col {
    margin-bottom: 12px;
  }
  
  .table-actions {
    flex-direction: column;
    gap: 12px;
  }
  
  .table-actions .action-buttons {
    justify-content: center;
  }
}
</style> 
