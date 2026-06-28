import request from '@/utils/request'

// 根据小组名称获取实验安排
export function getGroupExperimentByGroup(groupName) {
  return request({
    url: '/group-experiment/by-group',
    method: 'get',
    params: { groupName }
  })
}

// 管理员批量设置某些小组在若干周次的实验与教师
export function batchAssignGroupExperiments(data) {
  return request({
    url: '/group-experiment/batch-assign-items',
    method: 'post',
    data
  })
}

// 统一入口：一个小组 + 多周次，逐周 items 明细（支持每周不同实验/教师）
export function batchAssignItems(data) {
  return request({
    url: '/group-experiment/batch-assign-items',
    method: 'post',
    data
  })
}

// 获取全部小组实验安排（用于统计）
export function getAllGroupExperiments(params) {
  return request({
    url: '/group-experiment/all',
    method: 'get',
    params
  })
}

// 获取指定时间段的小组实验数据（用于课表显示）
export function getGroupExperimentsByTime(params) {
  return request({
    url: '/group-experiment/by-time',
    method: 'get',
    params
  })
}

// 获取指定学期下所有实验套的单双周实验安排（用于学生课表显示）
export function getStudentSchedule(semesterId) {
  return request({
    url: '/group-experiment/student-schedule',
    method: 'get',
    params: { semesterId }
  })
}
