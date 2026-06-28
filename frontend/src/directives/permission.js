import { hasAnyRole, isAdmin, isTeacher, isStudent } from '@/utils/permission'

// 权限指令
export const permission = {
  mounted(el, binding) {
    const { value } = binding
    
    if (value) {
      let hasPermission = false
      
      if (typeof value === 'string') {
        // 单个权限检查
        hasPermission = hasAnyRole([value])
      } else if (Array.isArray(value)) {
        // 多个权限检查
        hasPermission = hasAnyRole(value)
      } else if (typeof value === 'object') {
        // 对象形式的权限检查
        if (value.roles) {
          hasPermission = hasAnyRole(value.roles)
        }
      }
      
      if (!hasPermission) {
        el.parentNode?.removeChild(el)
      }
    }
  }
}

// 管理员权限指令
export const admin = {
  mounted(el) {
    if (!isAdmin()) {
      el.parentNode?.removeChild(el)
    }
  }
}

// 教师权限指令
export const teacher = {
  mounted(el) {
    if (!isTeacher()) {
      el.parentNode?.removeChild(el)
    }
  }
}

// 学生权限指令
export const student = {
  mounted(el) {
    if (!isStudent()) {
      el.parentNode?.removeChild(el)
    }
  }
}
