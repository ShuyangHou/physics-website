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

        // 拒绝包含路径分隔符或空字节的文件名，避免落盘时越界或被截断
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("\u0000")) {
            return "文件名包含非法字符";
        }

        String extension = fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
        if (extension.isEmpty() || !ALLOWED_TYPES.contains(extension)) {
            return "不支持的文件类型";
        }

        // 二次校验：浏览器声明的 MIME 类型需与允许集合相符（防止伪装扩展名）
        String contentType = file.getContentType();
        if (contentType != null && !isAllowedContentType(contentType)) {
            return "文件内容类型与扩展名不符";
        }

        return null;
    }

    private static final Set<String> ALLOWED_CONTENT_TYPES = new HashSet<>(Arrays.asList(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            // 部分浏览器/客户端对 office 文件会上报通用二进制类型
            "application/octet-stream"
    ));

    private boolean isAllowedContentType(String contentType) {
        return ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase());
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
            Path path = resolveWithinRoot(filePath);
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }
    
    /**
     * 获取文件
     */
    public File getFile(String filePath) {
        return resolveWithinRoot(filePath).toFile();
    }

    /**
     * 将相对路径安全地解析到上传根目录内，防止路径穿越（如 ../ 越界访问任意文件）。
     * 解析后的真实路径必须仍位于上传根目录之下，否则抛出异常。
     */
    public Path resolveWithinRoot(String filePath) {
        if (filePath == null) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        // 拒绝绝对路径与空字节，避免绕过
        if (filePath.contains("\u0000")) {
            throw new IllegalArgumentException("非法文件路径");
        }
        Path root = Paths.get(uploadRoot).toAbsolutePath().normalize();
        Path resolved = root.resolve(filePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("非法文件路径：越界访问被拒绝");
        }
        return resolved;
    }
}
