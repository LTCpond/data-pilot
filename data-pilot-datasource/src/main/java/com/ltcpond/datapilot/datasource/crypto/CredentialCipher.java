package com.ltcpond.datapilot.datasource.crypto;

/** 对持久化数据库密码进行可逆加密。 */
public interface CredentialCipher {

    /** 加密明文凭据，返回可持久化的版本化密文。 */
    String encrypt(String plaintext);

    /** 解密持久化凭据，失败时抛出运行时异常而不返回半成品。 */
    String decrypt(String ciphertext);
}
