package it.caciotta.core.auth;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Sicurezza crittografica per il salvataggio e la verifica delle password con PBKDF2-HMAC-SHA256.
 */
public final class AuthSecurity {

    private static final int ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private AuthSecurity() {
    }

    /**
     * Genera un salt casuale sicuro a 16 byte codificato in Base64.
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_LENGTH];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Esegue l'hashing di una password con il salt dato usando PBKDF2WithHmacSHA256.
     */
    public static String hashPassword(String password, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH);
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Errore durante l'hashing della password", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * Verifica in tempo costante se la password fornita corrisponde all'hash memorizzato.
     */
    public static boolean verifyPassword(String candidatePassword, String storedHash, String storedSalt) {
        if (candidatePassword == null || storedHash == null || storedSalt == null) {
            return false;
        }
        String calculatedHash = hashPassword(candidatePassword, storedSalt);
        byte[] a = calculatedHash.getBytes();
        byte[] b = storedHash.getBytes();
        return MessageDigest.isEqual(a, b);
    }
}
