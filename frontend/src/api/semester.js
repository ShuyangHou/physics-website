import request from '@/utils/request'

// 获取学期列表
export function getSemesterList(params) {
  return request({
    url: '/semester/list',
    method: 'get',
    params
  })
}

// 获取当前学期
export function getCurrentSemester() {
  return request({
    url: '/semester/current',
    method: 'get'
  })
}

// 获取周次信息（基于学期startDate计算当前第几周、单双周）
export function getWeekInfo(params) {
  return request({
    url: '/semester/week-info',
    method: 'get',
    params
  })
}

// 新增学期
export function addSemester(data) {
  return request({
    url: '/semester/add',
    method: 'post',
    data
  })
}

// 更新学期
export function updateSemester(data) {
  return request({
    url: '/semester/update',
    method: 'post',
    data
  })
}

// 删除学期
export function deleteSemester(semesterId) {
  return request({
    url: '/semester/delete',
    method: 'post',
    params: { semesterId }
  })
}