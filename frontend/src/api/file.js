import request from '@/utils/request'

// 上传文件
export function uploadFile(data) {
  return request({
    url: '/file/upload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 下载文件
export function downloadFile(filePath) {
  return request({
    url: '/file/download',
    method: 'get',
    params: { filePath },
    responseType: 'blob'
  })
}

// 删除文件
export function deleteFile(filePath) {
  return request({
    url: '/file/delete',
    method: 'post',
    params: { filePath }
  })
}

// 额外资料相关API
// 获取额外资料文件列表
export function getExtraFileList() {
  return request({
    url: '/file/extra/list',
    method: 'get'
  })
}

// 上传额外资料文件
export function uploadExtraFiles(data) {
  return request({
    url: '/file/extra/upload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 下载单个额外资料文件
export function downloadExtraFile(fileId) {
  return request({
    url: `/file/extra/download/${fileId}`,
    method: 'get',
    responseType: 'blob'
  })
}

// 删除单个额外资料文件
export function deleteExtraFile(fileId) {
  return request({
    url: `/file/extra/delete/${fileId}`,
    method: 'delete'
  })
}

// 批量删除额外资料文件
export function batchDeleteExtraFiles(fileIds) {
  return request({
    url: '/file/extra/batch-delete',
    method: 'post',
    data: { fileIds }
  })
}

// 下载额外资料压缩包
export function downloadExtraZip() {
  return request({
    url: '/file/extra/download-zip',
    method: 'get',
    responseType: 'blob'
  })
}