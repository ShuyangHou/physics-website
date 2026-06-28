<template>
  <div class="user-import">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>导入新生</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
          </div>
        </div>
      </template>

      <!-- 导入区域 -->
      <div class="import-section">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :on-change="handleFileChange"
          :before-upload="beforeUpload"
          accept=".xlsx,.xls"
          :limit="1"
          drag
        >
          <el-icon class="el-icon--upload"><upload-filled /></el-icon>
          <div class="el-upload__text">
            将文件拖到此处，或<em>点击上传</em>
          </div>
          <template #tip>
            <div class="el-upload__tip">
              <p>只能上传xlsx/xls文件，且不超过10MB</p>
              <p>Excel格式要求：第一行为标题，从第二行开始为数据</p>
              <p>列顺序：A列-学号，B列-姓名，C列-班号</p>
            </div>
          </template>
        </el-upload>
      </div>

      <!-- 示例图片 -->
      <div class="sample-image">
        <el-divider content-position="left">导入文件示范</el-divider>
        <img src="@/assets/导入文件示范.png" alt="导入文件示范" />
        <div class="sample-tip">请参考示例格式准备Excel文件</div>
      </div>

      <!-- 文件信息 -->
      <div v-if="selectedFile" class="file-info">
        <el-alert
          :title="`已选择文件：${selectedFile.name}`"
          type="info"
          :closable="false"
          show-icon
        />
      </div>

      <!-- 操作按钮 -->
      <div class="action-section">
                 <el-button 
           type="primary" 
           @click="handleImport" 
           :loading="importing"
           :disabled="!selectedFile"
         >
           开始导入
         </el-button>
         <el-button @click="handleClear">清空</el-button>
      </div>

      <!-- 导入进度 -->
      <div v-if="importing" class="progress-section">
        <el-progress 
          :percentage="importProgress" 
          :format="progressFormat"
          status="success"
        />
        <p class="progress-text">{{ progressText }}</p>
      </div>
    </el-card>

              <!-- 导入结果弹窗 -->
             <el-dialog
         v-model="resultVisible"
         title="导入结果"
         width="600px"
         :close-on-click-modal="false"
         @close="handleResultDialogClose"
       >
                           <div class="result-content">
         
         <el-result
           :icon="importResult.success ? 'success' : 'error'"
           :title="importResult.message || (importResult.success ? '导入成功' : '导入失败')"
           :sub-title="`总计：${importResult.totalCount || 0}条，成功：${importResult.successCount || 0}条，失败：${importResult.failCount || 0}条`"
         >
                       <template #extra>
              <div v-if="importResult.errorMessages && importResult.errorMessages.length > 0">
                <h4 style="margin-bottom: 15px; color: #f56c6c;">
                  <el-icon><Warning /></el-icon>
                  错误详情（共 {{ importResult.errorMessages.length }} 条）：
                </h4>
                <div class="error-list">
                  <div 
                    v-for="(error, index) in importResult.errorMessages" 
                    :key="index"
                    class="error-item"
                  >
                    <span class="error-number">{{ index + 1 }}.</span>
                    <span class="error-text">{{ error }}</span>
                  </div>
                </div>
              </div>
              <div v-else-if="!importResult.success && importResult.message">
                <el-alert
                  :title="importResult.message"
                  type="error"
                  :closable="false"
                  show-icon
                />
              </div>
            </template>
         </el-result>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="resultVisible = false">关闭</el-button>
          <el-button 
            v-if="importResult.success" 
            type="primary" 
            @click="goToStudentList"
          >
            查看学生列表
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { UploadFilled, Refresh, Warning } from '@element-plus/icons-vue'
import { importStudents } from '@/api/user'

const router = useRouter()
const uploadRef = ref()
const importing = ref(false)
const selectedFile = ref(null)
const resultVisible = ref(false)
const importResult = ref({})
const importProgress = ref(0)
const progressText = ref('')

// 导入成功后的处理逻辑

// 文件改变
const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

// 上传前验证
const beforeUpload = (file) => {
  const isExcel = file.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' || 
                  file.type === 'application/vnd.ms-excel'
  const isLt10M = file.size / 1024 / 1024 < 10

  if (!isExcel) {
    ElMessage.error('只能上传Excel文件!')
    return false
  }
  if (!isLt10M) {
    ElMessage.error('文件大小不能超过10MB!')
    return false
  }
  return false // 阻止自动上传
}

// 导入数据
const handleImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }

  importing.value = true
  importProgress.value = 0
  progressText.value = '正在解析Excel文件...'

  try {
    // 创建FormData
    const formData = new FormData()
    formData.append('file', selectedFile.value)

    // 模拟进度
    const progressTimer = setInterval(() => {
      if (importProgress.value < 90) {
        importProgress.value += 10
        if (importProgress.value < 30) {
          progressText.value = '正在解析Excel文件...'
        } else if (importProgress.value < 60) {
          progressText.value = '正在验证数据...'
        } else if (importProgress.value < 90) {
          progressText.value = '正在导入数据...'
        }
      }
    }, 200)

    // 调用导入接口
    const response = await importStudents(formData)
    
    clearInterval(progressTimer)
    importProgress.value = 100
    progressText.value = '导入完成'

    if (response.code === 200) {
      importResult.value = response.data
      resultVisible.value = true
      
      if (importResult.value.success) {
        ElMessage.success('导入成功！')
        
        // 导入成功后，设置刷新标志，通知其他页面刷新数据
        try {
          localStorage.setItem('refreshStudentList', 'true')
          localStorage.setItem('importResult', JSON.stringify(importResult.value))
          window.dispatchEvent(new CustomEvent('student-import-success'))
        } catch (error) {
          console.error('设置localStorage失败:', error)
          // 即使localStorage失败，也不影响导入功能
        }
        
        // 不要立即清空文件，让用户看到结果后再决定
        // handleClear() 移到这里不合适
      } else {
        // 导入失败时，显示具体的错误信息
        if (importResult.value.errorMessages && importResult.value.errorMessages.length > 0) {
          ElMessage.error(`导入失败：${importResult.value.errorMessages[0]}`)
        } else {
          ElMessage.error('导入失败，请查看错误详情')
        }
      }
    } else {
      ElMessage.error(response.message || '导入失败')
    }
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error('导入失败：' + error.message)
  } finally {
    importing.value = false
    // 延迟重置进度
    setTimeout(() => {
      importProgress.value = 0
      progressText.value = ''
    }, 1000)
  }
}

// 进度条格式化
const progressFormat = (percentage) => {
  return `${percentage}%`
}

// 清空数据
const handleClear = () => {
  selectedFile.value = null
  uploadRef.value.clearFiles()
  importProgress.value = 0
  progressText.value = ''
}

// 刷新
const handleRefresh = () => {
  handleClear()
  ElMessage.success('刷新完成')
}

// 跳转到学生列表
const goToStudentList = () => {
  resultVisible.value = false
  router.push('/user/student')
}

// 处理结果弹窗关闭
const handleResultDialogClose = () => {
  // 弹窗关闭后，清空文件选择，准备下次导入
  handleClear()
}


</script>

<style scoped>
.user-import {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.import-section {
  margin-bottom: 30px;
}

.file-info {
  margin-bottom: 20px;
}

.action-section {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-bottom: 20px;
}

.progress-section {
  margin-top: 20px;
  text-align: center;
}

.progress-text {
  margin-top: 10px;
  color: #606266;
  font-size: 14px;
}

.result-content {
  padding: 20px 0;
}

.sample-image {
  margin: 20px 0;
  text-align: center;
}
.sample-image img {
  max-width: 100%;
  height: auto;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
.sample-tip {
  margin-top: 8px;
  color: #667085;
  font-size: 15px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.error-list {
  max-height: 300px;
  overflow-y: auto;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  padding: 10px;
  background-color: #fef0f0;
}

.error-list .el-alert {
  margin-bottom: 8px;
}

.error-list .el-alert:last-child {
  margin-bottom: 0;
}

.error-item {
  display: flex;
  align-items: flex-start;
  padding: 8px 12px;
  margin-bottom: 8px;
  background-color: #fff;
  border: 1px solid #fde2e2;
  border-radius: 4px;
  color: #f56c6c;
}

.error-item:last-child {
  margin-bottom: 0;
}

.error-number {
  font-weight: bold;
  margin-right: 8px;
  min-width: 20px;
}

.error-text {
  flex: 1;
  line-height: 1.5;
}
</style> 
