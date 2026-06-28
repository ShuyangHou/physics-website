package com.physics.controller;

import com.physics.common.Result;
import com.physics.entity.ExtraFile;
import com.physics.service.ExtraFileService;
import com.physics.service.UserService;
import com.physics.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 文件管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/file")
public class FileController {
    
    @Autowired
    private ExtraFileService extraFileService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private FileUtil fileUtil;
    
    /**
     * 通用文件上传
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(@RequestParam("file") MultipartFile file,
                                                  @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            String validationResult = fileUtil.validateFile(file);
            if (validationResult != null) {
                return Result.error(validationResult);
            }
            
            String filePath = fileUtil.saveTemplateFile(file);
            if (filePath == null) {
                return Result.error("文件保存失败");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("fileName", file.getOriginalFilename());
            result.put("filePath", filePath);
            result.put("fileSize", file.getSize());
            result.put("fileType", fileUtil.getFileType(file.getOriginalFilename()));
            
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("文件上传失败", e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 通用文件下载
     */
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadFile(@RequestParam String filePath,
                                                @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            File file = fileUtil.getFile(filePath);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            String fileName = file.getName();
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("文件下载失败: {}", filePath, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 通用文件删除
     */
    @PostMapping("/delete")
    public Result<Boolean> deleteFile(@RequestParam String filePath,
                                      @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null || !"admin".equals(user.getUserType())) {
                return Result.error("权限不足");
            }
            
            boolean success = fileUtil.deleteFile(filePath);
            return success ? Result.success(true) : Result.error("文件删除失败");
            
        } catch (Exception e) {
            log.error("文件删除失败: {}", filePath, e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }
    
    // ==================== 额外资料文件管理 ====================
    
    /**
     * 获取额外资料文件列表
     */
    @GetMapping("/extra/list")
    public Result<List<ExtraFile>> getExtraFileList(@CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null) {
                return Result.error("用户不存在");
            }
            
            List<ExtraFile> files = extraFileService.getFileList();
            return Result.success(files);
            
        } catch (Exception e) {
            log.error("获取文件列表失败", e);
            return Result.error("获取文件列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 上传额外资料文件
     */
    @PostMapping("/extra/upload")
    public Result<Map<String, Object>> uploadExtraFiles(@RequestParam("files") MultipartFile[] files,
                                                        @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null || !"admin".equals(user.getUserType())) {
                return Result.error("权限不足");
            }
            
            if (files.length > 10) {
                return Result.error("单次最多上传10个文件");
            }
            
            Map<String, Object> result = extraFileService.uploadFiles(files, userId);
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("上传额外资料文件失败", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载单个额外资料文件
     */
    @GetMapping("/extra/download/{fileId}")
    public ResponseEntity<Resource> downloadExtraFile(@PathVariable Long fileId,
                                                     @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }
            
            ExtraFile extraFile = extraFileService.getById(fileId);
            if (extraFile == null) {
                return ResponseEntity.notFound().build();
            }
            
            File file = fileUtil.getFile(extraFile.getFilePath());
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=" + URLEncoder.encode(extraFile.getFileName(), StandardCharsets.UTF_8.toString()))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("下载额外资料文件失败: fileId={}", fileId, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * 删除单个额外资料文件
     */
    @DeleteMapping("/extra/delete/{fileId}")
    public Result<Boolean> deleteExtraFile(@PathVariable Long fileId,
                                           @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null || !"admin".equals(user.getUserType())) {
                return Result.error("权限不足");
            }
            
            boolean success = extraFileService.deleteFile(fileId);
            return success ? Result.success(true) : Result.error("文件删除失败");
            
        } catch (Exception e) {
            log.error("删除额外资料文件失败: fileId={}", fileId, e);
            return Result.error("文件删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 批量删除额外资料文件
     */
    @PostMapping("/extra/batch-delete")
    public Result<Map<String, Object>> batchDeleteExtraFiles(@RequestBody Map<String, List<Long>> request,
                                                             @CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null || !"admin".equals(user.getUserType())) {
                return Result.error("权限不足");
            }
            
            List<Long> fileIds = request.get("fileIds");
            if (fileIds == null || fileIds.isEmpty()) {
                return Result.error("请选择要删除的文件");
            }
            
            Map<String, Object> result = extraFileService.batchDeleteFiles(fileIds);
            return Result.success(result);
            
        } catch (Exception e) {
            log.error("批量删除额外资料文件失败", e);
            return Result.error("批量删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 下载额外资料压缩包：将所有额外资料打包成真正的 ZIP 返回
     */
    @GetMapping("/extra/download-zip")
    public ResponseEntity<Resource> downloadExtraZip(@CookieValue("userId") Long userId) {
        try {
            com.physics.entity.User user = userService.getById(userId);
            if (user == null) {
                return ResponseEntity.notFound().build();
            }

            List<ExtraFile> files = extraFileService.getFileList();
            if (files.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Set<String> usedNames = new HashSet<>();
            int packed = 0;
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                for (ExtraFile ef : files) {
                    File file;
                    try {
                        file = fileUtil.getFile(ef.getFilePath());
                    } catch (IllegalArgumentException ex) {
                        // 跳过非法路径
                        continue;
                    }
                    if (!file.exists() || !file.isFile()) {
                        continue;
                    }
                    // 处理 ZIP 内重名
                    String entryName = ef.getFileName() != null ? ef.getFileName() : file.getName();
                    String uniqueName = entryName;
                    int n = 1;
                    while (usedNames.contains(uniqueName)) {
                        int dot = entryName.lastIndexOf('.');
                        if (dot > 0) {
                            uniqueName = entryName.substring(0, dot) + "_" + n + entryName.substring(dot);
                        } else {
                            uniqueName = entryName + "_" + n;
                        }
                        n++;
                    }
                    usedNames.add(uniqueName);

                    zos.putNextEntry(new ZipEntry(uniqueName));
                    Files.copy(file.toPath(), zos);
                    zos.closeEntry();
                    packed++;
                }
            }

            if (packed == 0) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new ByteArrayResource(baos.toByteArray());
            String zipName = URLEncoder.encode("额外资料包.zip", StandardCharsets.UTF_8.toString());

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + zipName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            log.error("下载额外资料压缩包失败", e);
            return ResponseEntity.notFound().build();
        }
    }
}
