package nl.metafactory.aicontrol.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Service;

@Service
public class CredentialEncryptionService {

    private final TextEncryptor encryptor;

    public CredentialEncryptionService(
            @Value("${metafactory.encryption.secret}") String secret,
            @Value("${metafactory.encryption.salt}") String salt) {
        this.encryptor = Encryptors.delux(secret, salt);
    }

    public String encrypt(String plaintext) {
        return encryptor.encrypt(plaintext);
    }

    public String decrypt(String ciphertext) {
        return encryptor.decrypt(ciphertext);
    }
}