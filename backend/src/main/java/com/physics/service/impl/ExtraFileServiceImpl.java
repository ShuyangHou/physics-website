package com.physics.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.physics.entity.ExtraFile;
import com.physics.mapper.ExtraFileMapper;
import com.physics.service.ExtraFileService;
import com.physics.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 额外资料文件服务实现
 */
@Slf4j
@Service
public class ExtraFileServiceImpl extends ServiceImpl<ExtraFileMapper, ExtraFile> implements ExtraFileService {
    
    @Autowired
    private FileUtil fileUtil;
    
    @Override
    public Map<String, Object> uploadFiles(MultipartFile[] files, Long uploaderId) {
        Map<String, Object> result = new HashMap<>();
        List<ExtraFile> uploadedFiles = new ArrayList<>();
        List<Map<String, String>> errors = new ArrayList<>();
        
        int successCount = 0;
        int failCount = 0;
        
        for (MultipartFile file : files) {
            try {
                // 验证文件
                String validationResult = fileUtil.validateFile(file);
                if (validationResult != null) {
                    Map<String, String> error = new HashMap<>();
                    error.put("fileName", file.getOriginalFilename());
                    error.put("message", validationResult);
                    errors.add(error);
                    failCount++;
                    continue;
                }
                
                // 保存文件
                String filePath = fileUtil.saveFile(file);
                
                // 创建文件记录
                ExtraFile extraFile = new ExtraFile();
                extraFile.setFileName(file.getOriginalFilename());
                extraFile.setFileSize(file.getSize());
                extraFile.setFileType(fileUtil.getFileType(file.getOriginalFilename()));
                extraFile.setFilePath(filePath);
                extraFile.setUploadTime(LocalDateTime.now());
                extraFile.setUploaderId(uploaderId);
                extraFile.setCreateTime(LocalDateTime.now());
                
                if (this.save(extraFile)) {
                    uploadedFiles.add(extraFile);
                    successCount++;
                } else {
                    fileUtil.deleteFile(filePath);
                    Map<String, String> error = new HashMap<>();
                    error.put("fileName", file.getOriginalFilename());
                    error.put("message", "数据库保存失败");
                    errors.add(error);
                    failCount++;
                }
                
            } catch (Exception e) {
                log.error("上传文件失败: {}", file.getOriginalFilename(), e);
                Map<String, String> error = new HashMap<>();
                error.put("fileName", file.getOriginalFilename());
                error.put("message", "上传异常: " + e.getMessage());
                errors.add(error);
                failCount++;
            }
        }
        
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("totalCount", files.length);
        result.put("uploadedFiles", uploadedFiles);
        result.put("errors", errors);
        
        return result;
    }
    
    @Override
    public List<ExtraFile> getFileList() {
        return this.list();
    }
    
    @Override
    public boolean deleteFile(Long fileId) {
        ExtraFile extraFile = this.getById(fileId);
        if (extraFile != null) {
            // 逻辑删除：removeById 会将 deleted 置 1（@TableLogic），查询自动过滤。
            // 物理文件保留以便审计与恢复，磁盘清理可由独立任务处理。
            return this.removeById(fileId);
        }
        return false;
    }
    
    @Override
    public Map<String, Object> batchDeleteFiles(List<Long> fileIds) {
        Map<String, Object> result = new HashMap<>();
        int successCount = 0;
        int failCount = 0;
        
        for (Long fileId : fileIds) {
            try {
                if (deleteFile(fileId)) {
                    successCount++;
                } else {
                    failCount++;
                }
            } catch (Exception e) {
                log.error("删除文件失败: fileId={}", fileId, e);
                failCount++;
            }
        }
        
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("totalCount", fileIds.size());
        
        return result;
    }
}
