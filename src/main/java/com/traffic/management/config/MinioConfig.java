package com.traffic.management.config;

import io.minio.MinioClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO对象存储配置
 */
@Configuration
@ConfigurationProperties(prefix = "minio")
@Getter
@Setter
public class MinioConfig {

    /**
     * MinIO服务端点（API地址）
     * 例如: http://localhost:9000
     */
    private String endpoint;

    /**
     * MinIO访问密钥（用户名）
     */
    private String accessKey;

    /**
     * MinIO秘密密钥（密码）
     */
    private String secretKey;

    /**
     * 默认bucket名称
     */
    private String bucketName;

    /**
     * 是否允许公开访问
     */
    private Boolean publicAccess = true;

    /**
     * 创建MinioClient Bean
     *
     * @return MinioClient客户端
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
