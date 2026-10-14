package com.vidoc.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Component;

/**
 * BCrypt 密码散列。强度 10 与库中已有哈希一致；verifyer 会按哈希自带的 cost 校验，
 * 故旧数据（$2a$10$…）无需迁移。
 */
@Component
public class PasswordHasher {

    private static final int COST = 10;

    public String encode(String rawPassword) {
        return BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray());
    }

    public boolean matches(String rawPassword, String hash) {
        return BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified;
    }
}
