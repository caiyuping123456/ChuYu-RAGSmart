package com.yizhaoqi.smartpai.utils;

import com.yizhaoqi.smartpai.config.DbEncryptConfig;
import jakarta.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.util.encoders.UTF8;
import org.springframework.stereotype.Component;

/**
 * @author caiyuping
 * @date 2026/5/8 14:21
 * @description: 这个是使用AES的加密方法
 */
@Component
public class DbEncryptUtil {

    private final DbEncryptConfig dbEncryptConfig;

    public DbEncryptUtil(DbEncryptConfig dbEncryptConfig) {
        this.dbEncryptConfig = dbEncryptConfig;
    }

    /**
     * 这个是ASE加密方法
     * @param data
     * @return
     */
    public String StringEncrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(
                dbEncryptConfig.getAES_KEY().getBytes(StandardCharsets.UTF_8),
                "AES"
            );
            IvParameterSpec ivSpec = new IvParameterSpec(
                dbEncryptConfig.getAES_IV().getBytes(StandardCharsets.UTF_8)
            );

            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(
                data.getBytes(StandardCharsets.UTF_8)
            );

            // Java 加密后通常转 Base64 字符串传输
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (
            InvalidAlgorithmParameterException
            | NoSuchPaddingException
            | IllegalBlockSizeException
            | NoSuchAlgorithmException
            | BadPaddingException
            | InvalidKeyException e
        ) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 这个是AES解密方法
     * @param data
     * @return
     */
    public String StringDecrypt(String data) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(
                dbEncryptConfig.getAES_KEY().getBytes(StandardCharsets.UTF_8),
                "AES"
            );
            IvParameterSpec ivSpec = new IvParameterSpec(
                dbEncryptConfig.getAES_IV().getBytes(StandardCharsets.UTF_8)
            );
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decode = Base64.getDecoder().decode(data);
            byte[] encode = cipher.doFinal(decode);
            return new String(encode, StandardCharsets.UTF_8);
        } catch (
            InvalidAlgorithmParameterException
            | NoSuchPaddingException
            | IllegalBlockSizeException
            | NoSuchAlgorithmException
            | BadPaddingException
            | InvalidKeyException e
        ) {
            throw new RuntimeException(e);
        }
    }
}
