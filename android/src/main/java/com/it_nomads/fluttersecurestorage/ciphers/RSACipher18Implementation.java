package com.it_nomads.fluttersecurestorage.ciphers;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.StrongBoxUnavailableException;
import android.util.Log;

import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.security.auth.x500.X500Principal;

import static android.content.Context.KEYGUARD_SERVICE;

class RSACipher18Implementation {

    private final String KEY_ALIAS;
    private static final String KEYSTORE_PROVIDER_ANDROID = "AndroidKeyStore";
    private static final String TYPE_RSA = "RSA";


    public RSACipher18Implementation(Context context) throws Exception {
        KEY_ALIAS = context.getPackageName() + ".FlutterSecureStoragePluginKey";
        createRSAKeysIfNeeded(context);
    }

    public byte[] wrap(Key key) throws Exception {
        PublicKey publicKey = getPublicKey();
        Cipher cipher = getRSACipher();
        cipher.init(Cipher.WRAP_MODE, publicKey);

        return cipher.wrap(key);
    }

    public Key unwrap(byte[] wrappedKey, String algorithm) throws Exception {
        PrivateKey privateKey = getPrivateKey();
        Cipher cipher = getRSACipher();
        cipher.init(Cipher.UNWRAP_MODE, privateKey);

        return cipher.unwrap(wrappedKey, algorithm, Cipher.SECRET_KEY);
    }

    public byte[] encrypt(byte[] input) throws Exception {
        PublicKey publicKey = getPublicKey();
        Cipher cipher = getRSACipher();
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);

        return cipher.doFinal(input);
    }

    public byte[] decrypt(byte[] input) throws Exception {
        PrivateKey privateKey = getPrivateKey();
        Cipher cipher = getRSACipher();
        cipher.init(Cipher.DECRYPT_MODE, privateKey);

        return cipher.doFinal(input);
    }

    private PrivateKey getPrivateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID);
        ks.load(null);

        Key key = ks.getKey(KEY_ALIAS, null);
        if (key == null) {
            throw new Exception("No key found under alias: " + KEY_ALIAS);
        }

        if (!(key instanceof PrivateKey)) {
            throw new Exception("Not an instance of a PrivateKey");
        }

        return (PrivateKey) key;
    }

    private PublicKey getPublicKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID);
        ks.load(null);

        Certificate cert = ks.getCertificate(KEY_ALIAS);
        if (cert == null) {
            throw new Exception("No certificate found under alias: " + KEY_ALIAS);
        }

        PublicKey key = cert.getPublicKey();
        if (key == null) {
            throw new Exception("No key found under alias: " + KEY_ALIAS);
        }

        return key;
    }

    private Cipher getRSACipher() throws Exception {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidOpenSSL"); // error in android 6: InvalidKeyException: Need RSA private or public key
        } else {
            return Cipher.getInstance("RSA/ECB/PKCS1Padding", "AndroidKeyStoreBCWorkaround"); // error in android 5: NoSuchProviderException: Provider not available: AndroidKeyStoreBCWorkaround
        }
    }

    private void createRSAKeysIfNeeded(Context context) throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE_PROVIDER_ANDROID);
        ks.load(null);

        KeyStore.PrivateKeyEntry entry = (KeyStore.PrivateKeyEntry)ks.getEntry(KEY_ALIAS, null);
        if (entry != null) {
            Key privateKey = entry.getPrivateKey();
            if (privateKey == null) {
                createKeys(context);
            }
        } else {
            createKeys(context);
        }
    }

    private void createKeys(Context context) throws Exception {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        KeyguardManager mgr = (KeyguardManager) context.getSystemService(KEYGUARD_SERVICE);
        end.add(Calendar.YEAR, 25);

        KeyPairGenerator kpGenerator = KeyPairGenerator.getInstance(TYPE_RSA, KEYSTORE_PROVIDER_ANDROID);

        AlgorithmParameterSpec spec;

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            spec = new android.security.KeyPairGeneratorSpec.Builder(context)
                    .setAlias(KEY_ALIAS)
                    .setSubject(new X500Principal("CN=" + KEY_ALIAS))
                    .setSerialNumber(BigInteger.valueOf(1))
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
        } else {
            KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                    .setCertificateSubject(new X500Principal("CN=" + KEY_ALIAS))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                    .setCertificateSerialNumber(BigInteger.valueOf(1))
                    .setCertificateNotBefore(start.getTime())
                    .setUserAuthenticationRequired(mgr.isDeviceSecure())
                    .setCertificateNotAfter(end.getTime());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(true);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setInvalidatedByBiometricEnrollment(false);
            }

            spec = builder.build();
        }
        try {
            kpGenerator.initialize(spec);
            kpGenerator.generateKeyPair();
        } catch (Exception se) {
            Log.e("fluttersecurestorage", "An error occurred when trying to generate a StrongBoxSecurityKey: " + se.getMessage());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (se instanceof StrongBoxUnavailableException) {
                    Log.i("fluttersecurestorage", "StrongBox is unavailable on this device");
                    KeyGenParameterSpec.Builder builder = new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_DECRYPT | KeyProperties.PURPOSE_ENCRYPT)
                            .setCertificateSubject(new X500Principal("CN=" + KEY_ALIAS))
                            .setDigests(KeyProperties.DIGEST_SHA256)
                            .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1)
                            .setCertificateSerialNumber(BigInteger.valueOf(1))
                            .setCertificateNotBefore(start.getTime())
                            .setCertificateNotAfter(end.getTime())
                            .setUserAuthenticationRequired(mgr.isDeviceSecure())
                            .setInvalidatedByBiometricEnrollment(false);
                    spec = builder.build();
                    kpGenerator.initialize(spec);
                    kpGenerator.generateKeyPair();
                }
            }
        }
    }

}
