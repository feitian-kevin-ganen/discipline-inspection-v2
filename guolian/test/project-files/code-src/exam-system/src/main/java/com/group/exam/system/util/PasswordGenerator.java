package com.group.exam.system.util;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 初始密码生成器：8 位，含大小写字母 + 数字 + 特殊字符（满足 8-32 位含字母+数字规则）
 */
public class PasswordGenerator {

    private static final String LETTERS = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SPECIALS = "@#*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    public static String generate() {
        List<Character> chars = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            chars.add(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
        }
        for (int i = 0; i < 3; i++) {
            chars.add(DIGITS.charAt(RANDOM.nextInt(DIGITS.length())));
        }
        chars.add(SPECIALS.charAt(RANDOM.nextInt(SPECIALS.length())));
        Collections.shuffle(chars, RANDOM);
        StringBuilder sb = new StringBuilder(chars.size());
        chars.forEach(sb::append);
        return sb.toString();
    }
}
