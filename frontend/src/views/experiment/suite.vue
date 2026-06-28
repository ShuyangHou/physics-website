<template>
  <div class="experiment-suite">
    <el-card shadow="hover">
      <template #header>
        <div class="card-header">
          <span>实验套管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleRefresh">
              <el-icon><Refresh /></el-icon>
              刷新
            </el-button>
            <el-button type="success" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增实验套
            </el-button>
          </div>
        </div>
      </template>

      <!-- 实验套列表 -->
      <el-table :data="suiteList" style="width: 100%" v-loading="loading">
        <!-- 隐藏ID列 -->
        <el-table-column prop="suiteName" label="实验套名称" width="200" />
        <el-table-column label="实验数量" width="100">
          <template #default="scope">
            <el-tag type="info">{{ getExperimentCount(scope.row) }}个</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实验列表" min-width="300">
          <template #default="scope">
            <div class="experiment-list">
              <el-tag 
                v-for="(name, index) in scope.row.experimentNames" 
                :key="index"
                size="small"
                style="margin: 2px;"
              >
                {{ index + 1 }}. {{ name }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="180">
          <template #default="scope">
            {{ formatDateTime(scope.row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button size="small" type="info" @click="handleView(scope.row)">
                <el-icon><View /></el-icon>
                查看
              </el-button>
              <el-button size="small" type="primary" @click="handleEdit(scope.row)">
                <el-icon><Edit /></el-icon>
                编辑
              </el-button>
              <el-button size="small" type="danger" @click="handleDelete(scope.row)">
                <el-icon><Delete /></el-icon>
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
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
    </el-card>

    <!-- 新增/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="600px"
    >
      <el-form :model="suiteForm" :rules="suiteRules" ref="suiteFormRef" label-width="100px">
        <el-form-item label="实验套名称" prop="suiteName">
          <el-input v-model="suiteForm.suiteName" placeholder="请输入实验套名称" />
        </el-form-item>
        
        <el-form-item label="选择实验" prop="experimentIds">
          <div class="experiment-selection">
            <el-select 
              v-model="suiteForm.selectedExperiments" 
              multiple 
              :placeholder="`请选择实验（当前：${suiteForm.selectedExperiments.length}）`"
              style="width: 100%"
              :max-collapse-tags="5"
              @change="handleExperimentChange"
            >
              <el-option
                v-for="experiment in experimentList"
                :key="experiment.experimentId"
                :label="experiment.experimentName"
                :value="experiment.experimentId"
              />
            </el-select>
            <div class="experiment-tags" v-if="suiteForm.selectedExperiments.length > 0">
              <div class="tags-header">
                <span>已选择的实验（{{ suiteForm.selectedExperiments.length }}）</span>
                <el-button size="small" type="info" @click="toggleSortMode">
                  {{ sortMode ? '完成排序' : '调整顺序' }}
                </el-button>
              </div>
              <div class="tags-container" :class="{ 'sortable': sortMode }">
                <div
                  v-for="(experimentId, index) in suiteForm.selectedExperiments"
                  :key="experimentId"
                  class="experiment-tag-item"
                  :class="{ 'sortable': sortMode }"
                  :draggable="sortMode"
                  @dragstart="handleDragStart(index, $event)"
                  @dragover.prevent
                  @drop="handleDrop(index, $event)"
                  @dragenter.prevent
                >
                  <el-tag
                    :closable="!sortMode"
                    @close="handleRemoveExperiment(index)"
                    style="margin: 4px; cursor: default;"
                    :class="{ 'dragging': sortMode }"
                  >
                    <span class="tag-number">{{ index + 1 }}</span>
                    <span class="tag-name">{{ getExperimentName(experimentId) }}</span>
                    <el-icon v-if="sortMode" class="drag-handle"><Rank /></el-icon>
                  </el-tag>
                </div>
              </div>
              <div v-if="sortMode" class="sort-hint">
                <el-icon><InfoFilled /></el-icon>
                <span>拖拽标签调整实验顺序</span>
              </div>
            </div>
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 查看详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="实验套详情"
      width="800px"
    >
      <div v-if="currentSuite">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="实验套名称">{{ currentSuite.suiteName }}</el-descriptions-item>
          <el-descriptions-item label="实验数量">
            <el-tag type="info">
              {{ currentSuite.experimentNames ? currentSuite.experimentNames.length : 0 }} 个
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDate(currentSuite.createTime) }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{ formatDate(currentSuite.updateTime) }}</el-descriptions-item>
        </el-descriptions>
        
        <div style="margin-top: 20px;">
          <h4>包含的实验（{{ currentSuite.experimentNames ? currentSuite.experimentNames.length : 0 }}）：</h4>
          <el-table :data="currentSuite.experimentNames ? currentSuite.experimentNames.map((name, index) => ({ experimentName: name, index: index + 1 })) : []" style="width: 100%">
            <el-table-column prop="index" label="序号" width="80" />
            <el-table-column prop="experimentName" label="实验名称" />
          </el-table>
        </div>
      </div>
      
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="detailVisible = false">关闭</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Plus, Rank, InfoFilled, View, Edit, Delete } from '@element-plus/icons-vue'
import { 
  getSuiteList, 
  getSuiteDetail, 
  addSuite, 
  updateSuite, 
  deleteSuite 
} from '@/api/suite'
import { getExperimentList } from '@/api/experiment'
import { formatDateTime } from '@/utils/date'

const loading = ref(false)
const dialogVisible = ref(false)
const detailVisible = ref(false)
const isEdit = ref(false)
const suiteFormRef = ref()

// 用户信息
const userInfo = ref(JSON.parse(localStorage.getItem('userInfo') || '{}'))
const isAdmin = computed(() => userInfo.value.userType === 'admin')

// 分页信息
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 实验套列表
const suiteList = ref([])

// 实验列表
const experimentList = ref([])

// 当前选中的实验套
const currentSuite = ref(null)

// 表单数据
const suiteForm = reactive({
  suiteId: null,
  suiteName: '',
  selectedExperiments: []
})

// 表单验证规则
const suiteRules = {
  suiteName: [
    { required: true, message: '请输入实验套名称', trigger: 'blur' }
  ],
  selectedExperiments: [
    { required: true, message: '请选择至少一个实验', trigger: 'change' }
  ]
}

// 对话框标题
const dialogTitle = computed(() => {
  return isEdit.value ? '编辑实验套' : '新增实验套'
})

// 检查权限
const checkPermission = () => {
  if (!isAdmin.value) {
    ElMessage.error('只有管理员可以访问此页面')
    return false
  }
  return true
}

// 加载实验套列表
const loadSuiteList = async () => {
  if (!checkPermission()) return
  
  try {
    loading.value = true
    // 确保先有实验列表用于名称映射
    if (!experimentList.value || experimentList.value.length === 0) {
      await loadExperimentList()
    }
    const response = await getSuiteList({
      current: pagination.current,
      size: pagination.size
    })
    
    if (response.code === 200) {
      suiteList.value = response.data.records
      pagination.total = response.data.total
      
      // 为每个实验套并行加载详情并映射名称，避免逐个串行请求造成的卡顿
      await Promise.all(suiteList.value.map(suite => loadSuiteDetail(suite)))
    } else {
      ElMessage.error(response.message || '加载实验套列表失败')
    }
  } catch (error) {
    console.error('加载实验套列表失败:', error)
    ElMessage.error('加载实验套列表失败')
  } finally {
    loading.value = false
  }
}

// 加载实验套详情
const loadSuiteDetail = async (suite) => {
  try {
    const response = await getSuiteDetail(suite.suiteId)
    if (response.code === 200) {
      // 后端返回 experimentIds，前端根据实验列表映射名称
      suite.experimentIds = response.data.experimentIds || []
      suite.experimentNames = (suite.experimentIds || []).map(id => getExperimentName(id))
    }
  } catch (error) {
    console.error('加载实验套详情失败:', error)
  }
}

// 加载实验列表
const loadExperimentList = async () => {
  try {
    const response = await getExperimentList({ current: 1, size: 100 })
    if (response.code === 200) {
      experimentList.value = response.data.records
    }
  } catch (error) {
    console.error('加载实验列表失败:', error)
  }
}

// 获取实验数量
const getExperimentCount = (suite) => {
  return suite.experimentNames ? suite.experimentNames.length : 0
}

// 使用统一时间格式

// 实验ID到名称的映射，避免在列表渲染时反复线性查找
const experimentNameMap = computed(() => {
  const map = new Map()
  for (const e of experimentList.value) {
    map.set(e.experimentId, e.experimentName)
  }
  return map
})

// 获取实验名称
const getExperimentName = (experimentId) => {
  return experimentNameMap.value.get(experimentId) || '未知实验'
}

// 处理实验选择变化
const handleExperimentChange = (value) => {
  // 不限制数量，仅保持去重
  suiteForm.selectedExperiments = Array.from(new Set(value))
}

// 移除指定实验
const handleRemoveExperiment = (index) => {
  suiteForm.selectedExperiments.splice(index, 1)
}

// 刷新
const handleRefresh = () => {
  loadSuiteList()
}

// 新增
const handleAdd = () => {
  if (!checkPermission()) return
  
  isEdit.value = false
  suiteForm.suiteId = null
  suiteForm.suiteName = ''
  suiteForm.selectedExperiments = []
  dialogVisible.value = true
}

// 编辑
const handleEdit = async (row) => {
  if (!checkPermission()) return
  
  isEdit.value = true
  suiteForm.suiteId = row.suiteId
  suiteForm.suiteName = row.suiteName
  
  // 解析实验ID列表
  try {
    const response = await getSuiteDetail(row.suiteId)
    if (response.code === 200) {
      suiteForm.selectedExperiments = response.data.experimentIds || []
    }
  } catch (error) {
    console.error('获取实验套详情失败:', error)
    ElMessage.error('获取实验套详情失败')
  }
  
  dialogVisible.value = true
}

// 查看
const handleView = async (row) => {
  try {
    const response = await getSuiteDetail(row.suiteId)
    if (response.code === 200) {
      const ids = response.data.experimentIds || []
      currentSuite.value = {
        ...row,
        experimentIds: ids,
        experimentNames: ids.map(id => getExperimentName(id))
      }
      detailVisible.value = true
    }
  } catch (error) {
    ElMessage.error('获取详情失败')
  }
}

// 删除
const handleDelete = async (row) => {
  if (!checkPermission()) return
  
  try {
    await ElMessageBox.confirm(
      `确定要删除实验套"${row.suiteName}"吗？`,
      '确认删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    
    const response = await deleteSuite(row.suiteId)
    if (response.code === 200) {
      ElMessage.success('删除成功')
      loadSuiteList()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 保存
const handleSave = async () => {
  if (!suiteFormRef.value) return
  
  try {
    await suiteFormRef.value.validate()
    
    const data = {
      suiteName: suiteForm.suiteName,
      experimentIds: JSON.stringify(suiteForm.selectedExperiments)
    }
    
    if (isEdit.value) {
      data.suiteId = suiteForm.suiteId
    }
    
    const response = isEdit.value 
      ? await updateSuite(data)
      : await addSuite(data)
    
    if (response.code === 200) {
      ElMessage.success(isEdit.value ? '更新成功' : '新增成功')
      dialogVisible.value = false
      loadSuiteList()
    } else {
      ElMessage.error(response.message || (isEdit.value ? '更新失败' : '新增失败'))
    }
  } catch (error) {
    console.error('保存失败:', error)
  }
}

// 分页处理
const handleSizeChange = (size) => {
  pagination.size = size
  pagination.current = 1
  loadSuiteList()
}

const handleCurrentChange = (current) => {
  pagination.current = current
  loadSuiteList()
}

// 拖拽排序相关
const sortMode = ref(false)
const draggedItemIndex = ref(null)

const toggleSortMode = () => {
  sortMode.value = !sortMode.value
  draggedItemIndex.value = null
}

const handleDragStart = (index, event) => {
  draggedItemIndex.value = index
  event.dataTransfer.setData('text/plain', index)
}

const handleDrop = (targetIndex, event) => {
  if (draggedItemIndex.value === null || draggedItemIndex.value === targetIndex) return
  
  // 移动元素到新位置
  const [draggedItem] = suiteForm.selectedExperiments.splice(draggedItemIndex.value, 1)
  suiteForm.selectedExperiments.splice(targetIndex, 0, draggedItem)
  
  draggedItemIndex.value = null
}

onMounted(() => {
  if (checkPermission()) {
    loadSuiteList()
    loadExperimentList()
  }
})
</script>

<style scoped>
.experiment-suite {
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

.action-buttons {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-start;
}

.action-buttons .el-button {
  margin: 0;
  padding: 8px 12px;
  font-size: 14px;
  border-radius: 6px;
  transition: all 0.3s ease;
  font-weight: 500;
  letter-spacing: 0.5px;
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
}

.action-buttons .el-button .el-icon {
  margin-right: 4px;
  font-size: 14px;
}

/* 为不同类型的按钮添加特定样式 */
.action-buttons .el-button--info {
  background: linear-gradient(135deg, #909399, #a6a9ad);
  border: none;
  color: white;
}

.action-buttons .el-button--info:hover {
  background: linear-gradient(135deg, #82848a, #909399);
}

.action-buttons .el-button--primary {
  background: linear-gradient(135deg, #409eff, #66b1ff);
  border: none;
}

.action-buttons .el-button--primary:hover {
  background: linear-gradient(135deg, #337ecc, #409eff);
}

.action-buttons .el-button--danger {
  background: linear-gradient(135deg, #f56c6c, #f78989);
  border: none;
}

.action-buttons .el-button--danger:hover {
  background: linear-gradient(135deg, #dd6161, #f56c6c);
}

.experiment-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.experiment-selection {
  width: 100%;
}

.experiment-tags {
  margin-top: 10px;
  padding: 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background-color: #fafafa;
}

.tags-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.tags-container {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.experiment-tag-item {
  display: flex;
  align-items: center;
  gap: 4px;
  transition: all 0.2s ease;
}

.experiment-tag-item.sortable:hover {
  transform: translateY(-2px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.sortable {
  cursor: grab;
}

.sortable:active {
  cursor: grabbing;
}

.tag-number {
  font-weight: bold;
  color: #409eff;
  min-width: 20px;
  text-align: center;
}

.tag-name {
  flex-grow: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.drag-handle {
  cursor: grab;
  color: #667085;
  margin-left: 8px;
}

.drag-handle:hover {
  color: #409eff;
}

.dragging {
  opacity: 0.5;
  background-color: #f0f9eb;
  border: 1px dashed #67c23a;
  transform: rotate(5deg);
}

.sort-hint {
  margin-top: 10px;
  display: flex;
  align-items: center;
  color: #667085;
  font-size: 0.9em;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.sort-hint .el-icon {
  margin-right: 5px;
  color: #409eff;
}

.tags-container.sortable {
  min-height: 60px;
  padding: 8px;
  border: 2px dashed #e4e7ed;
  border-radius: 6px;
  background-color: #fafbfc;
}

.tags-container.sortable:hover {
  border-color: #409eff;
  background-color: #f0f9ff;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .experiment-suite {
    padding: 10px;
  }
  
  .header-actions {
    flex-direction: column;
    gap: 5px;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 4px;
    align-items: stretch;
  }
  
  .action-buttons .el-button {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 1200px) {
  .action-buttons .el-button {
    padding: 6px 8px;
    min-height: 36px;
    font-size: 14px;
  }
  
  .action-buttons .el-button .el-icon {
    margin-right: 2px;
  }
}
</style>
