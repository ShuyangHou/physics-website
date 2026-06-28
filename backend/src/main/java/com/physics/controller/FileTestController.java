package com.physics.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/file-test")
public class FileTestController {
    
    @Value("${file.upload.path:uploads}")
    private String uploadRoot;
    
    /**
     * 测试文件上传配置
     */
    @GetMapping("/config")
    public Map<String, Object> getFileConfig() {
        Map<String, Object> result = new HashMap<>();
        
        // 上传根目录
        result.put("uploadRoot", uploadRoot);
        
        // 检查目录是否存在
        File uploadDir = new File(uploadRoot);
        result.put("directoryExists", uploadDir.exists());
        result.put("isDirectory", uploadDir.isDirectory());
        result.put("canRead", uploadDir.canRead());
        result.put("canWrite", uploadDir.canWrite());
        result.put("absolutePath", uploadDir.getAbsolutePath());
        
        // 列出目录中的文件
        if (uploadDir.exists() && uploadDir.isDirectory()) {
            File[] files = uploadDir.listFiles();
            if (files != null) {
                result.put("fileCount", files.length);
                String[] fileNames = new String[Math.min(files.length, 10)]; // 最多显示10个文件
                for (int i = 0; i < fileNames.length; i++) {
                    fileNames[i] = files[i].getName();
                }
                result.put("sampleFiles", fileNames);
            }
        }
        
        return result;
    }
}
