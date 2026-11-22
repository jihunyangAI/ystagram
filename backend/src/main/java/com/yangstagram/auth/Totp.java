package com.yangstagram.auth;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP (RFC 6238): 30초마다 바뀌는 6자리 코드. Google Authenticator 등 인증 앱과 같은 방식(HMAC-SHA1).
 * 시각은 실제 시계를 쓴다 (Lab 가상 시계를 따르면 휴대폰 앱과 코드가 달라진다).
 */
public final class Totp {

    private static final int STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 새 비밀 키 (160비트, Base32) */
    public static String newSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32(bytes);
    }

    public static String code(String secret, long epochSeconds) {
        return code(base32Decode(secret), epochSeconds / STEP_SECONDS);
    }

    /** 앞뒤 30초까지 허용 (휴대폰 시계가 조금 어긋나도 되게) */
    public static boolean verify(String secret, String code, long epochSeconds) {
        if (secret == null || code == null) return false;
        String c = code.replace(" ", "").strip();
        if (!c.matches("\\d{6}")) return false;
        byte[] key = base32Decode(secret);
        long counter = epochSeconds / STEP_SECONDS;
        for (long d = -1; d <= 1; d++) {
            if (constantTimeEquals(code(key, counter + d), c)) return true;
        }
        return false;
    }

    /** 인증 앱에 등록할 주소 (QR 코드 내용) */
    public static String uri(String issuer, String account, String secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String code(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(), b.getBytes());
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] base32Decode(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(in.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (char ch : in.toCharArray()) {
            int v = BASE32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("Base32가 아닌 글자: " + ch);
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }
}
