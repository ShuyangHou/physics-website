<template>
  <div class="teacher-work">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>教师工作统计</span>
          <div class="header-actions">
            <el-select v-model="currentSemester" placeholder="选择学期" style="width: 180px; margin-right: 10px;">
              <el-option v-for="s in semesterOptions" :key="s.semesterId" :label="s.semesterName" :value="s.semesterId" />
            </el-select>
            <!-- 教师工作量统计不按实验套区分，去掉套件筛选 -->
            <el-input-number v-model="classWeight" :min="0" :max="10" :step="0.5" style="width: 160px; margin-right: 10px;" />
            <el-button type="primary" @click="reloadData">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="workRows" style="width: 100%" v-loading="loading">
        <el-table-column prop="teacherName" label="教师" min-width="160" />
        <el-table-column prop="classCount" label="实验次数" width="120" />
        <el-table-column prop="workload" label="工作量" width="140">
          <template #default="scope">
            <strong>{{ scope.row.workload }}</strong>
          </template>
        </el-table-column>
        <el-table-column label="明细" width="120">
          <template #default="scope">
            <el-button type="primary" link @click="openDetails(scope.row.teacherId)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
        </el-card>

    <el-dialog v-model="detailVisible" title="工作量明细" width="600px">
      <el-table :data="detailRows" style="width: 100%">
        <el-table-column prop="experimentName" label="实验名称" min-width="360" />
      </el-table>
      <template #footer>
        <span class="dialog-footer">
          <el-button type="primary" @click="detailVisible=false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getAllGroupExperiments } from '@/api/groupExperiment'
import { getExperimentList } from '@/api/experiment'
import { getTeacherList } from '@/api/user'
import { getSemesterList, getCurrentSemester } from '@/api/semester'
import { getAllSuites } from '@/api/suite'

const loading = ref(false)
const semesterOptions = ref([])
const suiteOptions = ref([]) // 保留变量避免报错，但不使用
const currentSemester = ref(null)
const classWeight = ref(1.0)

const teacherMap = ref(new Map()) // teacherId -> name
const experimentNameMap = ref(new Map()) // experimentId -> experimentName
const allRecords = ref([])
const workRows = ref([])

const detailVisible = ref(false)
const detailRows = ref([])

const loadSemesters = async () => {
  try {
    const resp = await getSemesterList({ current: 1, size: 100 });
    semesterOptions.value = resp?.data?.records || [];
    // 优先取本地存储，其次后端当前学期，最后回退第一个
    let stored = Number(localStorage.getItem('semesterId') || localStorage.getItem('currentSemesterId') || 0)
    if (!stored) {
      try {
        const curResp = await getCurrentSemester()
        stored = Number(curResp?.data?.semesterId || curResp?.data?.semester_id || 0)
      } catch {}
    }
    if (semesterOptions.value.length) {
      const found = stored && semesterOptions.value.find(s => Number(s.semesterId) === Number(stored))
      currentSemester.value = found ? found.semesterId : semesterOptions.value[0].semesterId
      // 同步到本地
      localStorage.setItem('semesterId', String(currentSemester.value))
      localStorage.setItem('currentSemesterId', String(currentSemester.value))
    }
  } catch { semesterOptions.value = [] }
}

// 选择学期后持久化
watch(currentSemester, (val) => {
  if (val != null) {
    localStorage.setItem('semesterId', String(val))
    localStorage.setItem('currentSemesterId', String(val))
  }
})
const loadSuites = async () => { suiteOptions.value = [] }
const loadTeachers = async () => {
  try { const resp = await getTeacherList({ current: 1, size: 1000 }); const list = resp?.data?.records || []; teacherMap.value = new Map(list.map(t => [String(t.userId), t.realName])) } catch { teacherMap.value = new Map() }
}

const reloadData = async () => {
  loading.value = true
  try {
    // 优化：直接调用后端API，后端已经包含了教师名称和实验名称
    const params = {}
    if (currentSemester.value) params.semesterId = currentSemester.value
    const resp = await getAllGroupExperiments(params)
    const list = resp?.data || []
    allRecords.value = list
    
    // 汇总：仅统计有 teacherId 的记录
    const byTeacher = new Map()
    list.filter(r => r.teacherId).forEach(r => {
      const key = String(r.teacherId)
      const arr = byTeacher.get(key) || []
      arr.push(r)
      byTeacher.set(key, arr)
    })
    
    const rows = []
    byTeacher.forEach((arr, tid) => {
      // 使用后端返回的教师名称，无需前端查询
      const name = arr[0].teacherName || `教师${tid}`
      const count = arr.length
      const workload = Math.round(count * Number(classWeight.value) * 10) / 10
      rows.push({ teacherId: tid, teacherName: name, classCount: count, workload })
    })
    
    workRows.value = rows.sort((a,b) => b.workload - a.workload)
  } catch (e) {
    console.error('加载工作量失败:', e)
    ElMessage.error('加载工作量失败')
  } finally { 
    loading.value = false 
  }
}

const openDetails = (teacherId) => {
  const arr = allRecords.value.filter(r => String(r.teacherId) === String(teacherId))
  // 仅显示实验名称，并按名称去重
  const seen = new Set()
  const rows = []
  for (const r of arr) {
    // 优化：直接使用后端返回的实验名称
    const name = r.experimentName || resolveExperimentName(r)
    if (!name || seen.has(name)) continue
    seen.add(name)
    rows.push({ experimentName: name })
  }
  detailRows.value = rows
  detailVisible.value = true
}

// init
;(async () => { await loadSemesters(); await reloadData() })()

// helpers
const loadAllExperiments = async () => {
  try {
    const resp = await getExperimentList({ current: 1, size: 2000 })
    const list = resp?.data?.records || resp?.data || []
    experimentNameMap.value = new Map(list.map(e => [String(e.experimentId), e.experimentName]))
  } catch { experimentNameMap.value = new Map() }
}

const resolveExperimentName = (r) => {
  if (r.experimentName) return r.experimentName
  if (r.experimentId != null) {
    const name = experimentNameMap.value.get(String(r.experimentId))
    if (name) return name
  }
  return ''
}

const resolveTimeText = (r) => {
  // 支持两种来源：
  // 1) r.experimentTime 可能是 "周一上午" 等文本
  // 2) 也可能是包含 week/weekday/slot 的 JSON 字符串
  const raw = r.experimentTime
  if (!raw) return ''
  if (typeof raw === 'string' && raw.startsWith('{')) {
    try {
      const t = JSON.parse(raw)
      // 优先展示“周几上午/下午”风格，如果有 weekday/slot
      if (t.weekday && t.slot) return `${t.weekday}${t.slot}`
      if (t.week) return `第${t.week}周`
    } catch {}
  }
  return raw
}
</script>

<style scoped>
.teacher-work { padding: 20px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.header-actions { display: flex; align-items: center; gap: 10px; }
</style> 