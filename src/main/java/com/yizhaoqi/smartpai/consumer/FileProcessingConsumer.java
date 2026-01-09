package com.yizhaoqi.smartpai.consumer;

import com.yizhaoqi.smartpai.config.KafkaConfig;
import com.yizhaoqi.smartpai.model.FileProcessingTask;
import com.yizhaoqi.smartpai.service.ParseService;
import com.yizhaoqi.smartpai.service.VectorizationService;
import io.minio.MinioClient;
import io.minio.errors.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

@Service
@Slf4j
public class FileProcessingConsumer {

    private final ParseService parseService;
    private final VectorizationService vectorizationService;
    @Autowired
    private KafkaConfig kafkaConfig;


    public FileProcessingConsumer(ParseService parseService, VectorizationService vectorizationService) {
        this.parseService = parseService;
        this.vectorizationService = vectorizationService;
    }

    /**
     * 这个时Kafka的主题监听注解
     * 用于指定特定的主题进行监听
     * @param task
     */
    @KafkaListener(topics = "#{kafkaConfig.getFileProcessingTopic()}", groupId = "#{kafkaConfig.getFileProcessingGroupId()}")
    public void processTask(FileProcessingTask task) {
        /**
         * 日志打印
         */
        log.info("Received task: {}", task);
        log.info("文件权限信息: userId={}, orgTag={}, isPublic={}", 
                task.getUserId(), task.getOrgTag(), task.isPublic());

        /**
         * 初始化输入流为空
         */
        InputStream fileStream = null;
        try {
            // 下载文件
            /**
             * 先获取到这个文件体
             */
            fileStream = downloadFileFromStorage(task.getFilePath());
            // 在 downloadFileFromStorage 返回后立即检查流是否可读
            /**
             * 检查文件是否为空
             */
            if (fileStream == null) {
                throw new IOException("流为空");
            }

            /**
             * 将文件转为缓冲流
             */
            // 强制转换为可缓存流
            if (!fileStream.markSupported()) {
                fileStream = new BufferedInputStream(fileStream);
            }

            /**
             * 进行文件解析
             * 然后存到数据库中
             */
            // 解析文件
            parseService.parseAndSave(task.getFileMd5(), fileStream, 
                    task.getUserId(), task.getOrgTag(), task.isPublic());
            log.info("文件解析完成，fileMd5: {}", task.getFileMd5());

            /**
             * 解析好了之后就进行文档向量化处理
             * 这个包括向量化和存储ES
             */
            // 向量化处理
            vectorizationService.vectorize(task.getFileMd5(), 
                    task.getUserId(), task.getOrgTag(), task.isPublic());

            /**
             * 日志打印
             */
            log.info("向量化完成，fileMd5: {}", task.getFileMd5());
        } catch (Exception e) {
            log.error("Error processing task: {}", task, e);
            // 抛出异常让 Kafka 的 DefaultErrorHandler 捕获并触发重试 / 死信
            throw new RuntimeException("Error processing task", e);
        } finally {
            // 确保关闭输入流
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    log.error("Error closing file stream", e);
                }
            }
        }
    }

    /**
     * 模拟从存储系统下载文件
     *
     * @param filePath 文件路径或 URL
     * @return 文件输入流
     */
    private InputStream downloadFileFromStorage(String filePath) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        log.info("Downloading file from storage: {}", filePath);

        /**
         * 这里是进行文件的下载
         * 放回一个输入流
         * 这里分为两个部分
         * 一个是本地，一个远程
         */
        try {
            // 如果是文件系统路径
            /**
             * 如果是本地
             * 直接进文件进行文件下载
             */
            File file = new File(filePath);
            if (file.exists()) {
                log.info("Detected file system path: {}", filePath);
                return new FileInputStream(file);
            }

            // 如果是远程 URL
            /**
             * 如果是远程
             * 使用http进行下载
             */
            if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
                log.info("Detected remote URL: {}", filePath);
                URL url = new URL(filePath);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000); // 连接超时30秒
                connection.setReadTimeout(180000);   // 读取超时时间3分钟

                /**
                 * 同时设置对应的请求
                 */
                // 添加必要的请求头
                connection.setRequestProperty("User-Agent", "SmartPAI-FileProcessor/1.0");

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    log.info("Successfully connected to URL, starting download...");
                    return connection.getInputStream();
                } else if (responseCode == HttpURLConnection.HTTP_FORBIDDEN) {
                    log.error("Access forbidden - possible expired presigned URL");
                    throw new IOException("Access forbidden - the presigned URL may have expired");
                } else {
                    log.error("Failed to download file, HTTP response code: {} for URL: {}", responseCode, filePath);
                    throw new IOException(String.format("Failed to download file, HTTP response code: %d", responseCode));
                }
            }

            /**
             * 如果不是这两种情况，就直接进行抛出异常
             */
            // 如果既不是文件路径也不是 URL
            throw new IllegalArgumentException("Unsupported file path format: " + filePath);
        } catch (Exception e) {
            log.error("Error downloading file from storage: {}", filePath, e);
            return null; // 或者抛出异常
        }
    }
}