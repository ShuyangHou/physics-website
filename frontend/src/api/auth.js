import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function logout() {
  return request({
    url: '/auth/logout',
    method: 'post'
  })
}

export function getUserInfo() {
  return request({
    url: '/auth/userInfo',
    method: 'get'
  })
}

let passwordStatusCache = null
let passwordStatusPending = null
const PASSWORD_STATUS_CACHE_TTL = 60 * 1000

export function changePassword(data) {
  passwordStatusCache = null
  passwordStatusPending = null
  return request({
    url: '/auth/changePassword',
    method: 'post',
    data
  })
}

export function checkPasswordStatus(options = {}) {
  const now = Date.now()
  if (!options.force && passwordStatusCache && now - passwordStatusCache.time < PASSWORD_STATUS_CACHE_TTL) {
    return Promise.resolve(passwordStatusCache.response)
  }
  if (!options.force && passwordStatusPending) {
    return passwordStatusPending
  }

  passwordStatusPending = request({
    url: '/auth/checkPasswordStatus',
    method: 'get'
  }).then(response => {
    passwordStatusCache = { response, time: Date.now() }
    return response
  }).finally(() => {
    passwordStatusPending = null
  })

  return passwordStatusPending
}
