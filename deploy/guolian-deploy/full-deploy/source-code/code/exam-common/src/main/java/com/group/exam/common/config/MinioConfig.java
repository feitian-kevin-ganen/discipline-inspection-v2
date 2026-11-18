package com.group.exam.common.config;

import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储：bucket 启动时自动创建，提供上传与预签名下载地址
 */
public class MinioConfig {

    @Data
    @Configuration
    @ConfigurationProperties(prefix = "exam.minio")
    public static class MinioProperties {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket;
        /** 预签名 URL 对外暴露的 host（外网可达地址）；不配置时回退 endpoint */
        private String publicEndpoint;

        @Bean
        public MinioClient minioClient() {
            return MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
        }
    }

    @Slf4j
    @Service
    public static class MinioService {

        private final MinioClient minioClient;
        private final MinioProperties properties;
        /** 以 publicEndpoint 构建的签名客户端：预签名 URL 的签名随 host 变化，必须用对外地址签名 */
        private volatile MinioClient presignClient;

        public MinioService(MinioClient minioClient, MinioProperties properties) {
            this.minioClient = minioClient;
            this.properties = properties;
        }

        @PostConstruct
        public void initBucket() {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(properties.getBucket()).build());
                if (!exists) {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                    log.info("MinIO bucket [{}] 已创建", properties.getBucket());
                }
            } catch (Exception e) {
                log.error("MinIO bucket 初始化失败: {}", e.getMessage());
            }
        }

        /**
         * 生成对象名：{prefix}/yyyyMMdd/{uuid}.{ext}
         */
        public String buildObjectName(String prefix, String originalFilename) {
            String ext = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
            }
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            return prefix + "/" + date + "/" + UUID.randomUUID().toString().replace("-", "") + ext;
        }

        /**
         * 上传文件，返回对象名
         */
        public String upload(String objectName, InputStream in, long size, String contentType) {
            try {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .stream(in, size, -1)
                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                        .build());
                return objectName;
            } catch (Exception e) {
                throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
            }
        }

        /**
         * 生成预签名 GET 地址（默认 2 小时有效）。
         * 配置了 exam.minio.public-endpoint 时用其作为 URL host 并以其参与签名，
         * 保证外网客户端拿到的是可达地址且签名校验通过（签名随 Host 头计算）。
         */
        public String presignedUrl(String objectName, int expirySeconds) {
            try {
                return presignClient().getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.GET)
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .expiry(expirySeconds, TimeUnit.SECONDS)
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("生成文件访问地址失败: " + e.getMessage(), e);
            }
        }

        public String presignedUrl(String objectName) {
            return presignedUrl(objectName, 2 * 3600);
        }

        /**
         * 读取对象流（服务端代理转发用，随 try-with-resources 关闭）
         */
        public io.minio.GetObjectResponse getObject(String objectName) {
            try {
                return minioClient.getObject(io.minio.GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
            }
        }

        /** 对象是否存在 */
        public boolean exists(String objectName) {
            try {
                minioClient.statObject(io.minio.StatObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .build());
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /** 对象大小（字节） */
        public long statSize(String objectName) {
            try {
                return minioClient.statObject(io.minio.StatObjectArgs.builder()
                                .bucket(properties.getBucket())
                                .object(objectName)
                                .build())
                        .size();
            } catch (Exception e) {
                throw new RuntimeException("文件信息读取失败: " + e.getMessage(), e);
            }
        }

        /** 读取对象指定字节区间（Range 流式转发用，随 try-with-resources 关闭） */
        public io.minio.GetObjectResponse getObjectRange(String objectName, long offset, long length) {
            try {
                return minioClient.getObject(io.minio.GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectName)
                        .offset(offset)
                        .length(length)
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("文件读取失败: " + e.getMessage(), e);
            }
        }

        /** 签名客户端：public-endpoint 未配置或与 endpoint 相同则复用内部 client */
        private MinioClient presignClient() {
            String pub = properties.getPublicEndpoint();
            if (pub == null || pub.isBlank() || pub.equals(properties.getEndpoint())) {
                return minioClient;
            }
            MinioClient c = presignClient;
            if (c == null) {
                synchronized (this) {
                    if (presignClient == null) {
                        presignClient = MinioClient.builder()
                                .endpoint(pub)
                                .credentials(properties.getAccessKey(), properties.getSecretKey())
                                .build();
                    }
                    c = presignClient;
                }
            }
            return c;
        }
    }
}
