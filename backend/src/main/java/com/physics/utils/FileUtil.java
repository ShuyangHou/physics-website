package com.physics.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件工具类
 */
@Component
public class FileUtil {
    
    @Value("${file.upload.path:uploads}")
    private String uploadRoot;
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> ALLOWED_TYPES = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
    ));
    
    /**
     * 验证文件
     */
    public String validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            return "文件不能为空";
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            return "文件大小不能超过10MB";
        }
        
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.contains(".")) {
            return "文件名格式错误";
        }
        
        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_TYPES.contains(extension)) {
            return "不支持的文件类型";
        }
        
        return null;
    }
    
    /**
     * 获取文件类型
     */
    public String getFileType(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 生成文件名（保持原始名称，避免重复）
     */
    public String generateFileName(String originalName) {
        if (originalName == null || originalName.trim().isEmpty()) {
            return "file_" + System.currentTimeMillis();
        }
        
        // 如果文件名包含路径分隔符，只取文件名部分
        String fileName = originalName;
        if (fileName.contains("/") || fileName.contains("\\")) {
            fileName = fileName.substring(Math.max(fileName.lastIndexOf("/"), fileName.lastIndexOf("\\")) + 1);
        }
        
        return fileName;
    }
    
    /**
     * 生成唯一文件名（避免重复）
     */
    public String generateUniqueFileName(String originalName, String directory) {
        String fileName = generateFileName(originalName);
        Path filePath = Paths.get(uploadRoot, directory, fileName);
        
        // 如果文件不存在，直接返回
        if (!Files.exists(filePath)) {
            return fileName;
        }
        
        // 如果文件存在，添加数字后缀
        String nameWithoutExt = fileName;
        String extension = "";
        if (fileName.contains(".")) {
            int lastDotIndex = fileName.lastIndexOf(".");
            nameWithoutExt = fileName.substring(0, lastDotIndex);
            extension = fileName.substring(lastDotIndex);
        }
        
        int counter = 1;
        String newFileName;
        do {
            newFileName = nameWithoutExt + "_" + counter + extension;
            filePath = Paths.get(uploadRoot, directory, newFileName);
            counter++;
        } while (Files.exists(filePath));
        
        return newFileName;
    }
    
    /**
     * 保存额外资料文件
     */
    public String saveFile(MultipartFile file) throws IOException {
        // 创建目录
        Path directory = Paths.get(uploadRoot, "extra");
        Files.createDirectories(directory);
        
        // 生成唯一文件名
        String fileName = generateUniqueFileName(file.getOriginalFilename(), "extra");
        Path filePath = directory.resolve(fileName);
        
        // 保存文件
        file.transferTo(filePath.toFile());
        
        return "extra/" + fileName;
    }

    /**
     * 保存字节流到 extra 目录（用于系统自动归档导出的xlsx）
     */
    public String saveExtraBytes(byte[] bytes, String originalName) throws IOException {
        if (bytes == null) {
            throw new IOException("文件内容为空");
        }
        // 创建目录
        Path directory = Paths.get(uploadRoot, "extra");
        Files.createDirectories(directory);

        String fileName = generateUniqueFileName(originalName, "extra");
        Path filePath = directory.resolve(fileName);
        try (OutputStream os = Files.newOutputStream(filePath)) {
            os.write(bytes);
        }
        return "extra/" + fileName;
    }
    
    /**
     * 保存实验模板文件
     */
    public String saveTemplateFile(MultipartFile file) throws IOException {
        // 创建目录
        Path directory = Paths.get(uploadRoot);
        Files.createDirectories(directory);
        
        // 生成唯一文件名
        String fileName = generateUniqueFileName(file.getOriginalFilename(), "");
        Path filePath = directory.resolve(fileName);
        
        // 保存文件
        file.transferTo(filePath.toFile());
        
        return fileName;
    }
    
    /**
     * 删除文件
     */
    public boolean deleteFile(String filePath) {
        try {
            Path path = Paths.get(uploadRoot, filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 获取文件
     */
    public File getFile(String filePath) {
        Path path = Paths.get(uploadRoot, filePath);
        return path.toFile();
    }
}
