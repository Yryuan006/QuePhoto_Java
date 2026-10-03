package com.quephoto.oss;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Service
public class ImageUrlService {
    private final String baseUrl;
    public ImageUrlService(@Value("${oss.base-url}") String baseUrl) {
        URI uri = URI.create(baseUrl);
        if(!"https".equals(uri.getScheme()) || uri.getHost() == null
        || uri.getQuery() != null || uri.getFragment() != null
        || uri.getUserInfo() != null
        || (uri.getPath() != null && !uri.getPath().isEmpty())
        && !"/".equals(uri.getPath())) {
            throw new IllegalArgumentException("图片基址必须是无路径、查询和凭据的HTTPS域名");
        }
        this.baseUrl = baseUrl.endsWith("/") ?
                baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }
    public String url(String objectKey) {
        if(objectKey == null) return null;
        if(objectKey.startsWith("/") || objectKey.contains("://")) {
            throw new IllegalArgumentException("数据库图片Key非法");
        }
        return baseUrl + "/" + UriUtils.encodePath(objectKey, StandardCharsets.UTF_8);
    }
}
