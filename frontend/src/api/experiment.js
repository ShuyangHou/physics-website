import request from '@/utils/request'

// 获取实验列表
export function getExperimentList(params) {
  return request({
    url: '/experiment/getExperimentList',
    method: 'get',
    params
  })
}

// 按套件获取实验列表
export function getExperimentsBySuite(suiteId) {
  return request({
    url: '/experiment/getExperimentsBySuite',
    method: 'get',
    params: { suiteId }
  })
}

// 获取实验详情
export function getExperimentDetail(experimentId) {
  return request({
    url: '/experiment/getExperimentDetail',
    method: 'get',
    params: { experimentId }
  })
}

// 新增实验
export function addExperiment(data) {
  return request({
    url: '/experiment/addExperiment',
    method: 'post',
    data
  })
}

// 更新实验
export function updateExperiment(experimentId, data) {
  return request({
    url: `/experiment/updateExperiment`,
    method: 'post',
    data: {
      ...data,
      experimentId
    }
  })
}

// 删除实验
export function deleteExperiment(experimentId) {
  return request({
    url: '/experiment/deleteExperiment',
    method: 'post',
    params: { experimentId }
  })
}