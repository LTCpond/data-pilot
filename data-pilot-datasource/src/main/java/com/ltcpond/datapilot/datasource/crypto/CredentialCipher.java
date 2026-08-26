package com.ltcpond.datapilot.datasource.crypto;

/** 对持久化数据库密码进行可逆加密。 */
public interface CredentialCipher {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);
}
