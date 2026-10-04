package com.quephoto.importing;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSSException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
@Profile("import")
public class ImportRunner implements ApplicationRunner, ExitCodeGenerator {
    private static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private final ImageImportCoordinator coordinator;
    private final JsonMapper jsonMapper;
    private final Environment environment;
    private int exitCode = 1;

    public ImportRunner(
            ImageImportCoordinator coordinator,
            JsonMapper jsonMapper,
            Environment environment
    ) {
        this.coordinator = coordinator;
        this.environment = environment;

        // 从 Spring 的配置构建导入专用 Mapper，不改变 HTTP 的 JSON 配置。
        this.jsonMapper = jsonMapper.rebuild()
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            String webType = environment.getProperty(
                    "spring.main.web-application-type");
            if (!"none".equalsIgnoreCase(webType)) {
                throw new IllegalArgumentException(
                        "导入必须指定 --spring.main.web-application-type=none");
            }

            String file = environment.getProperty("import.file");
            if (file == null || file.isBlank()) {
                throw new IllegalArgumentException(
                        "缺少 --import.file=清单的绝对路径");
            }

            ImportManifest manifest = readManifest(Path.of(file));
            var result = coordinator.importImages(manifest);

            // result 返回时，事务已经成功提交。
            System.out.println(jsonMapper.writeValueAsString(result));
            exitCode = 0;
        } catch (OSSException ex) {
            fail(3, "OSS 核验失败，code=" + ex.getErrorCode()
                    + " requestId=" + ex.getRequestId());
        } catch (ClientException ex) {
            fail(3, "OSS 客户端或网络失败，请检查配置和网络");
        } catch (ResponseStatusException ex) {
            fail(2, ex.getStatusCode().value() + " " + ex.getReason());
        } catch (IllegalArgumentException ex) {
            fail(2, ex.getMessage());
        } catch (IOException ex) {
            fail(2, "无法读取清单，请检查路径和文件权限");
        } catch (DuplicateKeyException ex) {
            fail(4, "数据库唯一键冲突，请核对登记归属后重试");
        } catch (DataAccessException ex) {
            fail(4, "数据库操作失败，请核对数据库状态后重试");
        } catch (JacksonException ex) {
            // 读取错误已在 readManifest 转成输入错误；这里是摘要输出错误。
            fail(1, "结果摘要生成失败，数据库可能已提交，请核对状态");
        } catch (Exception ex) {
            fail(1, "导入发生未预期错误，类型="
                    + ex.getClass().getSimpleName());
        }
    }

    private ImportManifest readManifest(Path path) throws IOException {
        if (!path.isAbsolute() || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException(
                    "import.file 必须是已有普通文件的绝对路径");
        }

        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_MANIFEST_BYTES + 1);
        }
        if (bytes.length > MAX_MANIFEST_BYTES) {
            throw new IllegalArgumentException("清单文件不能超过 1 MiB");
        }

        try {
            return jsonMapper.readValue(bytes, ImportManifest.class);
        } catch (JacksonException ex) {
            // 不把完整 JSON 内容或完整异常写进日志。
            throw new IllegalArgumentException(
                    "JSON 格式或字段类型不正确，请检查括号、字段名和整数值");
        }
    }

    private void fail(int code, String message) {
        exitCode = code;
        System.err.println("IMPORT_FAILED " + message);
    }

    @Override
    public int getExitCode() {
        return exitCode;
    }
}