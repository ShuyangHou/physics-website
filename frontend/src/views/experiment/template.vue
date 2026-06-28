<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">实验管理</h2>
      <p class="page-description">管理实验基础信息（后续将支持新建、编辑、删除）</p>
    </div>

    <!-- 搜索和操作 -->
    <div class="search-section">
      <el-row :gutter="20">
        <el-col :span="8">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索实验名称"
            clearable
            @input="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
        <el-col :span="8">
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-col>
        <el-col :span="8" style="text-align: right;">
          <el-button v-if="isAdmin" type="info" @click="testFileConfig" size="small">测试文件配置</el-button>
          <el-button v-if="isAdmin" type="success" @click="openCreateDialog">新建实验</el-button>
        </el-col>
      </el-row>
    </div>

    <!-- 实验列表 -->
    <div class="experiment-list">
      <el-table :data="experimentList" style="width: 100%" v-loading="loading">
        <el-table-column prop="experimentName" label="实验名称" />
        <el-table-column label="实验地点" width="140">
          <template #default="scope">{{ scope.row.location || '—' }}</template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">{{ formatDateTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="模板" width="200">
          <template #default="scope">
            <div class="template-actions">
              <!-- 如果有模板文件，显示文件状态和操作 -->
              <div v-if="scope.row.filePath" class="template-status">
                <el-tag type="success" size="small" class="template-tag">
                  <el-icon><Document /></el-icon>
                  已上传
                </el-tag>
                <div class="template-buttons">
                  <el-button
                    size="small"
                    type="primary"
                    @click="downloadTemplate(scope.row)"
                    class="action-btn"
                  >
                    <el-icon><Download /></el-icon>
                  </el-button>
                  <el-button
                    v-if="isAdmin"
                    size="small"
                    type="danger"
                    @click="removeTemplate(scope.row)"
                    class="action-btn"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
              <!-- 如果没有模板文件，显示上传按钮 -->
              <div v-else class="template-empty">
                <el-tag type="info" size="small" class="template-tag">
                  <el-icon><DocumentRemove /></el-icon>
                  未上传
                </el-tag>
                <el-button
                  v-if="isAdmin"
                  size="small"
                  type="success"
                  @click="openUpload(scope.row)"
                  class="upload-btn"
                >
                  <el-icon><Upload /></el-icon>
                  上传
                </el-button>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="isAdmin" label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="confirmDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 新建/编辑 实验对话框（基础字段） -->
    <el-dialog v-model="editVisible" :title="isEdit ? '编辑实验' : '新建实验'" width="560px">
      <el-form :model="form" ref="formRef" label-width="100px">
        <el-form-item label="实验名称">
          <el-input v-model="form.experimentName" placeholder="请输入名称" />
        </el-form-item>
        <el-form-item label="实验地点">
          <el-input v-model="form.location" placeholder="如 物理楼101" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" @click="saveExperiment" :loading="saving">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 上传模板对话框 -->
    <el-dialog v-model="uploadVisible" title="上传实验模板" width="520px">
      <div v-if="currentRow">
        <p style="margin-bottom:8px">实验：{{ currentRow.experimentName }}</p>
        <p style="margin:0 0 12px 0; color:#606266;">地点：{{ currentRow.location || '—' }}</p>
        <el-upload
          :auto-upload="false"
          :on-change="onFileSelect"
          :file-list="uploadList"
          :limit="1"
          accept=".pdf"
        >
          <el-button type="primary">选择文件</el-button>
          <template #tip>
            <div class="el-upload__tip">支持 PDF/Word/Excel/PPT，最大10MB</div>
          </template>
        </el-upload>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="uploadVisible = false">取消</el-button>
          <el-button type="primary" :loading="uploading" @click="submitUpload">上传</el-button>
        </span>
      </template>
    </el-dialog>
    
    <!-- 额外资料文件管理 -->
    <ExtraFilesManager :is-admin="isAdmin" />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Document, Download, Delete, DocumentRemove, Upload } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { getExperimentList, addExperiment, updateExperiment, deleteExperiment } from '@/api/experiment'
import { uploadFile } from '@/api/file'
import { formatDateTime } from '@/utils/date'
import ExtraFilesManager from '@/components/ExtraFilesManager.vue'

// 用户信息
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isAdmin = computed(() => {
  const userType = userInfo.value.userType
  return userType === 'admin'
})

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const editVisible = ref(false)
const isEdit = ref(false)
const experimentList = ref([])
const formRef = ref()
const uploadVisible = ref(false)
const currentRow = ref(null)
const uploadList = ref([])
const uploading = ref(false)

// 分页数据
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 搜索表单
const searchForm = reactive({
  keyword: ''
})

// 表单
const form = reactive({ experimentId: null, experimentName: '', location: '' })

// 加载实验列表
const loadExperimentList = async () => {
  try {
    loading.value = true
    const params = {
      current: pagination.current,
      size: pagination.size,
      keyword: searchForm.keyword
    }
    const response = await getExperimentList(params)
    experimentList.value = response.data.records
    pagination.total = response.data.total
  } catch (error) {
    ElMessage.error('加载实验列表失败')
    console.error('加载实验列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索
const handleSearch = () => {
  pagination.current = 1
  loadExperimentList()
}

// 重置搜索
const resetSearch = () => {
  searchForm.keyword = ''
  pagination.current = 1
  loadExperimentList()
}

const openCreateDialog = () => { isEdit.value = false; Object.assign(form, { experimentId:null, experimentName:'', location:'' }); editVisible.value = true }
const openEditDialog = (row) => { isEdit.value = true; Object.assign(form, { experimentId: row.experimentId, experimentName: row.experimentName, location: row.location || '' }); editVisible.value = true }
const confirmDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确定删除实验“${row.experimentName}”？`, '删除确认', { type: 'warning' })
    await deleteExperiment(row.experimentId)
    ElMessage.success('删除成功')
    loadExperimentList()
  } catch {}
}

const saveExperiment = async () => {
  try {
    saving.value = true
    if (isEdit.value) {
      await updateExperiment(form.experimentId, { experimentName: form.experimentName, location: form.location })
    } else {
      await addExperiment({ experimentName: form.experimentName, location: form.location })
    }
    ElMessage.success('保存成功')
    editVisible.value = false
    loadExperimentList()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally { saving.value = false }
}

// 模板上传/下载/删除逻辑
const openUpload = (row) => { currentRow.value = row; uploadList.value = []; uploadVisible.value = true }
const onFileSelect = (file) => {
  // 大小校验 10MB
  const max = 10 * 1024 * 1024
  if (file.size > max) {
    ElMessage.error('文件大小不能超过10MB')
    uploadList.value = []
    return false
  }
  const name = (file.name || '').toLowerCase()
  if (!name.endsWith('.pdf')) {
    ElMessage.error('仅支持上传 PDF 文件')
    uploadList.value = []
    return false
  }
  uploadList.value = [file]
  return true
}
const submitUpload = async () => {
  if (!currentRow.value || uploadList.value.length === 0) { ElMessage.warning('请先选择文件'); return }
  try {
    uploading.value = true
    const formData = new FormData()
    formData.append('file', uploadList.value[0].raw)
    formData.append('experimentId', currentRow.value.experimentId)
    const resp = await uploadFile(formData)
    let filePath = resp?.data?.filePath || resp?.data?.path || ''
    // 确保文件路径前加上/files前缀
    if (filePath && !filePath.startsWith('/files')) {
      // 如果路径以/开头，先去掉
      if (filePath.startsWith('/')) {
        filePath = filePath.substring(1)
      }
      filePath = `/files/${filePath}`
    }
    const updateResp = await updateExperiment(currentRow.value.experimentId, { filePath })
    if (updateResp?.code === 200) {
      ElMessage.success('上传成功')
      uploadVisible.value = false
      loadExperimentList()
    } else {
      ElMessage.error(updateResp?.message || '更新数据库失败')
    }
  } catch (e) {
    console.error('上传文件失败:', e)
    ElMessage.error(e?.response?.data?.message || e?.message || '上传失败，请检查网络连接')
  } finally { uploading.value = false }
}
const downloadTemplate = async (row) => {
  if (!row.filePath) { 
    ElMessage.warning('暂无模板文件')
    return 
  }
  
  try {
    // 构建下载URL（重要：不要拼接 /api 基础路径，/files 由后端静态资源直接映射）
    const origin = window.location.origin
    let path = row.filePath || ''
    // 规范化为 /files 开头
    if (path && !path.startsWith('/files')) {
      if (path.startsWith('/')) path = path.substring(1)
      path = `/files/${path}`
    }
    if (!path.startsWith('/')) path = `/${path}`
    const url = `${origin}${path}`
    
    // 先检查文件是否存在
    const response = await fetch(url, { method: 'HEAD' })
    if (!response.ok) {
      if (response.status === 404) {
        ElMessage.error('文件不存在或已被删除')
      } else if (response.status === 403) {
        ElMessage.error('没有权限访问该文件')
      } else {
        ElMessage.error(`文件访问失败 (${response.status})`)
      }
      return
    }
    
    // 创建下载链接
    const link = document.createElement('a')
    link.href = url
    link.download = row.experimentName + '_模板'
    link.target = '_blank'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    
    ElMessage.success('开始下载模板文件')
    
  } catch (error) {
    console.error('下载失败:', error)
    ElMessage.error('下载失败，请检查网络连接或联系管理员')
  }
}
const removeTemplate = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该实验的模板文件？', '删除确认', { type: 'warning' })
    const response = await updateExperiment(row.experimentId, { filePath: '' })
    if (response?.code === 200) {
      ElMessage.success('已删除模板')
      loadExperimentList()
    } else {
      ElMessage.error(response?.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除模板失败:', error)
      ElMessage.error('删除失败，请重试')
    }
  }
}

// 测试文件配置
const testFileConfig = async () => {
  try {
    const response = await request.get('/file-test/config')
    
    const config = response.data
    let message = `文件上传配置信息：\n`
    message += `上传目录: ${config.uploadRoot}\n`
    message += `绝对路径: ${config.absolutePath}\n`
    message += `目录存在: ${config.directoryExists ? '是' : '否'}\n`
    message += `可读: ${config.canRead ? '是' : '否'}\n`
    message += `可写: ${config.canWrite ? '是' : '否'}\n`
    message += `文件数量: ${config.fileCount || 0}\n`
    
    if (config.sampleFiles && config.sampleFiles.length > 0) {
      message += `示例文件: ${config.sampleFiles.join(', ')}`
    }
    
    await ElMessageBox.alert(message, '文件配置信息', {
      confirmButtonText: '确定',
      type: 'info'
    })
  } catch (error) {
    console.error('获取文件配置失败:', error)
    ElMessage.error('获取文件配置失败')
  }
}


// 编辑
const handleEdit = (row) => {
  editForm.experimentId = row.experimentId
  editForm.experimentName = row.experimentName
  editForm.filePath = row.filePath || ''
  editForm.file = null
  fileList.value = []
  showEditDialog.value = true
}

// 处理文件选择
const handleFileChange = (file) => {
  editForm.file = file.raw
  fileList.value = [file]
  
  // 验证文件大小（10MB）
  const maxSize = 10 * 1024 * 1024
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    fileList.value = []
    editForm.file = null
    return
  }
  
  // 验证文件类型
  const allowedTypes = ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx']
  const fileName = file.name.toLowerCase()
  const isValidType = allowedTypes.some(type => fileName.endsWith(type))
  
  if (!isValidType) {
    ElMessage.error('不支持的文件格式，请选择PDF、Word、Excel或PowerPoint文件')
    fileList.value = []
    editForm.file = null
    return
  }
}

// 获取文件名
const getFileName = (filePath) => {
  if (!filePath) return '未选择文件'
  const lastSlash = filePath.lastIndexOf('/')
  if (lastSlash === -1) return filePath
  return filePath.substring(lastSlash + 1)
}

// 移除当前文件
const removeCurrentFile = () => {
  editForm.filePath = ''
  editForm.file = null
  fileList.value = []
  ElMessage.info('已移除当前文件')
}

// 保存
const handleSave = async () => {
  try {
    await editFormRef.value.validate()
    saving.value = true
    
    let filePath = editForm.filePath
    
    // 如果有新文件，先上传文件
    if (editForm.file) {
      const formData = new FormData()
      formData.append('file', editForm.file)
      formData.append('experimentId', editForm.experimentId)
      
      const uploadResponse = await uploadFile(formData)
      if (uploadResponse.code === 200) {
        filePath = uploadResponse.data.filePath
        // 确保文件路径前加上/files前缀
        if (filePath && !filePath.startsWith('/files')) {
          // 如果路径以/开头，先去掉
          if (filePath.startsWith('/')) {
            filePath = filePath.substring(1)
          }
          filePath = `/files/${filePath}`
        }
        ElMessage.success('文件上传成功')
      } else {
        ElMessage.error(uploadResponse.message || '文件上传失败')
        return
      }
    }
    
    // 更新实验的文件路径
    const response = await updateExperiment(editForm.experimentId, {
      filePath: filePath
    })
    
    if (response.code === 200) {
      ElMessage.success('保存成功')
      showEditDialog.value = false
      loadExperimentList()
    } else {
      ElMessage.error(response.message || '保存失败')
    }
  } catch (error) {
    ElMessage.error('保存失败')
    console.error('保存失败:', error)
  } finally {
    saving.value = false
  }
}

// 分页大小改变
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadExperimentList()
}

// 当前页改变
const handleCurrentChange = (current) => {
  pagination.current = current
  loadExperimentList()
}

// 使用全局格式化
// imakerlab完成

onMounted(() => {
  loadExperimentList()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 8px 0;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}

.page-description {
  margin: 0;
  color: #667085;
  font-size: 15px;
}

.search-section {
  margin-bottom: 20px;
  padding: 20px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.experiment-list {
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.pagination {
  padding: 20px;
  text-align: right;
}

.dialog-footer {
  text-align: right;
}


.current-file {
  display: flex;
  align-items: center;
  color: #606266;
  font-size: 14px;
}

.current-file .el-icon {
  margin-right: 5px;
}

/* 模板操作样式优化 */
.template-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.template-status,
.template-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.template-tag {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
  min-width: 60px;
}

.template-buttons {
  display: flex;
  gap: 4px;
}

.action-btn {
  width: 32px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.upload-btn {
  font-size: 14px;
  height: 34px;
  padding: 0 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 移动端响应式样式 */
@media (max-width: 768px) {
  .page-container {
    padding: 10px;
  }
  
  .search-section {
    padding: 15px;
  }
  
  .search-section .el-row {
    flex-direction: column;
  }
  
  .search-section .el-col {
    margin-bottom: 10px;
  }
  
  .search-section .el-col:last-child {
    margin-bottom: 0;
    text-align: left;
  }
  
  .experiment-list {
    overflow-x: auto;
  }
  
  .experiment-list .el-table {
    min-width: 600px;
  }
  
  .pagination {
    padding: 15px;
    text-align: center;
  }
  
  .page-title {
    font-size: 20px;
  }
  
  .page-description {
    font-size: 14px;
  }
}
</style>
