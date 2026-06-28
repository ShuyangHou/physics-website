<template>
  <div class="extra-files-manager">
    <div class="manager-header">
      <h3 class="manager-title">额外资料管理</h3>
      <div class="manager-actions">
        <el-button type="success" @click="downloadAllFiles" :loading="downloading">
          <el-icon><Download /></el-icon>
          下载资料包
        </el-button>
      </div>
    </div>

    <!-- 上传区域 -->
    <div v-if="isAdmin" class="upload-section">
      <el-upload
        ref="uploadRef"
        class="upload-dragger"
        :auto-upload="false"
        :on-change="handleFileChange"
        :file-list="uploadFileList"
        :multiple="true"
        :limit="10"
        :on-exceed="handleExceed"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
          将文件拖到此处，或<em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">
            支持多文件上传，单个文件不超过10MB，支持PDF、Word、Excel、PowerPoint文件
          </div>
        </template>
      </el-upload>
      
      <div class="upload-actions" v-if="uploadFileList.length > 0">
        <el-button type="primary" @click="submitUpload" :loading="uploading">
          <el-icon><Upload /></el-icon>
          上传文件
        </el-button>
        <el-button @click="clearUploadList">清空列表</el-button>
      </div>
    </div>

    <!-- 文件列表 -->
    <div class="files-section">
      <div class="files-header">
        <div class="files-title">
          <span>文件列表 ({{ fileList.length }})</span>
          <el-button v-if="isAdmin && fileList.length > 0" type="danger" size="small" @click="batchDelete">
            <el-icon><Delete /></el-icon>
            批量删除
          </el-button>
        </div>
        <div class="files-search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索文件名..."
            size="small"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
      </div>

      <div class="files-list" v-loading="loading">
        <el-empty v-if="filteredFileList.length === 0 && !loading" description="暂无文件" />
        
        <div v-else class="file-grid">
          <div
            v-for="file in filteredFileList"
            :key="file.id"
            class="file-item"
            :class="{ 'selected': selectedFiles.includes(file.id) }"
          >
            <div class="file-icon">
              <el-icon v-if="getFileType(file.filename) === 'pdf'" color="#f56c6c">
                <Document />
              </el-icon>
              <el-icon v-else-if="getFileType(file.filename) === 'word'" color="#409eff">
                <Document />
              </el-icon>
              <el-icon v-else-if="getFileType(file.filename) === 'excel'" color="#67c23a">
                <Grid />
              </el-icon>
              <el-icon v-else-if="getFileType(file.filename) === 'powerpoint'" color="#e6a23c">
                <Grid />
              </el-icon>
              <el-icon v-else color="#909399">
                <Document />
              </el-icon>
            </div>
            
            <div class="file-info">
              <div class="file-name" :title="file.filename">{{ file.filename }}</div>
              <div class="file-meta">
                <span class="file-size">{{ formatFileSize(file.size) }}</span>
              </div>
            </div>

            <div class="file-actions">
              <el-checkbox
                v-if="isAdmin"
                v-model="selectedFiles"
                :value="file.id"
                @change="handleSelectFile"
                class="file-checkbox"
              />
              <el-button
                type="primary"
                size="small"
                @click="downloadFile(file)"
                :loading="downloadingFiles.includes(file.id)"
              >
                <el-icon><Download /></el-icon>
                下载
              </el-button>
              <el-button
                v-if="isAdmin"
                type="danger"
                size="small"
                @click="deleteFile(file)"
              >
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Download, 
  Upload, 
  Delete, 
  Search, 
  Document, 
  Grid, 
  UploadFilled 
} from '@element-plus/icons-vue'
import { 
  getExtraFileList, 
  uploadExtraFiles, 
  downloadExtraFile, 
  deleteExtraFile, 
  batchDeleteExtraFiles, 
  downloadExtraZip 
} from '@/api/file'
import request from '@/utils/request'

// Props
const props = defineProps({
  isAdmin: {
    type: Boolean,
    default: false
  }
})

// 响应式数据
const loading = ref(false)
const uploading = ref(false)
const downloading = ref(false)
const fileList = ref([])
const uploadFileList = ref([])
const selectedFiles = ref([])
const searchKeyword = ref('')
const downloadingFiles = ref([])

// 计算属性
const filteredFileList = computed(() => {
  if (!searchKeyword.value) return fileList.value
  return fileList.value.filter(file => 
    file.filename.toLowerCase().includes(searchKeyword.value.toLowerCase())
  )
})

// 文件类型判断
const getFileType = (filename) => {
  const ext = filename.toLowerCase().split('.').pop()
  if (['pdf'].includes(ext)) return 'pdf'
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['xls', 'xlsx'].includes(ext)) return 'excel'
  if (['ppt', 'pptx'].includes(ext)) return 'powerpoint'
  return 'other'
}

// 格式化文件大小
const formatFileSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 格式化时间
const formatTime = (timeStr) => {
  const date = new Date(timeStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

// 加载文件列表
const loadFileList = async () => {
  try {
    loading.value = true
    const response = await getExtraFileList()
    
    if (response.code === 200) {
      // 转换后端数据格式为前端期望的格式
      fileList.value = (response.data || []).map(item => ({
        id: item.fileId,
        filename: item.fileName,
        size: item.fileSize,
        uploadTime: item.uploadTime,
        filePath: item.filePath,
        fileType: item.fileType,
        uploaderId: item.uploaderId
      }))
    } else {
      ElMessage.error(response.message || '加载文件列表失败')
    }
  } catch (error) {
    ElMessage.error('加载文件列表失败')
    console.error('Load file list error:', error)
  } finally {
    loading.value = false
  }
}

// 文件选择处理
const handleFileChange = (file, fileList) => {
  // 验证文件大小
  const maxSize = 10 * 1024 * 1024 // 10MB
  if (file.size > maxSize) {
    ElMessage.error(`文件 ${file.name} 大小超过10MB`)
    return false
  }
  
  // 验证文件类型
  const allowedTypes = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']
  const fileName = file.name.toLowerCase()
  const isValidType = allowedTypes.some(type => fileName.endsWith(type))
  
  if (!isValidType) {
    ElMessage.error(`文件 ${file.name} 格式不支持`)
    return false
  }
  
  uploadFileList.value = fileList
  return true
}

// 文件数量超出限制
const handleExceed = (files, uploadFileList) => {
  ElMessage.warning(`最多只能上传10个文件，当前选择了 ${files.length} 个文件，共选择了 ${uploadFileList.length} 个文件`)
}

// 清空上传列表
const clearUploadList = () => {
  uploadFileList.value = []
}

// 提交上传
const submitUpload = async () => {
  if (uploadFileList.value.length === 0) {
    ElMessage.warning('请先选择文件')
    return
  }
  
  try {
    uploading.value = true
    const formData = new FormData()
    
    // 添加所有文件
    uploadFileList.value.forEach((file, index) => {
      formData.append(`files`, file.raw)
    })
    
    const response = await uploadExtraFiles(formData)
    
    if (response.code === 200) {
      ElMessage.success(`成功上传 ${uploadFileList.value.length} 个文件`)
      uploadFileList.value = []
      loadFileList() // 重新加载文件列表
    } else {
      ElMessage.error(response.message || '上传失败')
    }
  } catch (error) {
    ElMessage.error('上传失败')
    console.error('Upload error:', error)
  } finally {
    uploading.value = false
  }
}

// 下载单个文件
const downloadFile = (file) => {
  if (!file) {
    ElMessage.warning('文件不存在')
    return
  }
  
  downloadingFiles.value.push(file.id)
  
  try {
    // 直接使用window.open打开下载链接，让浏览器处理下载
    const origin = window.location.origin
    let base = (request?.defaults?.baseURL || import.meta.env.VITE_API_BASE || '').trim()
    // 若 base 是相对路径（如 /api），补上 origin
    if (base.startsWith('/')) base = origin + base
    if (!base) base = origin // 兜底
    base = base.replace(/\/+$/, '')
    const url = `${base}/file/extra/download/${file.id}`
    
    // 使用window.open方式下载，与实验模板下载逻辑保持一致
    const newWindow = window.open(url, '_blank')
    
    if (!newWindow || newWindow.closed || typeof newWindow.closed == 'undefined') {
      // 弹窗可能被浏览器阻止了
      ElMessage.warning('下载窗口可能被浏览器阻止，请检查浏览器设置')
      // 提供备用下载方式
      const link = document.createElement('a')
      link.href = url
      link.download = file.filename
      link.style.display = 'none'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    } else {
      ElMessage.success('下载已开始')
    }
  } catch (error) {
    ElMessage.error('下载失败')
    console.error('Download error:', error)
  } finally {
    // 延迟移除，避免快速点击导致的问题
    setTimeout(() => {
      downloadingFiles.value = downloadingFiles.value.filter(id => id !== file.id)
    }, 1000)
  }
}

// 下载所有文件
const downloadAllFiles = () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('暂无文件可下载')
    return
  }
  
  downloading.value = true
  
  try {
    // 直接使用URL下载，让浏览器处理
    const origin = window.location.origin
    let base = (request?.defaults?.baseURL || import.meta.env.VITE_API_BASE || '').trim()
    // 若 base 是相对路径（如 /api），补上 origin
    if (base.startsWith('/')) base = origin + base
    if (!base) base = origin // 兜底
    base = base.replace(/\/+$/, '')
    const url = `${base}/file/extra/download-zip`
    
    // 使用window.open方式下载，与实验模板下载逻辑保持一致
    const newWindow = window.open(url, '_blank')
    
    if (!newWindow || newWindow.closed || typeof newWindow.closed == 'undefined') {
      // 弹窗可能被浏览器阻止了
      ElMessage.warning('下载窗口可能被浏览器阻止，请检查浏览器设置')
      // 提供备用下载方式
      const link = document.createElement('a')
      link.href = url
      link.download = '额外资料.zip'
      link.style.display = 'none'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
    } else {
      ElMessage.success('下载已开始')
    }
  } catch (error) {
    ElMessage.error('下载失败')
    console.error('Download all error:', error)
  } finally {
    // 延迟设置状态，避免快速点击导致的问题
    setTimeout(() => {
      downloading.value = false
    }, 1000)
  }
}

// 删除单个文件
const deleteFile = async (file) => {
  try {
    await ElMessageBox.confirm(
      `确定删除文件 "${file.filename}" 吗？`,
      '删除确认',
      { type: 'warning' }
    )
    
    const response = await deleteExtraFile(file.id)
    
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadFileList() // 重新加载文件列表
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
      console.error('Delete error:', error)
    }
  }
}

// 选择文件
const handleSelectFile = () => {
  // 选择逻辑已通过 v-model 处理
}

// 批量删除
const batchDelete = async () => {
  if (selectedFiles.value.length === 0) {
    ElMessage.warning('请先选择要删除的文件')
    return
  }
  
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedFiles.value.length} 个文件吗？`,
      '批量删除确认',
      { type: 'warning' }
    )
    
    const response = await batchDeleteExtraFiles(selectedFiles.value)
    
    if (response.code === 200) {
      ElMessage.success('批量删除成功')
      selectedFiles.value = []
      loadFileList() // 重新加载文件列表
    } else {
      ElMessage.error(response.message || '批量删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
      console.error('Batch delete error:', error)
    }
  }
}

// 搜索处理
const handleSearch = () => {
  // 搜索逻辑已通过计算属性处理
}

// 监听文件列表变化，清空选择
watch(fileList, () => {
  selectedFiles.value = []
})

// 组件挂载时加载文件列表
onMounted(() => {
  loadFileList()
})
</script>

<style scoped>
.extra-files-manager {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.manager-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #ebeef5;
}

.manager-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.upload-section {
  margin-bottom: 30px;
  padding: 20px;
  background: #f8f9fa;
  border-radius: 6px;
  border: 2px dashed #d9d9d9;
}

.upload-dragger {
  width: 100%;
}

.upload-actions {
  margin-top: 15px;
  text-align: center;
}

.files-section {
  margin-top: 20px;
}

.files-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.files-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 600;
  color: #303133;
}

.files-search {
  width: 250px;
}

.files-list {
  min-height: 200px;
}

.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 15px;
}

.file-item {
  display: flex;
  align-items: center;
  padding: 15px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  transition: all 0.3s;
  position: relative;
}

.file-item:hover {
  border-color: #409eff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.file-item.selected {
  border-color: #409eff;
  background: #f0f9ff;
}

.file-icon {
  font-size: 24px;
  margin-right: 12px;
  flex-shrink: 0;
}

.file-info {
  flex: 1;
  min-width: 0;
}

.file-name {
  font-weight: 500;
  color: #303133;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-meta {
  display: flex;
  gap: 10px;
  font-size: 14px;
  color: #667085;
}

.file-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.file-checkbox {
  margin-right: 8px;
}

.el-upload__tip {
  color: #667085;
  font-size: 14px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .file-grid {
    grid-template-columns: 1fr;
  }
  
  .files-header {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }
  
  .files-search {
    width: 100%;
  }
}
</style>
