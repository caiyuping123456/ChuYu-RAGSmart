package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.model.FileUpload;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.DocumentVectorRepository;
import com.yizhaoqi.smartpai.repository.FileUploadRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文档管理服务类
 * 负责文档的删除等管理操作
 */
@Service
public class DocumentService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentService.class);

    @Autowired
    private FileUploadRepository fileUploadRepository;

    @Autowired
    private DocumentVectorRepository documentVectorRepository;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private ElasticsearchService elasticsearchService;

    @Autowired
    private OrgTagCacheService orgTagCacheService;

    @Autowired
    private UserRepository userRepository;
    @Resource
    private UserService userService;

    /**
     * 删除文档及其相关数据
     * 该方法将删除:
     * 1. FileUpload记录
     * 2. DocumentVector记录
     * 3. MinIO中的文件
     * 4. Elasticsearch中的向量数据
     *
     * @param fileMd5 文件MD5
     */
    @Transactional
    public void deleteDocument(String fileMd5, String userId) {
        /**
         * 日志打印
         */
        logger.info("开始删除文档: {}", fileMd5);

        /**
         * 这个是获取文件的数据库信息
         * 是整个文件
         */
        try {
            // 获取文件信息以获取文件名
            FileUpload fileUpload = fileUploadRepository.findByFileMd5AndUserId(fileMd5, userId)
                    .orElseThrow(() -> new RuntimeException("文件不存在"));

            /**
             * 第一步是进行ES的数据删除
             * 删除ES中的向量数据
             */
            // 1. 删除Elasticsearch中的数据
            try {
                /**
                 * 调用ES客户端进行删除
                 */
                elasticsearchService.deleteByFileMd5(fileMd5);
                logger.info("成功从Elasticsearch删除文档: {}", fileMd5);
            } catch (Exception e) {
                logger.error("从Elasticsearch删除文档时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }

            /**
             * 第二部是删除Minio中的数据
             * 这个是通过数据库中的Miniio路径进行Minio文件路径构造
             * 取Minio进行进行文件删除
             * 同样是调用API
             */
            // 2. 删除MinIO中的文件
            try {
                String objectName = "merged/" + fileUpload.getFileName();
                /**
                 * 直接调用的是Minio的客户端
                 */
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket("uploads")
                                .object(objectName)
                                .build()
                );
                logger.info("成功从MinIO删除文件: {}", objectName);
            } catch (Exception e) {
                logger.error("从MinIO删除文件时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }

            /**
             * 写一部就是删除数据库中文件的分片信息了
             * 同时也是直接调用API
             */
            // 3. 删除DocumentVector记录
            try {
                documentVectorRepository.deleteByFileMd5(fileMd5);
                logger.info("成功删除文档向量记录: {}", fileMd5);
            } catch (Exception e) {
                logger.error("删除文档向量记录时出错: {}", fileMd5, e);
                // 继续删除其他数据
            }

            /**
             * 最后删除数据中这个额文件信息
             * 这个是总的信息
             */
            // 4. 删除FileUpload记录
            fileUploadRepository.deleteByFileMd5(fileMd5);
            logger.info("成功删除文件上传记录: {}", fileMd5);
            
            logger.info("文档删除完成: {}", fileMd5);
        } catch (Exception e) {
            logger.error("删除文档过程中发生错误: {}", fileMd5, e);
            throw new RuntimeException("删除文档失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户可访问的所有文件列表
     * 包括用户自己的文件、公开文件和用户所属组织的文件（支持层级权限）
     *
     * @param userId 用户ID
     * @param orgTags 用户所属的组织标签（逗号分隔的字符串，仅供兼容性使用）
     * @return 用户可访问的文件列表
     */
    public List<FileUpload> getAccessibleFiles(String userId, String orgTags) {
        logger.info("获取用户可访问文件列表: userId={}", userId);
        
        try {
            // 获取用户有效的组织标签（包含层级关系）
            User user = userRepository.findByUsername(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在: " + userId));
            System.out.println("UserId："+userId);
            System.out.println("getAccessibleFiles："+user);
            List<String> userEffectiveTags = orgTagCacheService.getUserEffectiveOrgTags(user.getUsername());
            logger.debug("用户有效组织标签: {}", userEffectiveTags);
            
            // 使用有效标签查询文件
            List<FileUpload> files;
            if (userEffectiveTags.isEmpty()) {
                // 如果用户没有任何组织标签，只返回自己的文件和公开文件
                files = fileUploadRepository.findByUserIdOrIsPublicTrue(String.valueOf(user.getId()));
                logger.debug("用户无组织标签，仅返回个人和公开文件");
            } else {
                // 查询用户可访问的所有文件（考虑层级标签）
                files = fileUploadRepository.findAccessibleFilesWithTags(String.valueOf(user.getId()), userEffectiveTags);
                logger.debug("使用有效组织标签查询文件");
            }
            //修改Bug
            logger.info("成功获取用户可访问文件列表: userId={}, fileCount={}", user.getId(), files.size());
            return files;
        } catch (Exception e) {
            logger.error("获取用户可访问文件列表失败: userId={}", userId, e);
            throw new RuntimeException("获取可访问文件列表失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 获取用户上传的所有文件列表
     *
     * @param userId 用户ID
     * @return 用户上传的文件列表
     */
    public List<FileUpload> getUserUploadedFiles(String userId) {
        logger.info("获取用户上传的文件列表: userId={}", userId);
        User user = userService.getUserById(Long.valueOf(userId));
        List<String> orgTagList = List.of(user.getOrgTags().split(","));
        try {
//            List<FileUpload> files = fileUploadRepository.findByUserId(userId);
            List<FileUpload> files = fileUploadRepository.findAccessibleIsPulicFiles(userId,orgTagList);
            logger.info("成功获取用户上传的文件列表: userId={}, fileCount={}", userId, files.size());
            return files;
        } catch (Exception e) {
            logger.error("获取用户上传的文件列表失败: userId={}", userId, e);
            throw new RuntimeException("获取用户上传的文件列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成文件下载链接
     *
     * @param fileMd5 文件MD5
     * @return 预签名下载URL
     */
    public String generateDownloadUrl(String fileMd5) {
        logger.info("生成文件下载链接: fileMd5={}", fileMd5);
        
        try {
            // 从数据库获取文件信息
            /**
             * 先通过数据库获取文件名字
             */
            FileUpload fileUpload = fileUploadRepository.findByFileMd5(fileMd5)
                    .orElseThrow(() -> new RuntimeException("文件不存在: " + fileMd5));

            /**
             * 拼接到miniio中的名字
             * 取minio中查找
             */
            // MinIO中的对象路径格式: merged/文件名
            String objectName = "merged/" + fileUpload.getFileName();

            /**
             * 生成一个URL供前端下载
             */
            // 生成预签名URL，有效期1小时
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket("uploads")
                            .object(objectName)
                            .expiry(3600) // 1小时有效期
                            .build()
            );
            
            logger.info("成功生成文件下载链接: fileMd5={}, fileName={}, objectName={}", 
                    fileMd5, fileUpload.getFileName(), objectName);
            /**
             * 返回URL
             *
             */
            return presignedUrl;
        } catch (Exception e) {
            logger.error("生成文件下载链接失败: fileMd5={}", fileMd5, e);
            return null;
        }
    }
    
    /**
     * 获取文件预览内容
     * 
     * @param fileMd5 文件MD5
     * @param fileName 文件名
     * @return 文件预览内容，对于文本文件返回前几KB内容，非文本文件返回文件信息
     */
    public Map<String, Object> getFilePreviewContent(String fileMd5, String fileName) {
        logger.info("获取文件预览内容: fileMd5={}, fileName={}", fileMd5, fileName);

        try {
            String objectName = "merged/" + fileName;
            String fileExtension = getFileExtension(fileName).toLowerCase();
            FileUpload fileUpload = fileUploadRepository.findByFileMd5(fileMd5)
                    .orElseThrow(() -> new RuntimeException("文件不存在: " + fileMd5));

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("fileName", fileName);
            result.put("fileSize", fileUpload.getTotalSize());
            result.put("fileType", fileExtension);

            if (isPdfFile(fileExtension)) {
                String presignedUrl = generateDownloadUrl(fileMd5);
                result.put("previewType", "pdf");
                result.put("previewUrl", presignedUrl);
                logger.info("PDF文件预览，返回presigned URL: fileMd5={}", fileMd5);
                return result;
            } else if (isImageFile(fileExtension)) {
                String presignedUrl = generateDownloadUrl(fileMd5);
                result.put("previewType", "image");
                result.put("previewUrl", presignedUrl);
                logger.info("图片文件预览，返回presigned URL: fileMd5={}", fileMd5);
                return result;
            } else if (isTextFile(fileExtension)) {
                // 文本文件：读取前10KB内容
                try (InputStream inputStream = minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket("uploads")
                                .object(objectName)
                                .build())) {

                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    StringBuilder content = new StringBuilder();
                    String line;
                    int bytesRead = 0;
                    int maxBytes = 10240;

                    while ((line = reader.readLine()) != null && bytesRead < maxBytes) {
                        content.append(line).append("\n");
                        bytesRead += line.getBytes("UTF-8").length + 1;
                    }

                    String textContent = content.toString();
                    if (bytesRead >= maxBytes) {
                        textContent += "\n... (内容已截断，仅显示前10KB)";
                    }

                    result.put("previewType", "text");
                    result.put("content", textContent);
                    logger.info("成功获取文本文件预览内容: fileMd5={}, contentLength={}", fileMd5, textContent.length());
                    return result;
                }
            } else {
                // 其他不支持预览的文件类型
                String fileInfo = String.format(
                    "文件名: %s\n文件大小: %s\n文件类型: %s\n上传时间: %s\n\n此文件类型不支持预览，请下载后查看。",
                    fileName,
                    formatFileSize(fileUpload.getTotalSize()),
                    fileExtension.toUpperCase(),
                    fileUpload.getCreatedAt()
                );
                result.put("previewType", "unsupported");
                result.put("content", fileInfo);
                logger.info("返回非文本文件信息: fileMd5={}", fileMd5);
                return result;
            }

        } catch (Exception e) {
            logger.error("获取文件预览内容失败: fileMd5={}, fileName={}", fileMd5, fileName, e);
            Map<String, Object> errorResult = new java.util.HashMap<>();
            errorResult.put("previewType", "error");
            errorResult.put("content", "预览失败: " + e.getMessage());
            return errorResult;
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1);
    }
    
    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String extension) {
        String[] textExtensions = {
            "txt", "md", "doc", "docx", "html", "htm", "xml", "json",
            "csv", "log", "java", "js", "ts", "py", "cpp", "c", "h", "css",
            "scss", "less", "sql", "yml", "yaml", "properties", "conf", "config"
        };

        return Arrays.stream(textExtensions)
                .anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }

    private boolean isPdfFile(String extension) {
        return "pdf".equalsIgnoreCase(extension);
    }

    private boolean isImageFile(String extension) {
        String[] imageExtensions = {"jpg", "jpeg", "png", "bmp", "webp", "tiff", "gif"};
        return Arrays.stream(imageExtensions)
                .anyMatch(ext -> ext.equalsIgnoreCase(extension));
    }
    
    /**
     * 格式化文件大小
     */
    private String formatFileSize(Long size) {
        if (size == null) return "未知";
        
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
} 