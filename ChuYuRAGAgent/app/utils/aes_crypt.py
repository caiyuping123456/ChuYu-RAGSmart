import base64
import os
from Crypto.Cipher import AES
from Crypto.Util.Padding import unpad, pad
from dotenv import load_dotenv

load_dotenv()

# 从环境变量加载字符串
KEY = os.getenv("AES_KEY")
IV = os.getenv("AES_IV")


def decrypt_java_aes(encrypted_text_base64: str) -> str:
    """解密由 Java 加密的 Base64 密文"""
    encrypted_bytes = base64.b64decode(encrypted_text_base64)

    # 关键：将字符串 KEY/IV 转为 UTF-8 字节，与 Java 保持一致
    cipher = AES.new(KEY.encode('utf-8'), AES.MODE_CBC, IV.encode('utf-8'))

    decrypted_padded = cipher.decrypt(encrypted_bytes)
    decrypted_text = unpad(decrypted_padded, AES.block_size)
    return decrypted_text.decode('utf-8')


def encrypt_java_aes(plain_text: str) -> str:
    """加密明文，生成与 Java 兼容的 Base64 密文"""
    plain_bytes = plain_text.encode('utf-8')
    padded_plain = pad(plain_bytes, AES.block_size)

    # 关键：将字符串 KEY/IV 转为 UTF-8 字节，与 Java 保持一致
    cipher = AES.new(KEY.encode('utf-8'), AES.MODE_CBC, IV.encode('utf-8'))

    encrypted_bytes = cipher.encrypt(padded_plain)
    return base64.b64encode(encrypted_bytes).decode('utf-8')