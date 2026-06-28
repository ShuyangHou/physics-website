import request from '@/utils/request'

// 获取实验套列表（分页）
export function getSuiteList(params) {
  return request({
    url: '/experiment-suite/list',
    method: 'get',
    params
  })
}

// 获取所有实验套列表
export function getAllSuites() {
  return request({
    url: '/experiment-suite/all',
    method: 'get'
  })
}

// 获取实验套详情
export function getSuiteDetail(suiteId) {
  return request({
    url: `/experiment-suite/detail/${suiteId}`,
    method: 'get'
  })
}

// 根据实验套ID获取实验列表
export function getExperimentIdsBySuiteId(suiteId) {
  return request({
    url: `/experiment-suite/experiments/${suiteId}`,
    method: 'get'
  })
}

// 新增实验套
export function addSuite(data) {
  return request({
    url: '/experiment-suite/add',
    method: 'post',
    data
  })
}

// 更新实验套
export function updateSuite(data) {
  return request({
    url: '/experiment-suite/update',
    method: 'post',
    data
  })
}

// 删除实验套
export function deleteSuite(suiteId) {
  return request({
    url: `/experiment-suite/delete/${suiteId}`,
    method: 'post'
  })
}
