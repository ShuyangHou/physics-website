<template>
  <div class="teacher-schedule">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>教师课表（打印视图）</span>
          <div class="header-actions">
            <div class="zoom-control">
              <span class="zoom-label">缩放</span>
              <el-slider
                v-model="zoomPercent"
                :min="70"
                :max="130"
                :step="5"
                style="width: 160px; margin: 0 8px;"
                @change="handleZoomChange"
              />
              <span class="zoom-value">{{ zoomPercent }}%</span>
            </div>
            <el-select v-model="currentSemester" placeholder="选择学期" style="width: 200px; margin-right: 10px;" @change="handleSemesterChange">
              <el-option 
                v-for="semester in semesterOptions" 
                :key="semester.semesterId" 
                :label="semester.semesterName" 
                :value="semester.semesterId" 
              />
            </el-select>
            <el-select v-model="selectedSuiteId" placeholder="选择实验套" style="width: 220px; margin-right: 10px;" clearable @change="optimizedRefreshData({ force: true })">
              <el-option :key="0" :label="'全部实验套'" :value="null" />
              <el-option
                v-for="s in suiteOptions"
                :key="s.suiteId || s.experimentSuiteId"
                :label="s.suiteName"
                :value="s.suiteId || s.experimentSuiteId"
              />
            </el-select>
            <el-button type="primary" @click="optimizedRefreshData({ force: true })" :loading="loading">刷新数据</el-button>
            <el-button type="success" @click="printTeacherSchedule" :loading="printing" style="margin-left: 8px;">
              <el-icon><Printer /></el-icon>
              打印教师课表
            </el-button>
          </div>
        </div>
      </template>

      <div class="schedule-content" v-loading="loading">
        <div v-if="!currentSemester" class="no-data">
          <el-empty description="请先选择学期" />
        </div>
        <div v-else class="preview-wrap">
          <div class="preview" :style="previewStyle" v-html="previewHTML"></div>
        </div>
      </div>
    </el-card>
  </div>
  </template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { getSemesterList, getCurrentSemester, getWeekInfo } from '@/api/semester'
import { getAllSuites } from '@/api/suite'
import { getTeacherList } from '@/api/user'
import { getScheduleForDisplay } from '@/api/schedule'
import { getAllGroupExperiments } from '@/api/groupExperiment'

const currentSemester = ref(null)
const semesterOptions = ref([])
const loading = ref(false)
const printing = ref(false)
const selectedSuiteId = ref(null)
const suiteOptions = ref([])
const scheduleList = ref([])
const allGroupExperimentsData = ref([])
const teacherListRef = ref([])
const weekdays = ['周一','周二','周三','周四','周五']
const previewHTML = ref('')
const zoomPercent = ref(Number(localStorage.getItem('teacherScheduleZoom') || 100))
let refreshSeq = 0
let refreshPromise = null

// 规则B：动态教学周（来自 /semester/week-info）
const teachingWeeksOdd = ref([])
const teachingWeeksEven = ref([])
const totalWeeks = ref(20)

const previewStyle = computed(() => {
  const z = (Number(zoomPercent.value) || 100) / 100
  return {
    transform: `scale(${z})`,
    transformOrigin: 'top left',
    width: `${100 / z}%`
  }
})

const handleZoomChange = (val) => {
  try {
    localStorage.setItem('teacherScheduleZoom', String(val))
  } catch (e) {
    // ignore
  }
}

// 数据加载
const refreshData = async () => {
  if (!currentSemester.value) return
  loading.value = true
  try {
    // 周次信息
    try {
      const info = await getWeekInfo({ semesterId: currentSemester.value })
      if (info?.code === 200 && info?.data) {
        const odd = Array.isArray(info.data.teachingWeeksOdd) ? info.data.teachingWeeksOdd : []
        const even = Array.isArray(info.data.teachingWeeksEven) ? info.data.teachingWeeksEven : []
        teachingWeeksOdd.value = odd.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
        teachingWeeksEven.value = even.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
        const tw = Number(info.data.totalWeeks)
        if (!Number.isNaN(tw) && tw > 0) totalWeeks.value = tw
      }
    } catch (e) {
      // ignore
    }
    // 套件
    const suitesResp = await getAllSuites()
    suiteOptions.value = suitesResp?.data || []
    if (!selectedSuiteId.value && suiteOptions.value.length) {
      selectedSuiteId.value = suiteOptions.value[0].suiteId || suiteOptions.value[0].experimentSuiteId
    }
    // 教师
    const teacherResp = await getTeacherList({ current: 1, size: 200 })
    teacherListRef.value = teacherResp?.data?.records || []
    // 课表（显示用）
    const schedResp = await getScheduleForDisplay({ semesterId: currentSemester.value })
    scheduleList.value = Array.isArray(schedResp?.data) ? schedResp.data : []
    // 小组安排
    const geResp = await getAllGroupExperiments({ semesterId: currentSemester.value })
    allGroupExperimentsData.value = geResp?.data || []
    // 生成预览
    previewHTML.value = generateTeacherScheduleHTML()
  } catch (e) {
    console.error('加载打印数据失败:', e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

const optimizedRefreshData = async (options = {}) => {
  if (!currentSemester.value) return
  if (refreshPromise && !options.force) return refreshPromise

  const seq = ++refreshSeq
  loading.value = true
  refreshPromise = (async () => {
    try {
      const semester = currentSemester.value
      const [info, suitesResp, teacherResp, schedResp, geResp] = await Promise.all([
        getWeekInfo({ semesterId: semester }).catch(() => null),
        getAllSuites(),
        getTeacherList({ current: 1, size: 200 }),
        getScheduleForDisplay({ semesterId: semester }),
        getAllGroupExperiments({ semesterId: semester })
      ])

      if (seq !== refreshSeq) return

      if (info?.code === 200 && info?.data) {
        const odd = Array.isArray(info.data.teachingWeeksOdd) ? info.data.teachingWeeksOdd : []
        const even = Array.isArray(info.data.teachingWeeksEven) ? info.data.teachingWeeksEven : []
        teachingWeeksOdd.value = odd.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
        teachingWeeksEven.value = even.map(n => Number(n)).filter(n => !Number.isNaN(n) && n > 0)
        const tw = Number(info.data.totalWeeks)
        if (!Number.isNaN(tw) && tw > 0) totalWeeks.value = tw
      }

      suiteOptions.value = suitesResp?.data || []
      if (!selectedSuiteId.value && suiteOptions.value.length) {
        selectedSuiteId.value = suiteOptions.value[0].suiteId || suiteOptions.value[0].experimentSuiteId
      }
      teacherListRef.value = teacherResp?.data?.records || []
      scheduleList.value = Array.isArray(schedResp?.data) ? schedResp.data : []
      allGroupExperimentsData.value = geResp?.data || []
      previewHTML.value = generateTeacherScheduleHTML()
    } catch (e) {
      console.error('加载打印数据失败:', e)
      ElMessage.error('加载数据失败')
    } finally {
      if (seq === refreshSeq) {
        loading.value = false
        refreshPromise = null
      }
    }
  })()

  return refreshPromise
}

// 工具
const weekdayToChar = (w) => String(w).replace('周','')
const buildTimeString = (weekType, weekday, slot) => `${weekType === 0 ? '单周' : '双周'}${weekdayToChar(weekday)}${slot === 'morning' ? '上午' : '下午'}`

const getTeachingWeeksByWeekType = (wt) => {
  // 兼容旧逻辑：单周包含第1周，双周包含第2周；教学周来自后端动态列表
  if (wt === 0) {
    const base = (Array.isArray(teachingWeeksOdd.value) && teachingWeeksOdd.value.length)
      ? teachingWeeksOdd.value
      : (() => {
          const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
          const weeks = []
          for (let w = 3; w <= maxWeek; w++) {
            if (w % 2 === 1) weeks.push(w)
          }
          return weeks
        })()
    return [1, ...base]
  }
  if (wt === 1) {
    const base = (Array.isArray(teachingWeeksEven.value) && teachingWeeksEven.value.length)
      ? teachingWeeksEven.value
      : (() => {
          const maxWeek = (totalWeeks.value && Number(totalWeeks.value) > 0) ? Number(totalWeeks.value) : 20
          const weeks = []
          for (let w = 3; w <= maxWeek; w++) {
            if (w % 2 === 0) weeks.push(w)
          }
          return weeks
        })()
    // 注意：fallback里已包含2；为统一起见仍保持 [2,...base]，并去重
    return Array.from(new Set([2, ...base]))
  }
  return []
}

const findScheduleForCell = (suiteId, weekType, weekday, slot) => {
  const t = buildTimeString(weekType, weekday, slot)
  return scheduleList.value.find(s => {
    const sSuiteId = s.suiteId || s.experimentSuiteId
    const suiteOk = !suiteId || String(sSuiteId) === String(suiteId)
    const weekOk = String(s.weekType) === String(weekType)
    const timeOk = (s.experimentTime || '') === t || (s.experimentTime || '').replace(/^(单|双)周/,'周') === t || (s.experimentTime || '').replace(/^(单|双)/,'') === t
    return suiteOk && weekOk && timeOk
  })
}

const getGroupIdsForCell = (suiteId, weekType, slot, weekday) => {
  const s = findScheduleForCell(suiteId, weekType, weekday, slot)
  if (!s || !s.groupIds) return []
  const groups = s.groupIds.replaceAll(/[\[\]]/g,'').split(',').map(v=>v.trim()).filter(Boolean)
  // 返回小组列表，用于后续处理
  return groups
}

const getTeacherNamesForCell = (suiteId, weekType, slot, weekday) => {
  const s = findScheduleForCell(suiteId, weekType, weekday, slot)
  if (s && s.teacherIds) {
    const ids = s.teacherIds.replaceAll(/[\[\]]/g,'').split(',').map(v=>v.trim()).filter(Boolean)
    const names = ids.map(id => {
      const t = teacherListRef.value.find(x => String(x.userId) === String(id))
      return t ? t.realName : id
    })
    // 绪论课标记：以 experiment_schedule.introTeacherId 为唯一权威来源，确保与“教师设置”一致
    const introId = (s && s.introTeacherId != null) ? String(s.introTeacherId) : ''
    const flags = ids.map(teacherId => (introId && String(teacherId) === introId) ? '绪' : '')
    return { teacherNames: names, introCourseFlags: flags }
  }
  return { teacherNames: [], introCourseFlags: [] }
}

// 生成预览HTML（简单格式：每行两个小组，用空格分隔）
const generateTeacherWeekScheduleHTML = (suiteId, weekType) => {
  let html = '<div class="schedule-table">'
  html += '<div class="table-header">'
  html += '<div class="header-cell">时间</div>'
  html += '<div class="header-cell">星期一</div>'
  html += '<div class="header-cell">星期二</div>'
  html += '<div class="header-cell">星期三</div>'
  html += '<div class="header-cell">星期四</div>'
  html += '<div class="header-cell">星期五</div>'
  html += '</div>'
  const renderRow = (slotLabel, slotKey) => {
    html += '<div class="table-row">'
    html += `<div class="time-cell">${slotLabel}</div>`
    for (const weekday of weekdays) {
      const groupList = getGroupIdsForCell(suiteId, weekType, slotKey, weekday)
      const flatGroups = Array.isArray(groupList) ? groupList : []
      const td = getTeacherNamesForCell(suiteId, weekType, slotKey, weekday)
      const names = Array.isArray(td?.teacherNames) ? td.teacherNames : []
      const flags = Array.isArray(td?.introCourseFlags) ? td.introCourseFlags : []
      
      // 预览格式：每行两个小组，用空格分隔
      const groupRows = []
      for (let i = 0; i < flatGroups.length; i += 2) {
        if (i + 1 < flatGroups.length) {
          groupRows.push(`${flatGroups[i]} ${flatGroups[i + 1]}`)
        } else {
          groupRows.push(flatGroups[i])
        }
      }
      const groupText = groupRows.join('\n')
      
      html += '<div class="schedule-cell">'
      html += '<div class="cell-content">'
      html += '<div class="left-section">'
      html += `<div class="group-ids">${groupText}</div>`
      html += '</div>'
      html += '<div class="divider"></div>'
      html += '<div class="right-section">'
      html += '<div class="teacher-names-column">'
      for (let i = 0; i < names.length; i++) {
        html += `<div class="teacher-name-cell">${names[i] || ''}</div>`
      }
      html += '</div>'
      html += '<div class="divider-vertical"></div>'
      html += '<div class="intro-course-column">'
      for (let i = 0; i < flags.length; i++) {
        html += `<div class="intro-course-cell">${flags[i] || ''}</div>`
      }
      html += '</div>'
      html += '</div>'
      html += '</div>'
      html += '</div>'
    }
    html += '</div>'
  }
  renderRow('上午','morning')
  renderRow('下午','afternoon')
  html += '</div>'
  return html
}

// 生成打印HTML（使用group-row结构，用于精确对齐）
const generateTeacherWeekScheduleHTMLForPrint = (suiteId, weekType) => {
  let html = '<div class="schedule-table">'
  html += '<div class="table-header">'
  html += '<div class="header-cell">时间</div>'
  html += '<div class="header-cell">星期一</div>'
  html += '<div class="header-cell">星期二</div>'
  html += '<div class="header-cell">星期三</div>'
  html += '<div class="header-cell">星期四</div>'
  html += '<div class="header-cell">星期五</div>'
  html += '</div>'
  const renderRow = (slotLabel, slotKey) => {
    html += '<div class="table-row">'
    html += `<div class="time-cell">${slotLabel}</div>`
    for (const weekday of weekdays) {
      const groupList = getGroupIdsForCell(suiteId, weekType, slotKey, weekday)
      const flatGroups = Array.isArray(groupList) ? groupList : []
      const td = getTeacherNamesForCell(suiteId, weekType, slotKey, weekday)
      const names = Array.isArray(td?.teacherNames) ? td.teacherNames : []
      const flags = Array.isArray(td?.introCourseFlags) ? td.introCourseFlags : []
      
      html += '<div class="schedule-cell">'
      html += '<div class="cell-content">'
      html += '<div class="left-section">'
      html += '<div class="group-ids">'
      // 每行显示两个小组
      for (let i = 0; i < flatGroups.length; i += 2) {
        html += '<div class="group-row">'
        html += `<div class="group-item">${flatGroups[i] || ''}</div>`
        if (i + 1 < flatGroups.length) {
          html += `<div class="group-item">${flatGroups[i + 1] || ''}</div>`
        }
        html += '</div>'
      }
      html += '</div>'
      html += '</div>'
      html += '<div class="divider"></div>'
      html += '<div class="right-section">'
      html += '<div class="teacher-names-column">'
      // 每行显示一个老师，一个老师对应两个小组
      for (let i = 0; i < names.length; i++) {
        html += `<div class="teacher-name-cell">${names[i] || ''}</div>`
      }
      html += '</div>'
      html += '<div class="divider-vertical"></div>'
      html += '<div class="intro-course-column">'
      for (let i = 0; i < flags.length; i++) {
        html += `<div class="intro-course-cell">${flags[i] || ''}</div>`
      }
      html += '</div>'
      html += '</div>'
      html += '</div>'
      html += '</div>'
    }
    html += '</div>'
  }
  renderRow('上午','morning')
  renderRow('下午','afternoon')
  html += '</div>'
  return html
}

const generateTeacherScheduleHTML = () => {
  if (!suiteOptions.value.length) return '<div class="no-data">没有实验套数据</div>'
  const suites = selectedSuiteId.value
    ? suiteOptions.value.filter(s => (s.experimentSuiteId || s.suiteId) === selectedSuiteId.value)
    : suiteOptions.value
  let html = ''
  for (const suite of suites) {
    const sid = suite.experimentSuiteId || suite.suiteId
    html += '<div class="suite-schedule">'
    html += `<h2 class="suite-title">${suite.suiteName}教师课表</h2>`
    html += '<div class="week-schedule">'
    html += '<h3 class="week-title">单周</h3>'
    html += generateTeacherWeekScheduleHTML(sid, 0)
    html += '</div>'
    html += '<div class="week-schedule">'
    html += '<h3 class="week-title">双周</h3>'
    html += generateTeacherWeekScheduleHTML(sid, 1)
    html += '</div>'
    html += '</div>'
  }
  return html
}

const generateTeacherScheduleHTMLForPrint = () => {
  if (!suiteOptions.value.length) return '<div class="no-data">没有实验套数据</div>'
  const suites = selectedSuiteId.value
    ? suiteOptions.value.filter(s => (s.experimentSuiteId || s.suiteId) === selectedSuiteId.value)
    : suiteOptions.value
  let html = ''
  for (const suite of suites) {
    const sid = suite.experimentSuiteId || suite.suiteId
    html += '<div class="suite-schedule">'
    html += `<h2 class="suite-title">${suite.suiteName}教师课表</h2>`
    html += '<div class="week-schedule">'
    html += '<h3 class="week-title">单周</h3>'
    html += generateTeacherWeekScheduleHTMLForPrint(sid, 0)
    html += '</div>'
    html += '<div class="week-schedule">'
    html += '<h3 class="week-title">双周</h3>'
    html += generateTeacherWeekScheduleHTMLForPrint(sid, 1)
    html += '</div>'
    html += '</div>'
  }
  return html
}

const printTeacherSchedule = async () => {
  // 立即打开窗口（在异步操作之前），避免浏览器拦截
  const printWindow = window.open('', '_blank', 'width=1600,height=900,resizable=yes,scrollbars=yes')
  if (!printWindow) {
    ElMessage.error('浏览器阻止了弹窗，请允许弹窗后重试')
    return
  }
  
  // 显示加载提示
  printWindow.document.write(`
    <html>
      <head><meta charset="UTF-8"><title>教师课表打印</title></head>
      <body style="display:flex;align-items:center;justify-content:center;height:100vh;font-size:16px;">
        <div>正在加载打印内容，请稍候...</div>
      </body>
    </html>
  `)
  printWindow.document.close()
  
  try {
    printing.value = true
    
    if (!currentSemester.value) {
      printWindow.close()
      ElMessage.warning('请先选择学期')
      return
    }
    
    // 在打印前，重新加载最新的数据
    ElMessage.info('正在加载最新数据...')
    
    // 确保有实验套选项，如果没有则加载
    if (suiteOptions.value.length === 0) {
      try {
        const resp = await getAllSuites()
        suiteOptions.value = resp?.data || []
        if (suiteOptions.value.length === 0) {
          printWindow.close()
          ElMessage.warning('请先加载实验套信息')
          return
        }
      } catch (error) {
        console.error('加载实验套选项失败:', error)
        printWindow.close()
        ElMessage.warning('加载实验套信息失败')
        return
      }
    }
    
    // 使用 getScheduleForDisplay API 加载课表数据（与页面显示一致）
    try {
      const params = {}
      if (currentSemester.value) {
        params.semesterId = currentSemester.value
      }
      const response = await getScheduleForDisplay(params)
      scheduleList.value = response.data || []
    } catch (error) {
      console.error('加载课表数据失败:', error)
      ElMessage.warning('加载课表数据失败，将使用已有数据')
    }
    
    // 加载教师列表（如果还没有加载）
    if (!teacherListRef.value || teacherListRef.value.length === 0) {
      try {
        const teacherResponse = await getTeacherList({ current: 1, size: 100 })
        teacherListRef.value = teacherResponse.data.records || []
      } catch (error) {
        console.error('加载教师列表失败:', error)
      }
    }
    
    // 加载所有实验套的group_experiment数据
    try {
      const params = {
        semesterId: currentSemester.value
      }
      const response = await getAllGroupExperiments(params)
      allGroupExperimentsData.value = response.data || []
    } catch (error) {
      console.error('加载group_experiment数据失败:', error)
      ElMessage.warning('加载实验安排数据失败，将使用已有数据')
    }
    
    // 显示准备打印的消息
    ElMessage.success('正在生成打印内容...')
    
    // 创建教师课表打印内容（使用打印专用格式）
    const content = generateTeacherScheduleHTMLForPrint()
    
    // 准备打印内容
    const html = `<!DOCTYPE html><html><head><meta charset="UTF-8"><title>教师课表打印</title>
    <style>
    @page{size:A4 landscape;margin:10mm}
    *{box-sizing:border-box !important;margin:0 !important;padding:0 !important}
    html,body{width:100% !important;height:100% !important;transform:scale(1) !important;transform-origin:top left !important}
    body{font-family:'Microsoft YaHei','SimSun',Arial,sans-serif !important;margin:8px !important;background:#fff !important;font-size:11px !important;line-height:1.4 !important}
    .suite-schedule{margin-bottom:30px !important;page-break-after:always !important}
    .suite-title{text-align:center !important;font-size:20px !important;font-weight:bold !important;margin-bottom:15px !important;color:#000 !important}
    .week-title{font-size:16px !important;font-weight:bold !important;margin:12px 0 !important;color:#000 !important;border-left:3px solid #000 !important;padding-left:12px !important}
    .schedule-table{width:100% !important;margin:12px 0 !important;border:1px solid #000 !important;border-radius:6px !important;overflow:visible !important;table-layout:fixed !important;display:block !important}
    .table-header{display:flex !important;background:#f8f9fa !important;flex-direction:row !important;width:100% !important}
    .header-cell{flex:0 0 18.4% !important;width:18.4% !important;border-right:1px solid #000 !important;padding:8px 4px !important;text-align:center !important;font-weight:bold !important;background:#f8f9fa !important;font-size:11px !important;color:#000 !important;min-width:0 !important;max-width:18.4% !important;overflow:visible !important}
    .header-cell:first-child{background:#e3f2fd !important;color:#000 !important;flex:0 0 60px !important;width:60px !important;min-width:60px !important;max-width:60px !important}
    .header-cell:last-child{border-right:none !important}
    .table-body{display:flex !important;flex-direction:column !important}
    .table-row{display:flex !important;border-bottom:1px solid #000 !important;flex-direction:row !important;width:100% !important}
    .table-row:last-child{border-bottom:none !important}
    .time-cell{flex:0 0 60px !important;width:60px !important;min-width:60px !important;max-width:60px !important;padding:8px 4px !important;text-align:center !important;background:#f3e5f5 !important;border-right:1px solid #000 !important;border-bottom:1px solid #000 !important;font-weight:bold !important;color:#000 !important;font-size:12px !important;display:flex !important;align-items:center !important;justify-content:center !important}
    .schedule-cell{flex:0 0 18.4% !important;width:18.4% !important;min-width:0 !important;max-width:18.4% !important;border-right:1px solid #000 !important;padding:6px 4px !important;text-align:left !important;min-height:100px !important;background:white !important;display:flex !important;align-items:flex-start !important;overflow:visible !important}
    .schedule-cell:last-child{border-right:none !important}
    .cell-content{width:100% !important;min-height:100px !important;display:flex !important;flex-direction:row !important;justify-content:space-between !important;align-items:flex-start !important;gap:0 !important;padding:0 !important;margin:0 !important;overflow:visible !important}
    .left-section{flex:1 !important;min-width:0 !important;max-width:50% !important;display:flex !important;align-items:flex-start !important;justify-content:flex-start !important;padding-right:8px !important;padding-top:0 !important;overflow:visible !important}
    .right-section{flex:1 !important;min-width:0 !important;max-width:50% !important;display:flex !important;flex-direction:row !important;align-items:flex-start !important;justify-content:flex-start !important;padding-left:8px !important;overflow:visible !important}
    .divider{width:2px !important;background:#000 !important;margin:0 6px !important;align-self:stretch !important;flex-shrink:0 !important;flex-grow:0 !important}
    .group-ids{margin:0 !important;padding:0 !important;font-size:9px !important;line-height:1.8 !important;color:#000 !important;font-weight:normal !important;white-space:pre-line !important;word-break:keep-all !important;overflow-wrap:break-word !important;text-align:left !important;font-family:'Microsoft YaHei','SimSun',monospace !important;overflow:visible !important;display:flex !important;flex-direction:column !important;gap:0 !important}
    .group-row{display:flex !important;flex-direction:row !important;justify-content:flex-start !important;align-items:center !important;gap:8px !important;min-height:14px !important;height:14px !important;line-height:14px !important}
    .group-item{font-size:9px !important;line-height:14px !important;height:14px !important;display:flex !important;align-items:center !important}
    .teacher-names-column{display:flex !important;flex-direction:column !important;gap:0 !important;flex:1 !important;min-width:0 !important;padding:0 !important;margin:0 !important;overflow:visible !important}
    .teacher-name-cell{text-align:left !important;font-size:9px !important;line-height:14px !important;min-height:14px !important;height:14px !important;color:#000 !important;font-weight:normal !important;word-break:keep-all !important;overflow-wrap:break-word !important;font-family:'Microsoft YaHei','SimSun',monospace !important;padding:0 !important;margin:0 !important;white-space:normal !important;writing-mode:horizontal-tb !important;min-width:60px !important;overflow:visible !important;display:flex !important;align-items:center !important}
    .divider-vertical{width:2px !important;background:#000 !important;margin:0 6px !important;align-self:stretch !important;flex-shrink:0 !important;flex-grow:0 !important}
    .intro-course-column{display:flex !important;flex-direction:column !important;gap:0 !important;width:25px !important;flex-shrink:0 !important;flex-grow:0 !important;padding:0 !important;margin:0 !important;align-items:center !important;overflow:visible !important}
    .intro-course-cell{text-align:center !important;font-size:9px !important;line-height:14px !important;min-height:14px !important;height:14px !important;color:#000 !important;font-weight:normal !important;font-family:'Microsoft YaHei','SimSun',monospace !important;padding:0 !important;margin:0 !important;white-space:nowrap !important;writing-mode:horizontal-tb !important;overflow:visible !important;display:flex !important;align-items:center !important;justify-content:center !important}
    @media print{
      @page{size:A4 landscape;margin:10mm}
      html,body{width:100% !important;transform:scale(1) !important}
      .suite-schedule{page-break-after:always !important}
      body{margin:8px !important}
      *{print-color-adjust:exact !important;-webkit-print-color-adjust:exact !important}
    }
    </style></head><body><div class="preview" style="background:white;padding:20px;width:100%;box-sizing:border-box">${content}</div></body></html>`
    
    printWindow.document.write(html)
    printWindow.document.close()
    
    // 等待内容加载完成后打印
    printWindow.onload = () => {
      ElMessage.success('打印窗口已打开，请选择打印选项')
      
      // 简单的打印方式，让用户手动关闭窗口
      setTimeout(() => {
        try {
          printWindow.focus()
          printWindow.print()
          ElMessage.info('打印对话框已打开，完成后请手动关闭打印窗口')
        } catch (error) {
          console.error('打印过程出错:', error)
          if (!printWindow.closed) {
            printWindow.close()
          }
          ElMessage.error('打印过程出错，请重试')
        }
      }, 300)
    }
    
  } catch (error) {
    console.error('打印教师课表失败:', error)
    if (printWindow && !printWindow.closed) {
      printWindow.close()
    }
    ElMessage.error('打印教师课表失败: ' + error.message)
  } finally {
    printing.value = false
  }
}

// 加载学期列表
const loadSemesters = async () => {
  try {
    const resp = await getSemesterList({ current: 1, size: 100 })
    semesterOptions.value = resp?.data?.records || []
    
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
  } catch (error) {
    console.error('加载学期列表失败:', error)
    semesterOptions.value = []
  }
}

// 选择学期后持久化
const handleSemesterChange = (val) => {
  if (val != null) {
    localStorage.setItem('semesterId', String(val))
    localStorage.setItem('currentSemesterId', String(val))
  }
}

// 刷新课表
const handleRefresh = async () => {
  loading.value = true
  try {
    await refreshData()
  } catch (error) {
    ElMessage.error('刷新失败')
  } finally {
    loading.value = false
  }
}

// 页面加载时初始化
onMounted(async () => {
  await loadSemesters()
  await optimizedRefreshData()
})
</script>

<style scoped>
.teacher-schedule {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
}

.schedule-content {
  margin-top: 20px;
}

.preview-wrap {
  width: 100%;
  overflow: auto;
  max-height: calc(100vh - 240px);
  border: 1px solid #ebeef5;
  border-radius: 8px;
}

.no-data {
  text-align: center;
  padding: 40px 0;
}

.preview {
  background: white;
  padding: 20px;
  width: 100%;
  box-sizing: border-box;
}

.zoom-control {
  display: flex;
  align-items: center;
  margin-right: 10px;
  padding: 0 10px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
}

.zoom-label {
  font-size: 14px;
  color: #606266;
}

.zoom-value {
  font-size: 14px;
  color: #606266;
  width: 42px;
  text-align: right;
}

.preview :deep(.suite-schedule) {
  margin-bottom: 30px;
}

.preview :deep(.suite-title) {
  text-align: center;
  font-size: 22px;
  font-weight: bold;
  margin-bottom: 15px;
  color: #000;
}

.preview :deep(.week-title) {
  font-size: 18px;
  font-weight: bold;
  margin: 12px 0;
  color: #000;
  border-left: 3px solid #000;
  padding-left: 12px;
}

.preview :deep(.schedule-table) {
  width: 100%;
  margin: 12px 0;
  border: 1px solid #000;
  border-radius: 6px;
  overflow: hidden;
  table-layout: fixed;
  border-collapse: collapse;
  line-height: 1;
}

.preview :deep(.schedule-table > *) {
  line-height: 1;
}

.preview :deep(.table-header) {
  display: flex;
  background: #f8f9fa;
  border-bottom: 1px solid #000;
  margin: 0;
  padding: 0;
  line-height: 1;
  vertical-align: top;
}

.preview :deep(.header-cell) {
  flex: 0 0 calc((100% - 60px) / 5);
  border-right: 1px solid #000;
  padding: 10px 6px;
  text-align: center;
  font-weight: bold;
  background: #f8f9fa;
  font-size: 13px;
  color: #000;
  margin: 0;
  border-bottom: none;
}

.preview :deep(.header-cell:first-child) {
  background: #e3f2fd;
  color: #000;
  flex: 0 0 60px;
  border-bottom: 1px solid #000;
}

.preview :deep(.header-cell:last-child) {
  border-right: none;
}

.preview :deep(.table-row) {
  display: flex;
  border-bottom: 1px solid #000;
  margin: 0;
  padding: 0;
  border-top: none;
  line-height: 1;
  vertical-align: top;
}

.preview :deep(.table-row:first-child) {
  border-top: none;
}

.preview :deep(.table-row:last-child) {
  border-bottom: none;
}

.preview :deep(.time-cell) {
  flex: 0 0 60px;
  padding: 8px 4px;
  text-align: center;
  background: #f3e5f5;
  border-right: 1px solid #000;
  border-top: none;
  border-bottom: none;
  font-weight: bold;
  color: #000;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0;
}

.preview :deep(.schedule-cell) {
  flex: 0 0 calc((100% - 60px) / 5);
  border-right: 1px solid #000;
  border-top: none;
  border-bottom: none;
  padding: 8px 6px;
  text-align: left;
  min-height: 100px;
  background: white;
  display: flex;
  align-items: flex-start;
  margin: 0;
}

.preview :deep(.schedule-cell:last-child) {
  border-right: none;
}

.preview :deep(.cell-content) {
  width: 100%;
  min-height: 100px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-start;
  gap: 0;
  padding: 0;
  margin: 0;
}

.preview :deep(.left-section) {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  padding-right: 10px;
  padding-top: 0;
}

.preview :deep(.right-section) {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: flex-start;
  padding-left: 10px;
}

.preview :deep(.divider) {
  width: 2px;
  background: #000;
  margin: 0 8px;
  align-self: stretch;
  flex-shrink: 0;
}

.preview :deep(.group-ids) {
  margin: 0;
  padding: 0;
  font-size: 13px;
  line-height: 1.4;
  color: #000;
  font-weight: normal;
  white-space: pre-line;
  word-break: keep-all;
  overflow-wrap: break-word;
  text-align: left;
  font-family: 'Microsoft YaHei', 'SimSun', monospace;
}

.preview :deep(.teacher-names-column) {
  display: flex;
  flex-direction: column;
  gap: 0;
  flex: 1;
  min-width: 0;
  padding: 0;
  margin: 0;
}

.preview :deep(.teacher-name-cell) {
  text-align: left;
  font-size: 12px;
  line-height: 18px;
  min-height: 18px;
  height: 18px;
  color: #000;
  font-weight: normal;
  word-break: keep-all;
  overflow-wrap: break-word;
  font-family: 'Microsoft YaHei', 'SimSun', monospace;
  padding: 0;
  margin: 0;
  white-space: normal;
  writing-mode: horizontal-tb;
  min-width: 60px;
  display: flex;
  align-items: center;
}

.preview :deep(.divider-vertical) {
  width: 2px;
  background: #000;
  margin: 0 6px;
  align-self: stretch;
  flex-shrink: 0;
}

.preview :deep(.intro-course-column) {
  display: flex;
  flex-direction: column;
  gap: 0;
  width: 25px;
  flex-shrink: 0;
  padding: 0;
  margin: 0;
  align-items: flex-start;
}

.preview :deep(.intro-course-cell) {
  text-align: center;
  font-size: 12px;
  line-height: 18px;
  min-height: 18px;
  height: 18px;
  color: #000;
  font-weight: normal;
  font-family: 'Microsoft YaHei', 'SimSun', monospace;
  padding: 0;
  margin: 0;
  white-space: nowrap;
  writing-mode: horizontal-tb;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
}
</style> 
