package com.quephoto.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.comm.SignVersion;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("import")
@EnableConfigurationProperties(OssImportProperties.class)
public class OssImportConfig {

    @Bean(destroyMethod = "shutdown")
    OSS ossClient(OssImportProperties p) {

        var options = new ClientBuilderConfiguration();

        options.setSignatureVersion(SignVersion.V4);
        options.setConnectionTimeout(5000);
        options.setSocketTimeout(10000);

        var credentials =
                CredentialsProviderFactory.newDefaultCredentialProvider(
                        p.accessKeyId(),
                        p.accessKeySecret()
                );

        return OSSClientBuilder.create()
                .endpoint(p.endpoint())
                .region(p.region())
                .credentialsProvider(credentials)
                .clientConfiguration(options)
                .build();
    }
}