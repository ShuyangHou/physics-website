import request from '@/utils/request'

// 获取安排列表
export function getScheduleList(params) {
  return request({
    url: '/schedule/list',
    method: 'get',
    params
  })
}

// 获取安排详情
export function getScheduleDetail(scheduleId) {
  return request({
    url: '/schedule/detail',
    method: 'get',
    params: { scheduleId }
  })
}

// 新增安排
export function addSchedule(data) {
  return request({
    url: '/schedule/add',
    method: 'post',
    data
  })
}

// 更新安排
export function updateSchedule(data) {
  return request({
    url: '/schedule/update',
    method: 'post',
    data
  })
}

// 删除安排
export function deleteSchedule(scheduleId) {
  return request({
    url: '/schedule/delete',
    method: 'post',
    params: { scheduleId }
  })
}

// 根据分组信息生成课表
export function generateScheduleFromGroups(data) {
  return request({
    url: '/schedule/generateScheduleFromGroups',
    method: 'post',
    data
  })
}

// 保存教师分配
export function saveTeacherAssignment(data) {
  return request({
    url: '/schedule/saveTeacherAssignment',
    method: 'post',
    data
  })
}

// 获取教师课表
export function getTeacherSchedule(params) {
  return request({
    url: '/schedule/teacherSchedule',
    method: 'get',
    params
  })
}

// 获取学生课表
export function getStudentSchedule(params) {
  return request({
    url: '/schedule/studentSchedule',
    method: 'get',
    params
  })
}

// 按时间段更新教师（新增或更新）
export function updateTeachersByTime(params) {
  return request({
    url: '/schedule/updateTeachersByTime',
    method: 'post',
    params
  })
}

// 按时间段更新小组（新增或合并）
export function updateGroupsByTime(params) {
  return request({
    url: '/schedule/updateGroupsByTime',
    method: 'post',
    params
  })
}

// 获取课表显示数据 - 返回所有实验套的数据
export function getScheduleForDisplay(params) {
  return request({
    url: '/schedule/scheduleForDisplay',
    method: 'get',
    params
  })
}

// 导出课表Excel（xlsx）
export function exportScheduleXlsx(params) {
  return request({
    url: '/schedule/export-xlsx',
    method: 'get',
    params,
    responseType: 'blob'
  })
}

// 学生上课反查：按学号查询上课学期/时间段/老师
export function getStudentTrace(params) {
  return request({
    url: '/schedule/student-trace',
    method: 'get',
    params
  })
}