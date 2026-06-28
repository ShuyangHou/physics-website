import { createRouter, createWebHistory } from 'vue-router'
import { getUserInfo, hasPermission } from '@/utils/permission'
import Layout from '@/layout/index.vue'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'House', roles: ['admin', 'teacher', 'student'] }
      },
      {
        path: 'experiment/schedule',
        name: 'ExperimentSchedule',
        component: () => import('@/views/experiment/schedule.vue'),
        meta: { title: '实验课表', icon: 'Calendar', roles: ['admin', 'teacher'] }
      },
      {
        path: 'experiment/teacher-schedule',
        name: 'TeacherSchedule',
        component: () => import('@/views/experiment/teacher-schedule.vue'),
        meta: { title: '教师课表', icon: 'Calendar', roles: ['teacher'] }
      },
      {
        path: 'experiment/student-schedule',
        name: 'StudentSchedule',
        component: () => import('@/views/experiment/student-schedule.vue'),
        meta: { title: '学生课表', icon: 'Calendar', roles: ['student'] }
      },
      {
        path: 'experiment/student-trace',
        name: 'StudentTrace',
        component: () => import('@/views/experiment/student-trace.vue'),
        meta: { title: '学生上课查询', icon: 'Search', roles: ['admin', 'teacher'] }
      },
      {
        path: 'experiment/teacher-work',
        name: 'TeacherWork',
        component: () => import('@/views/experiment/teacher-work.vue'),
        meta: { title: '教师工作', icon: 'Clock', roles: ['admin'], hidden: true }
      },
      {
        path: 'experiment/grade-entry',
        name: 'GradeEntry',
        component: () => import('@/views/experiment/grade-entry.vue'),
        meta: { title: '成绩录入', icon: 'Edit', roles: ['admin', 'teacher'] }
      },
      {
        path: 'system/semester',
        name: 'SemesterManagement',
        component: () => import('@/views/system/semester.vue'),
        meta: { title: '学期管理', icon: 'Calendar', roles: ['admin'] }
      },
      {
        path: 'user/import',
        name: 'UserImport',
        component: () => import('@/views/user/import.vue'),
        meta: { title: '导入新生', icon: 'Upload', roles: ['admin'] }
      },
      {
        path: 'experiment/template',
        name: 'ExperimentManage',
        component: () => import('@/views/experiment/template.vue'),
        meta: { title: '实验管理', icon: 'Document', roles: ['admin', 'teacher', 'student'] }
      },
      {
        path: 'experiment/suite',
        name: 'ExperimentSuite',
        component: () => import('@/views/experiment/suite.vue'),
        meta: { title: '实验套管理', icon: 'Collection', roles: ['admin'] }
      },
      {
        path: 'experiment/grade',
        name: 'ExperimentGrade',
        component: () => import('@/views/experiment/grade.vue'),
        meta: { title: '成绩查看', icon: 'Trophy', roles: ['admin', 'teacher', 'student'] }
      },
      {
        path: 'user/teacher',
        name: 'TeacherManagement',
        component: () => import('@/views/user/teacher.vue'),
        meta: { title: '教师管理', icon: 'UserFilled', roles: ['admin'] }
      },
      {
        path: 'user/student',
        name: 'StudentManagement',
        component: () => import('@/views/user/student.vue'),
        meta: { title: '学生管理', icon: 'Avatar', roles: ['admin', 'teacher'] }
      },
      {
        path: 'profile/index',
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心', icon: 'User', roles: ['admin', 'teacher', 'student'] }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userInfo = getUserInfo()
  
  if (to.path === '/login') {
    if (userInfo.userId) {
      next('/')
    } else {
      next()
    }
  } else {
    if (userInfo.userId) {
      if (hasPermission(to)) {
        next()
      } else {
        next('/dashboard')
      }
    } else {
      next('/login')
    }
  }
})

export default router 