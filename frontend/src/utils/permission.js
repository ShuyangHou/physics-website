// 获取用户信息
export function getUserInfo() {
  const userInfoStr = localStorage.getItem('userInfo')
  return userInfoStr ? JSON.parse(userInfoStr) : {}
}

function normalize(type) {
  return typeof type === 'string' ? type.toLowerCase() : type
}

// 获取用户类型
export function getUserType() {
  const userInfo = getUserInfo()
  return normalize(userInfo.userType)
}

// 检查是否有权限访问某个路由
export function hasPermission(route) {
  // 如果没有设置roles，则所有用户都可以访问
  if (!route.meta || !route.meta.roles) {
    return true
  }
  
  const userType = getUserType()
  // 检查用户类型是否在允许的角色列表中（统一小写比较）
  const allowed = (route.meta.roles || []).map(r => normalize(r))
  return allowed.includes(userType)
}

// 检查是否是管理员
export function isAdmin() {
  return getUserType() === 'admin'
}

// 检查是否是教师
export function isTeacher() {
  return getUserType() === 'teacher'
}

// 检查是否是学生
export function isStudent() {
  return getUserType() === 'student'
}

// 检查是否有指定权限
export function hasRole(role) {
  const userType = getUserType()
  return userType === normalize(role)
}

// 检查是否有多个权限中的任意一个
export function hasAnyRole(roles) {
  const userType = getUserType()
  const list = (roles || []).map(r => normalize(r))
  return list.includes(userType)
}

// 检查是否有所有权限
export function hasAllRoles(roles) {
  const userType = getUserType()
  const list = (roles || []).map(r => normalize(r))
  return list.includes(userType)
}
