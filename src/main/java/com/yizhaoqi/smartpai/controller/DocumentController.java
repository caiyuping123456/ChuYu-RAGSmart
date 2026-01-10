package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.service.DocumentService;
import com.yizhaoqi.smartpai.utils.LogUtils;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 文档控制器类，处理文档相关操作请求
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    @Autowired
    private DocumentService documentService;
    
    @Autowired
    private FileUploadRepository fileUploadRepository;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;
    
    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 删除文档及其相关数据
     * 
     * @param fileMd5 文件MD5
     * @param userId 当前用户ID
     * @param role 用户角色
     * @return 删除结果
     */
    @DeleteMapping("/{fileMd5}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String fileMd5,
            @RequestAttribute("userId") String userId,
            @RequestAttribute("role") String role) {

        /**
         * 这个是文档删除
         */
        /**
         * 首先还是进行性能检测
         * 传入需要操作的名称
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DELETE_DOCUMENT");
        try {
            /**
             * 日志打印
             */
            LogUtils.logBusiness("DELETE_DOCUMENT", userId, "接收到删除文档请求: fileMd5=%s, role=%s", fileMd5, role);

            /**
             * 这个是获取文件的信息
             * 注意这个是查询数据库来获取文件的全部信息（不是分片）
             */
            // 获取文件信息
            Optional<FileUpload> fileOpt = fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId);
            /**
             * 判断文件是否存在
             */
            if (fileOpt.isEmpty()) {
                /**
                 * 日志打印
                 */
                LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_NOT_FOUND");
                monitor.end("删除失败：文档不存在");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文档不存在");
                /**
                 * 文件不存在，直接返回空，同时告诉前端
                 */
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            /**
             * 如果文件存在
             * 获取这个文件（只有一个文件）
             */
            FileUpload file = fileOpt.get();
            
            // 权限检查：只有文件所有者或管理员可以删除
            /**
             * 先检查删除者的权限
             * 只有管理员和文件拥有者可以删除
             */
            if (!file.getUserId().equals(userId) && !"ADMIN".equals(role)) {
                /**
                 * 这个是标识文件权限验证失败
                 * 不能进行删除
                 * 同时需要返回前端
                 */
                LogUtils.logUserOperation(userId, "DELETE_DOCUMENT", fileMd5, "FAILED_PERMISSION_DENIED");
                LogUtils.logBusiness("DELETE_DOCUMENT", userId, "用户无权删除文档: fileMd5=%s, fileOwner=%s", fileMd5, file.getUserId());
                monitor.end("删除失败：权限不足");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.FORBIDDEN.value());
                response.put("message", "没有权限删除此文档");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
            }

            /**
             * 权限没有问题了，直接进行文件删除
             */
            // 执行删除操作
            documentService.deleteDocument(fileMd5, userId);

            /**
             * 删除成功
             * 进行日志打印
             * 同时封装前端请求
             */
            LogUtils.logFileOperation(userId, "DELETE", file.getFileName(), fileMd5, "SUCCESS");
            monitor.end("文档删除成功");
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文档删除成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            /**
             * 异常失败
             * 同时封装前端请求
             */
            LogUtils.logBusinessError("DELETE_DOCUMENT", userId, "删除文档失败: fileMd5=%s", e, fileMd5);
            monitor.end("删除失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "删除文档失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 
     * @param userId 当前用户ID
     * @param orgTags 用户所属组织标签
     * @return 可访问的文件列表
     */
    @GetMapping("/accessible")
    public ResponseEntity<?> getAccessibleFiles(
            @RequestAttribute("userId") String userId,
            @RequestAttribute("orgTags") String orgTags) {

        /**
         * 这个就是获取到用户可以访问的文件
         * 包括个人私有和公共
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_ACCESSIBLE_FILES");
        try {
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "接收到获取可访问文件请求: orgTags=%s", orgTags);
            
            List<FileUpload> files = documentService.getAccessibleFiles(userId, orgTags);
            
            LogUtils.logUserOperation(userId, "GET_ACCESSIBLE_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_ACCESSIBLE_FILES", userId, "成功获取可访问文件: fileCount=%d", files.size());
            monitor.end("获取可访问文件成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取可访问文件列表成功");
            response.put("data", files);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ACCESSIBLE_FILES", userId, "获取可访问文件失败", e);
            monitor.end("获取可访问文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取可访问文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 获取用户上传的所有文件列表
     * 
     * @param userId 当前用户ID
     * @return 用户上传的文件列表
     */
    @GetMapping("/uploads")
    public ResponseEntity<?> getUserUploadedFiles(
            @RequestAttribute("userId") String userId) {

        /**
         * 这个只是获取的是用户个人私有的文件
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("GET_USER_UPLOADED_FILES");
        try {
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId, "接收到获取用户上传文件请求");
            
            List<FileUpload> files = documentService.getUserUploadedFiles(userId);
            
            // 将FileUpload转换为包含tagName的DTO
            List<Map<String, Object>> fileData = files.stream().map(file -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("fileMd5", file.getFileMd5());
                dto.put("fileName", file.getFileName());
                dto.put("totalSize", file.getTotalSize());
                dto.put("status", file.getStatus());
                dto.put("userId", file.getUserId());
                dto.put("public", file.isPublic());
                dto.put("createdAt", file.getCreatedAt());
                dto.put("mergedAt", file.getMergedAt());
                
                // 将orgTag从tagId转换为tagName
                String orgTagName = getOrgTagName(file.getOrgTag());
                dto.put("orgTagName", orgTagName);
                
                return dto;
            }).collect(Collectors.toList());
            
            LogUtils.logUserOperation(userId, "GET_USER_UPLOADED_FILES", "file_list", "SUCCESS");
            LogUtils.logBusiness("GET_USER_UPLOADED_FILES", userId, "成功获取用户上传文件: fileCount=%d", files.size());
            monitor.end("获取用户上传文件成功");
            
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "获取用户上传文件列表成功");
            response.put("data", fileData);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_USER_UPLOADED_FILES", userId, "获取用户上传文件失败", e);
            monitor.end("获取用户上传文件失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "获取用户上传文件列表失败: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据文件名下载文件
     * 
     * @param fileName 文件名
     * @param token JWT token
     * @return 文件资源或错误响应
     */
    @GetMapping("/download")
    public ResponseEntity<?> downloadFileByName(
            @RequestParam String fileName,
            @RequestParam(required = false) String token) {

        /**
         * 这个是文件的下载操作
         * 这个是性能日志打印
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("DOWNLOAD_FILE_BY_NAME");
        try {
            /**
             * 获取下载者的token
             * 保证安全
             */
            // 验证token并获取用户信息
            String userId = null;
            String orgTags = null;

            /**
             * 要保证token不为空
             */
            if (token != null && !token.trim().isEmpty()) {
                try {
                    // 解析JWT token获取用户信息
                    // 注意：JWT中的sub字段存储用户名，userId字段存储用户ID（但有时可能存储的是用户名）
                    /**
                     * 如果token不为空
                     * 直接通过token获取token中存在的用户Id
                     * 和用户的标签
                     * 这个是下载个人私有的文件
                     */
                    userId = jwtUtils.extractUsernameFromToken(token);
                    orgTags = jwtUtils.extractOrgTagsFromToken(token);
                } catch (Exception e) {
                    LogUtils.logBusiness("DOWNLOAD_FILE_BY_NAME", "anonymous", "Token解析失败: fileName=%s", fileName);
                }
            }
            /**
             * 日志打印
             */
            LogUtils.logBusiness("DOWNLOAD_FILE_BY_NAME", userId != null ? userId : "anonymous", "接收到文件下载请求: fileName=%s", fileName);

            /**
             * 如果没有token获取token无效
             * 只能下载公开的文件
             */
            // 如果没有提供token或token无效，只允许下载公开文件
            if (userId == null) {
                // 查找公开文件
                Optional<FileUpload> publicFile = fileUploadRepository.findByFileNameAndIsPublicTrue(fileName);
                /**
                 * 这里要保证文件不为空
                 * 不然需要告诉前端
                 * 文件为空
                 */
                if (publicFile.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }

                /**
                 * 这个是获取文件在数据中的信息
                 */
                FileUpload file = publicFile.get();
                /**
                 * 调用获取到信息取minio中拼接地址
                 */
                String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());

                /**
                 * 判断Miniio拼接的地址不为空
                 */
                if (downloadUrl == null) {
                    /**
                     * 如果亲姐的地址为空
                     * 同样要告诉前端
                     */
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法生成下载链接");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

                /**
                 * 没有什么问题1
                 * 直接将下载的URL告诉前端
                 * 然前端进行下载
                 * 封装前端地址
                 */
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件下载链接生成成功");
                response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "downloadUrl", downloadUrl,
                    "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }

            /**
             * 在有token的情况1下
             * 进行访问私人的文件
             * 同时要保证
             * 文件存在
             */
            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);

            /**
             * 进行文件信息查找
             * 只查找这个文件名字
             */
            // 根据文件名查找匹配的文件
            Optional<FileUpload> targetFile = accessibleFiles.stream()
                    .filter(file -> file.getFileName().equals(fileName))
                    .findFirst();

            /**
             * 如果下载的文件温控
             * 抛出前端
             * 告诉前端文件不存在
             */
            if (targetFile.isEmpty()) {
                /**
                 * 封装请求
                 * 告诉前端
                 */
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("下载失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }

            /**
             * 文件存在
             * 进行文件的信息获取
             */
            FileUpload file = targetFile.get();

            /**
             * 同样是进行拼接地址
             * 同时调用minio的URL进行零时URL生成
             */
            // 生成下载链接或返回预签名URL
            String downloadUrl = documentService.generateDownloadUrl(file.getFileMd5());

            /**
             * 如果生成的URl为空
             * 进行前端报告
             * 封装前端请求
             *
             */
            if (downloadUrl == null) {
                /**
                 * 如果下载地址为空
                 * 告诉前端
                 * 同时结束请求
                 */
                LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "FAILED_GENERATE_URL");
                monitor.end("下载失败：无法生成下载链接");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法生成下载链接");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "DOWNLOAD", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "DOWNLOAD_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件下载链接生成成功");

            /**
             * 文件下载连接成功
             * 直接告诉前端
             */
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件下载链接生成成功");
            response.put("data", Map.of(
                "fileName", file.getFileName(),
                "downloadUrl", downloadUrl,
                "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            /**
             * 解决异常
             */
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("DOWNLOAD_FILE_BY_NAME", userId, "文件下载失败: fileName=%s", e, fileName);
            monitor.end("下载失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件下载失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 预览文件内容
     * 
     * @param fileName 文件名
     * @param token JWT token (URL参数，用于向后兼容)
     * @return 文件预览内容或错误响应
     */
    @GetMapping("/preview")
    public ResponseEntity<?> previewFileByName(
            @RequestParam String fileName,
            @RequestParam(required = false) String token) {

        /**
         * 这个是文件预览接口
         * 同样，这个是日志打印
         */
        LogUtils.PerformanceMonitor monitor = LogUtils.startPerformanceMonitor("PREVIEW_FILE_BY_NAME");
        try {
            /**
             * 这个是获取token
             * 同时验证token
             */
            // 验证token并获取用户信息
            String userId = null;
            String orgTags = null;
            
            // 优先从Spring Security上下文获取已认证的用户信息
            try {
                /**
                 * 先获取这个这个用户的用户信息
                 */
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                /**
                 * 如果用户信息不为空
                 * 就进一步获取标签信息
                 */
                if (authentication != null && authentication.isAuthenticated() 
                    && authentication.getPrincipal() instanceof UserDetails) {
                    UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                    userId = userDetails.getUsername();
                    // 从userDetails中获取组织标签信息
                    orgTags = userDetails.getAuthorities().stream()
                        .map(auth -> auth.getAuthority().replace("ROLE_", ""))
                        .findFirst()
                        .orElse(null);
                }
            } catch (Exception e) {
                LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "Security上下文获取失败: fileName=%s", fileName);
            }

            /**
             * 如果如果Security上下文中没有用户信息，从URL进行获取token
             * Security这个会保存用户的登录信息
             */
            // 如果Security上下文中没有用户信息，尝试从URL参数token中获取
            if (userId == null && token != null && !token.trim().isEmpty()) {
                try {
                    userId = jwtUtils.extractUsernameFromToken(token);
                    orgTags = jwtUtils.extractOrgTagsFromToken(token);
                } catch (Exception e) {
                    LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", "anonymous", "Token解析失败: fileName=%s", fileName);
                }
            }
            
            LogUtils.logBusiness("PREVIEW_FILE_BY_NAME", userId != null ? userId : "anonymous", "接收到文件预览请求: fileName=%s", fileName);

            /**
             * 如果都没有
             * 只能进行公开文件的下载
             */
            // 如果没有提供token或token无效，只允许预览公开文件
            if (userId == null) {
                Optional<FileUpload> publicFile = fileUploadRepository.findByFileNameAndIsPublicTrue(fileName);
                if (publicFile.isEmpty()) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.NOT_FOUND.value());
                    response.put("message", "文件不存在或需要登录访问");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                }
                
                FileUpload file = publicFile.get();
                String previewContent = documentService.getFilePreviewContent(file.getFileMd5(), file.getFileName());
                
                if (previewContent == null) {
                    Map<String, Object> response = new HashMap<>();
                    response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                    response.put("message", "无法获取文件预览内容");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                }

                /**
                 * 封装前端请求
                 */
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "文件预览内容获取成功");
                response.put("data", Map.of(
                    "fileName", file.getFileName(),
                    "content", previewContent,
                    "fileSize", file.getTotalSize()
                ));
                return ResponseEntity.ok(response);
            }
            
            // 有token的情况，查找用户可访问的文件
            List<FileUpload> accessibleFiles = documentService.getAccessibleFiles(userId, orgTags);

            /**
             * 有token的
             * 可以进行私人文件的浏览
             */
            // 根据文件名查找匹配的文件
            Optional<FileUpload> targetFile = accessibleFiles.stream()
                    .filter(file -> file.getFileName().equals(fileName))
                    .findFirst();
                    
            if (targetFile.isEmpty()) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_NOT_FOUND");
                monitor.end("预览失败：文件不存在或无权限访问");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.NOT_FOUND.value());
                response.put("message", "文件不存在或无权限访问");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
            }
            
            FileUpload file = targetFile.get();

            /**
             * 这个是获取文件的内容
             */
            // 获取文件预览内容
            String previewContent = documentService.getFilePreviewContent(file.getFileMd5(), file.getFileName());
            
            if (previewContent == null) {
                LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "FAILED_GET_CONTENT");
                monitor.end("预览失败：无法获取文件内容");
                Map<String, Object> response = new HashMap<>();
                response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
                response.put("message", "无法获取文件预览内容");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
            
            LogUtils.logFileOperation(userId, "PREVIEW", file.getFileName(), file.getFileMd5(), "SUCCESS");
            LogUtils.logUserOperation(userId, "PREVIEW_FILE_BY_NAME", fileName, "SUCCESS");
            monitor.end("文件预览内容获取成功");

            /**
             * 同样是封装请求
             */
            Map<String, Object> response = new HashMap<>();
            response.put("code", 200);
            response.put("message", "文件预览内容获取成功");
            response.put("data", Map.of(
                "fileName", file.getFileName(),
                "content", previewContent,
                "fileSize", file.getTotalSize()
            ));
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            String userId = "unknown";
            try {
                if (token != null && !token.trim().isEmpty()) {
                    userId = jwtUtils.extractUsernameFromToken(token);
                }
            } catch (Exception ignored) {}
            
            LogUtils.logBusinessError("PREVIEW_FILE_BY_NAME", userId, "文件预览失败: fileName=%s", e, fileName);
            monitor.end("预览失败: " + e.getMessage());
            Map<String, Object> response = new HashMap<>();
            response.put("code", HttpStatus.INTERNAL_SERVER_ERROR.value());
            response.put("message", "文件预览失败: " + e.getMessage()); 
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * 根据tagId获取tagName
     *
     * @param tagId 组织标签ID
     * @return 组织标签名称，如果找不到则返回原tagId
     */
    private String getOrgTagName(String tagId) {
        /**
         * 这个就是根据标签的ID获取标签的名字
         * 简单的查询
         */
        if (tagId == null || tagId.isEmpty()) {
            return null;
        }
        
        try {
            Optional<OrganizationTag> tagOpt = organizationTagRepository.findByTagId(tagId);
            if (tagOpt.isPresent()) {
                return tagOpt.get().getName();
            } else {
                LogUtils.logBusiness("GET_ORG_TAG_NAME", "system", "找不到组织标签: tagId=%s", tagId);
                return tagId; // 如果找不到标签名称，返回原tagId
            }
        } catch (Exception e) {
            LogUtils.logBusinessError("GET_ORG_TAG_NAME", "system", "查询组织标签名称失败: tagId=%s", e, tagId);
            return tagId; // 发生错误时返回原tagId
        }
    }
} 