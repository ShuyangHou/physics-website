import request from '@/utils/request'

// 获取用户列表
export function getUserList(params) {
  return request({
    url: '/user/list',
    method: 'get',
    params
  })
}

// 获取学生列表
export function getStudentList(params) {
  return request({
    url: '/user/studentList',
    method: 'get',
    params
  })
}

// 获取教师列表
export function getTeacherList(params) {
  return request({
    url: '/user/teacherList',
    method: 'get',
    params
  })
}

// 新增用户
export function addUser(data) {
  return request({
    url: '/user/add',
    method: 'post',
    data
  })
}

// 更新用户
export function updateUser(data) {
  return request({
    url: '/user/update',
    method: 'post',
    data
  })
}

// 删除用户
export function deleteUser(userId) {
  return request({
    url: '/user/delete',
    method: 'post',
    params: { userId }
  })
}

// 重置密码
export function resetPassword(userId) {
  return request({
    url: '/user/resetPassword',
    method: 'post',
    params: { userId }
  })
}

// 导入学生
export function importStudents(data) {
  return request({
    url: '/user/importStudents',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    },
    timeout: 120000
  })
}

// 检查学号是否与其他学生重复（编辑场景可传excludeUserId）
export function checkDuplicateStudent(params) {
  return request({
    url: '/user/check-duplicate-student',
    method: 'get',
    params
  })
}

// 合并学生成绩：将fromUserId的成绩迁移到toUserId（管理员）
export function mergeStudentGrades(params) {
  return request({
    url: '/user/merge-student-grades',
    method: 'post',
    params
  })
}

// 批量将选中学生分到同一组
export function assignGroup(data) {
  return request({
    url: '/user/assignGroup',
    method: 'post',
    data
  })
}

// 逐个学生设置组别
export function assignGroupPairs(data) {
  return request({
    url: '/user/assignGroupPairs',
    method: 'post',
    data
  })
} 
