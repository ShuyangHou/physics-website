import request from '@/utils/request'

/**
 * 自动分组并更新数据库
 * @param {Object} data - 请求参数
 * @param {string} data.experimentTime - 时间段，如 "周一上午"
 * @param {string} data.classIds - 班级ID列表，如 "240191,240192,240193,240194"
 * @param {number} data.semesterId - 学期ID
 * @param {number} data.suiteId - 实验套ID
 * @returns {Promise} 返回分组结果
 */
export function autoGroupAndUpdate(data) {
  return request({
    url: '/auto-grouping/auto-group',
    method: 'post',
    params: data
  })
}
