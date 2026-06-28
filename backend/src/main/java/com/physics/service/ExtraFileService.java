package com.physics.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.physics.entity.ExtraFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 额外资料文件服务
 */
public interface ExtraFileService extends IService<ExtraFile> {
    
    /**
     * 上传文件
     */
    Map<String, Object> uploadFiles(MultipartFile[] files, Long uploaderId);
    
    /**
     * 获取文件列表
     */
    List<ExtraFile> getFileList();
    
    /**
     * 删除文件
     */
    boolean deleteFile(Long fileId);
    
    /**
     * 批量删除文件
     */
    Map<String, Object> batchDeleteFiles(List<Long> fileIds);
}
