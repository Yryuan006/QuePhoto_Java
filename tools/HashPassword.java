import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class HashPassword {
    public static void main(String[] args) {
        char[] raw;
        if (args.length == 1 && "--from-environment".equals(args[0])) {
            // 供本地启动脚本捕获哈希；原始密码不放进命令参数或文件。
            String password = System.getenv("QUEPHOTO_BOOTSTRAP_PASSWORD");
            if (password == null) throw new IllegalStateException("启动脚本没有提供管理员密码");
            raw = password.toCharArray();
        } else if (args.length == 0) {
            var console = System.console();
            if (console == null) throw new IllegalStateException("请在真实终端运行");
            raw = console.readPassword("Password: ");
        } else {
            throw new IllegalArgumentException("不支持的哈希工具参数");
        }
        if (raw == null) throw new IllegalStateException("已取消密码输入");
        try {
            String value = new String(raw);
            int bytes = value.getBytes(StandardCharsets.UTF_8).length;
            if (bytes < 12 || bytes > 72) throw new IllegalArgumentException("使用12–72字节密码");
            System.out.println(new BCryptPasswordEncoder(12).encode(value));
        } finally {
            Arrays.fill(raw, '\0');
        }
    }
}
