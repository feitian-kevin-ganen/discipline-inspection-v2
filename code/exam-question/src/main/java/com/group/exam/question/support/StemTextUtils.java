package com.group.exam.question.support;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;

/**
 * 题干文本规范化与相似度工具（题库查重）。
 *
 * <p>规范化：去 HTML 标签 → 常见 HTML 实体还原 → 全角转半角 → 去全部空白 → 转小写。
 * 相似度：规范化纯文本的字符 bigram 集合 Jaccard 相似度。
 */
public final class StemTextUtils {

    private StemTextUtils() {
    }

    /** 去 HTML 标签并还常见实体，得到纯文本（保留空白，用于摘要展示） */
    public static String plainText(String stemHtml) {
        if (stemHtml == null) {
            return "";
        }
        String text = stemHtml.replaceAll("(?s)<[^>]+>", "");
        return unescape(text).replaceAll("\\s+", " ").trim();
    }

    /** 规范化：去标签 → 实体还原 → 全角转半角 → 去全部空白 → 小写（查重口径） */
    public static String normalize(String stemHtml) {
        if (stemHtml == null) {
            return "";
        }
        String text = stemHtml.replaceAll("(?s)<[^>]+>", "");
        text = unescape(text);
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '　') {
                c = ' ';
            } else if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            sb.append(c);
        }
        return sb.toString().replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /** 题干规范化 MD5（32 位小写 hex），入库 stem_md5 */
    public static String normalizeMd5(String stemHtml) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(normalize(stemHtml).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 计算失败", e);
        }
    }

    /** 字符 bigram 集合 Jaccard 相似度，[0,1]；两串均空视为 1 */
    public static double bigramJaccard(String normalizedA, String normalizedB) {
        if (normalizedA == null) {
            normalizedA = "";
        }
        if (normalizedB == null) {
            normalizedB = "";
        }
        if (normalizedA.equals(normalizedB)) {
            return 1.0d;
        }
        Set<String> a = bigrams(normalizedA);
        Set<String> b = bigrams(normalizedB);
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0d;
        }
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0d;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    /** 纯文本摘要（列表/查重结果展示用），超长截断 */
    public static String summary(String stemHtml, int maxLen) {
        String text = plainText(stemHtml);
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    private static Set<String> bigrams(String s) {
        Set<String> set = new HashSet<>();
        if (s.length() == 1) {
            set.add(s);
            return set;
        }
        for (int i = 0; i < s.length() - 1; i++) {
            set.add(s.substring(i, i + 2));
        }
        return set;
    }

    private static String unescape(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }
}
