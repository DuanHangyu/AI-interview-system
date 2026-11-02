package system.assessment.defense.infrastructure.common;

import cn.hutool.crypto.digest.BCrypt;
import org.apache.commons.lang3.StringUtils;

/**
 * 密码哈希（BCrypt）。库里早期数据是明文，校验时兼容明文，登录成功后由调用方升级为哈希。
 */
public final class PasswordCodec {

    private PasswordCodec() {
    }

    public static String hash(String raw) {
        return BCrypt.hashpw(raw, BCrypt.gensalt());
    }

    /** 空白密码表示"不修改"，返回 null，配合 MyBatis-Plus 的非空更新策略。 */
    public static String hashOrNull(String raw) {
        return StringUtils.isBlank(raw) ? null : hash(raw);
    }

    public static boolean matches(String raw, String stored) {
        if (raw == null || stored == null) {
            return false;
        }
        if (isHashed(stored)) {
            try {
                return BCrypt.checkpw(raw, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return stored.equals(raw);
    }

    public static boolean isHashed(String stored) {
        return stored != null && stored.length() == 60 && stored.startsWith("$2");
    }
}
