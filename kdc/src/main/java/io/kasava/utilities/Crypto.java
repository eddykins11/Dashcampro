package io.kasava.utilities;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

/**
 * Utility class that encrypts or decrypts a file.
 * @author Kasava
 *
 */
public class Crypto {
    private static String TAG = "Crypto";

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES";

    public static void encrypt(String key, File inputFile, File outputFile) {
        Log.d(TAG, "Encrypting " + inputFile.getAbsolutePath());
        doCrypto(Cipher.ENCRYPT_MODE, key, inputFile, outputFile);
    }

    public static void decrypt(String key, File inputFile, File outputFile) {
        Log.d(TAG, "Decrypting " + inputFile.getAbsolutePath());
        doCrypto(Cipher.DECRYPT_MODE, key, inputFile, outputFile);
    }

    private static void doCrypto(int cipherMode, String key, File inputFile, File outputFile) {
        try {
            int read;
            byte[] buffer = new byte[4096];

            Key secretKey = new SecretKeySpec(key.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(cipherMode, secretKey);

            FileInputStream inputStream = new FileInputStream(inputFile);
            FileOutputStream outputStream = new FileOutputStream(outputFile);

            CipherInputStream cis = new CipherInputStream(inputStream, cipher);

            while ((read = cis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            outputStream.close();
            cis.close();

        } catch (NoSuchPaddingException | NoSuchAlgorithmException
                | InvalidKeyException | IOException ex) {
            Log.e(TAG, ex.getMessage());
        }
    }
}