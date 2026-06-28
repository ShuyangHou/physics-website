import request from '@/utils/request'

/**
 * 获取所有小组列表
 * 通过学生列表接口获取小组信息
 */
export function getGroupList() {
  return request({
    url: '/user/studentList',
    method: 'get',
    params: {
      current: 1,
      size: 1 // 只需要获取小组列表，不需要实际的学生数据
    }
  }).then(response => {
    // 从响应中提取小组列表
    if (response.data && response.data.groupList) {
      return {
        code: response.code,
        message: response.message,
        data: response.data.groupList
      }
    }
    return {
      code: response.code,
      message: response.message,
      data: []
    }
  })
}

/**
 * 获取指定小组的学生列表
 */
export function getStudentsByGroup(groupName) {
  return request({
    url: '/user/studentList',
    method: 'get',
    params: {
      groupName: groupName,
      current: 1,
      size: 1000 // 获取该小组的所有学生
    }
  })
}
