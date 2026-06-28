import request from '@/utils/request'

// 获取成绩列表
export function getGradeList(params) {
  return request({
    url: '/grade/list',
    method: 'get',
    params
  })
}

// 新增成绩
export function addGrade(data) {
  return request({
    url: '/grade/add',
    method: 'post',
    data
  })
}

// 更新成绩
export function updateGrade(data) {
  return request({
    url: '/grade/update',
    method: 'post',
    data
  })
}

// 删除成绩
export function deleteGrade(gradeId) {
  return request({
    url: '/grade/delete',
    method: 'post',
    params: { gradeId }
  })
}

// 获取成绩统计
export function getGradeStats() {
  return request({
    url: '/grade/stats',
    method: 'get'
  })
}

// 获取教师课表
export function getTeacherSchedule() {
  return request({
    url: '/grade/teacherSchedule',
    method: 'get'
  })
}

// 获取课程学生列表
export function getScheduleStudents(scheduleId) {
  return request({
    url: '/grade/scheduleStudents',
    method: 'get',
    params: { scheduleId }
  })
}

// 新增：按小组获取学生
export function getStudentsByGroup(groupName, semesterId = null, suiteId = null, weekType = null) {
  const params = { groupName }
  if (semesterId !== null && semesterId !== undefined) {
    params.semesterId = semesterId
  }
  if (suiteId !== null && suiteId !== undefined) {
    params.suiteId = suiteId
  }
  if (weekType !== null && weekType !== undefined) {
    params.weekType = weekType
  }
  return request({
    url: '/grade/students-by-group',
    method: 'get',
    params
  })
}

// 新增：批量upsert成绩
export function batchUpsertGrades(data) {
  return request({
    url: '/grade/batch-upsert',
    method: 'post',
    data
  })
} 

// 新增：按小组+实验获取已录入成绩（仅返回 userId, score）
export function getGradesByGroupAndExperiment(params) {
  return request({
    url: '/grade/by-group-experiment',
    method: 'get',
    params
  })
}

// 冻结成绩
export function lockGrade(gradeId) {
  return request({
    url: `/grade/lock/${gradeId}`,
    method: 'post'
  })
}

// 解冻成绩
export function unlockGrade(gradeId, password) {
  return request({
    url: `/grade/unlock/${gradeId}`,
    method: 'post',
    params: {
      password: password
    }
  })
}

// 批量冻结成绩
export function batchLockGrades(gradeIds) {
  return request({
    url: '/grade/batch-lock',
    method: 'post',
    data: gradeIds
  })
}

// 获取成绩详情（包含锁定状态）
export function getGradeDetail(params) {
  return request({
    url: '/grade/group-students-detail',
    method: 'get',
    params
  })
}

// 一次获取小组在指定学期、实验套下的全部成绩
export function getGroupSuiteGrades(params) {
  return request({
    url: '/grade/group-suite-grades',
    method: 'get',
    params
  })
}

// 导出成绩Excel（xlsx）
export function exportGradesXlsx(params) {
  return request({
    url: '/grade/export-xlsx',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 120000
  })
}

// 下载成绩录入模板（xlsx）
export function downloadGradeTemplateXlsx(params) {
  return request({
    url: '/grade/template-xlsx',
    method: 'get',
    params,
    responseType: 'blob',
    timeout: 120000
  })
}

// 导入成绩模板（xlsx）
export function importGradeTemplateXlsx(file, params) {
  const formData = new FormData()
  formData.append('file', file)
  Object.keys(params || {}).forEach(k => {
    if (params[k] !== null && params[k] !== undefined) {
      formData.append(k, params[k])
    }
  })
  return request({
    url: '/grade/import-xlsx',
    method: 'post',
    data: formData,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000
  })
}
