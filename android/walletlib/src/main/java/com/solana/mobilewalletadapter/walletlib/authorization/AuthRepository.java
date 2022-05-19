package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.solana.mobilewalletadapter.common.protocol.PrivilegedMethod;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class AuthRepository {
    private static final String TAG = AuthRepository.class.getSimpleName();

    // N.B. These bitmask values represent a contract for the JWTs produced by this class. Changing
    // existing values will break compatibility with issued tokens.
    private static final String PRIVILEGED_METHOD_CLAIM = "prm";
    private static final int PRIVILEGED_METHOD_SIGN_TRANSACTION = 1;
    private static final int PRIVILEGED_METHOD_SIGN_MESSAGE = 2;
    private static final int PRIVILEGED_METHOD_SIGN_AND_SEND_TRANSACTION = 4;

    private static final String AUTH_TOKEN_CONTENT_TYPE_SUFFIX = "-auth-token";

    @NonNull
    private final Context mContext;

    @NonNull
    private final AuthIssuerConfig mAuthIssuerConfig;

    private boolean mInitialized = false;
    private SecretKey mSecretKey;
    private AuthDatabase mAuthDb;

    public AuthRepository(@NonNull Context context, @NonNull AuthIssuerConfig authIssuerConfig) {
        mContext = context;
        mAuthIssuerConfig = authIssuerConfig;
    }

    public synchronized void start() {
        Log.v(TAG, "Starting AuthRepository");
        ensureStarted();
    }

    public synchronized void stop() {
        Log.v(TAG, "Stopping AuthRepository");
        mAuthDb.close();
    }

    @NonNull
    @GuardedBy("this")
    private SQLiteDatabase ensureStarted() {
        if (!mInitialized) {
            mSecretKey = getSecretKey();
            if (mSecretKey == null) {
                Log.d(TAG, "No secret key found, recreating secret key and database");
                mSecretKey = createSecretKey();
                SQLiteDatabase.deleteDatabase(
                        mContext.getDatabasePath(AuthDatabase.getDatabaseName(mAuthIssuerConfig)));
            }

            mAuthDb = new AuthDatabase(mContext, mAuthIssuerConfig);
            mInitialized = true;
        }

        return mAuthDb.getWritableDatabase();
    }

    // Note: only uses final mAuthIssuerConfig, does not require any locks
    @Nullable
    private SecretKey getSecretKey() {
        try {
            final KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            return (SecretKey) ks.getKey(AuthDatabase.getDatabaseName(mAuthIssuerConfig), null);
        } catch (KeyStoreException | NoSuchAlgorithmException | UnrecoverableKeyException |
                CertificateException | IOException e) {
            throw new RuntimeException("Android keystore error; aborting", e);
        }
    }

    // Note: only uses final mAuthIssuerConfig, does not require any locks
    @NonNull
    private SecretKey createSecretKey() {
        try {
            final KeyGenerator kg = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(
                    AuthDatabase.getDatabaseName(mAuthIssuerConfig),
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return kg.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException("Android keystore error; aborting", e);
        }
    }

    @Nullable
    public synchronized AuthRecord fromAuthToken(@NonNull String authToken) {
        final SQLiteDatabase db = ensureStarted();

        final EncryptedJWT jwt;
        try {
            jwt = EncryptedJWT.parse(authToken);
        } catch (ParseException e) {
            Log.w(TAG, "AuthToken could not be parsed as a JWE-wrapped JWT", e);
            return null;
        }

        final String expectedContentType = getJWTContentType();
        final String contentType = jwt.getHeader().getContentType();
        if (!expectedContentType.equals(contentType)) {
            Log.w(TAG, "JWT content type is incorrect: expected=" + expectedContentType +
                    ", actual=" + contentType);
            return null;
        }

        // Look up the identity secret key for the key specified in this JWT
        final String identityId = jwt.getHeader().getKeyID();
        final IdentityRecord identityRecord;
        try (final Cursor c = db.query(AuthDatabase.TABLE_IDENTITIES,
                new String[] { AuthDatabase.COLUMN_IDENTITIES_ID,
                        AuthDatabase.COLUMN_IDENTITIES_NAME,
                        AuthDatabase.COLUMN_IDENTITIES_URI,
                        AuthDatabase.COLUMN_IDENTITIES_ICON_RELATIVE_URI,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY_IV },
                AuthDatabase.COLUMN_IDENTITIES_ID + "=?",
                new String[] { identityId },
                null,
                null,
                null)) {
            if (!c.moveToNext()) {
                Log.w(TAG, "Identity not found: " + identityId);
                return null;
            }

            final int id = c.getInt(0);
            final String name = c.getString(1);
            final String uri = c.getString(2);
            final String iconRelativeUri = c.getString(3);
            final byte[] keyCiphertext = c.getBlob(4);
            final byte[] keyIV = c.getBlob(5);
            identityRecord = new IdentityRecord(id, name, Uri.parse(uri),
                    Uri.parse(iconRelativeUri), keyCiphertext, keyIV);
        }

        // Decrypt the JWT
        final SecretKeySpec secretKey = decryptAES128SecretKey(
                identityRecord.secretKeyCiphertext, identityRecord.secretKeyIV);
        try {
            jwt.decrypt(new DirectDecrypter(secretKey));
        } catch (JOSEException e) {
            Log.w(TAG, "Failed decrypting JWT; it does not belong to this identity", e);
            return null;
        }

        // Create an AuthRecord from the claims object
        final JWTClaimsSet claims;
        try {
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            Log.w(TAG, "Failed decoding JWT payload as a claims set", e);
            return null;
        }
        final long issued = claims.getIssueTime().getTime();
        final ArraySet<PrivilegedMethod> privilegedMethods;
        try {
            privilegedMethods = bitmaskToPrivilegedMethods(claims.getIntegerClaim(PRIVILEGED_METHOD_CLAIM));
        } catch (ParseException | IllegalArgumentException e) {
            Log.w(TAG, "Failed processing the privileged methods claim in the JWT", e);
            return null;
        }
        final AuthRecord authRecord = new AuthRecord(
                Integer.parseInt(claims.getJWTID(), 10),
                identityRecord,
                privilegedMethods,
                issued,
                issued + mAuthIssuerConfig.authorizationValidityMs);

        // Check that this authorization hasn't been revoked or purged
        try (final Cursor c = db.query(AuthDatabase.TABLE_AUTHORIZATIONS,
                new String[] { AuthDatabase.COLUMN_AUTHORIZATIONS_ID },
                AuthDatabase.COLUMN_AUTHORIZATIONS_ID + "=?",
                new String[] { Integer.toString(authRecord.id) },
                null,
                null,
                null,
                "1")) {
            if (!c.moveToNext()) {
                Log.w(TAG, "Auth token has been revoked, or has expired and been purged");
                return null;
            }
        }

        // Revoke this authorization if it is either from the future, or too old to be reissued
        if (revokeNonReissuableAuthRecord(authRecord)) {
            return null;
        }

        Log.v(TAG, "Returning AuthRecord from auth token: " + authRecord);

        return authRecord;
    }

    @NonNull
    public synchronized String toAuthToken(@NonNull AuthRecord authRecord) {
        // To create an AuthRecord requires that the DB have been previously opened; we can thus
        // rely on mSecretKey being initialized and valid.
        final SecretKeySpec identityKey = decryptAES128SecretKey(
                authRecord.identity.secretKeyCiphertext, authRecord.identity.secretKeyIV);

        final int privilegedMethodsMask = privilegedMethodsToBitmask(authRecord.privilegedMethods);
        final JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(Integer.toString(authRecord.id))
                .claim(PRIVILEGED_METHOD_CLAIM, privilegedMethodsMask)
                .issueTime(new Date(authRecord.issued))
                .build();
        final JWEHeader header = new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A128GCM)
                .contentType(getJWTContentType())
                .keyID(Integer.toString(authRecord.identity.id))
                .build();
        final EncryptedJWT jwt = new EncryptedJWT(header, claims);
        try {
            jwt.encrypt(new DirectEncrypter(identityKey));
        } catch (JOSEException e) {
            throw new RuntimeException("Error encrypting JWT", e);
        }

        Log.v(TAG, "Returning auth token for AuthRecord: " + authRecord);

        return jwt.serialize();
    }

    @NonNull
    @GuardedBy("this")
    private String getJWTContentType() {
        return mAuthIssuerConfig.name + AUTH_TOKEN_CONTENT_TYPE_SUFFIX;
    }

    @GuardedBy("this")
    private boolean revokeNonReissuableAuthRecord(@NonNull AuthRecord authRecord) {
        final long now = System.currentTimeMillis();
        final long authRecordAgeMs = now - authRecord.issued;

        boolean revoke = false;
        if (authRecordAgeMs < 0) {
            Log.w(TAG, "AuthRecord issued in the future; revoking");
            revoke = true;
        } else if (authRecordAgeMs > mAuthIssuerConfig.reauthorizationValidityMs) {
            Log.w(TAG, "AuthRecord beyond reissue validity duration; revoking");
            revoke = true;
        }

        if (revoke) {
            revoke(authRecord);
        }

        return revoke;
    }

    @NonNull
    public synchronized AuthRecord issue(@NonNull String name,
                                         @NonNull Uri uri,
                                         @NonNull Uri relativeIconUri,
                                         @NonNull Set<PrivilegedMethod> privilegedMethods) {
        final SQLiteDatabase db = ensureStarted();

        int identityId = -1;
        byte[] identityKeyCiphertext = null;
        byte[] identityKeyIV = null;
        try (final Cursor c = db.query(AuthDatabase.TABLE_IDENTITIES,
                new String[] { AuthDatabase.COLUMN_IDENTITIES_ID,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY_IV },
                AuthDatabase.COLUMN_IDENTITIES_NAME + "=? AND " +
                        AuthDatabase.COLUMN_IDENTITIES_URI + "=? AND " +
                        AuthDatabase.COLUMN_IDENTITIES_ICON_RELATIVE_URI + "=?",
                new String[] { name, uri.toString(), relativeIconUri.toString() },
                null,
                null,
                null)) {
            if (c.moveToNext()) {
                identityId = c.getInt(0);
                identityKeyCiphertext = c.getBlob(1);
                identityKeyIV = c.getBlob(2);
            }
        }

        if (identityId == -1) {
            Log.d(TAG, "Creating IdentityRecord for " + name + '/' + uri + '/' + relativeIconUri);

            final Pair<byte[], byte[]> p = createEncryptedAES128SecretKey();
            identityKeyCiphertext = p.first;
            identityKeyIV = p.second;

            final ContentValues identityContentValues = new ContentValues(5);
            identityContentValues.put(AuthDatabase.COLUMN_IDENTITIES_NAME, name);
            identityContentValues.put(AuthDatabase.COLUMN_IDENTITIES_URI, uri.toString());
            identityContentValues.put(AuthDatabase.COLUMN_IDENTITIES_ICON_RELATIVE_URI, relativeIconUri.toString());
            identityContentValues.put(AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY, identityKeyCiphertext);
            identityContentValues.put(AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY_IV, identityKeyIV);
            identityId = (int) db.insert(AuthDatabase.TABLE_IDENTITIES, null, identityContentValues);
        }

        final IdentityRecord identityRecord = new IdentityRecord(identityId, name, uri,
                relativeIconUri, identityKeyCiphertext, identityKeyIV);

        final long now = System.currentTimeMillis();

        final ContentValues authContentValues = new ContentValues(2);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID, identityRecord.id);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED, now);
        final int id = (int) db.insert(AuthDatabase.TABLE_AUTHORIZATIONS, null, authContentValues);

        // If needed, purge oldest entries for this identity
        final SQLiteStatement purgeOldestStatement = db.compileStatement(
                "DELETE FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                " WHERE " + AuthDatabase.COLUMN_AUTHORIZATIONS_ID + " IN " +
                "(SELECT " + AuthDatabase.COLUMN_AUTHORIZATIONS_ID +
                " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                " WHERE " + AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?" +
                " ORDER BY " + AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED +
                " DESC LIMIT -1 OFFSET ?)");
        purgeOldestStatement.bindLong(1, identityRecord.id);
        purgeOldestStatement.bindLong(2, mAuthIssuerConfig.maxOutstandingTokensPerIdentity);
        final int purgeCount = purgeOldestStatement.executeUpdateDelete();
        if (purgeCount > 0) {
            Log.v(TAG, "Purged " + purgeCount + " oldest authorizations for identity: " + identityRecord);
        }
        // Note: we only purge if we exceeded the max outstanding authorizations per identity. We
        // thus know that the identity remains referenced; no need to purge unused identities.

        return new AuthRecord(id, identityRecord, privilegedMethods,
                now, now + mAuthIssuerConfig.authorizationValidityMs);
    }

    @Nullable
    public synchronized AuthRecord reissue(@NonNull AuthRecord authRecord) {
        final SQLiteDatabase db = ensureStarted();

        final long now = System.currentTimeMillis();
        final long authRecordAgeMs = authRecord.issued - now;
        final AuthRecord reissued;
        if (revokeNonReissuableAuthRecord(authRecord)) {
            reissued = null;
        } else if (authRecordAgeMs < mAuthIssuerConfig.reauthorizationNopDurationMs) {
            Log.d(TAG, "AuthRecord still valid; reissuing same AuthRecord: " + authRecord);
            reissued = authRecord;
        } else {
            final ContentValues reissueContentValues = new ContentValues(2);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID, authRecord.identity.id);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED, now);
            final int id = (int) db.insert(AuthDatabase.TABLE_AUTHORIZATIONS, null, reissueContentValues);
            final ArraySet<PrivilegedMethod> privilegedMethods = new ArraySet<>(authRecord.privilegedMethods.size());
            privilegedMethods.addAll(authRecord.privilegedMethods);
            reissued = new AuthRecord(id, authRecord.identity, privilegedMethods, now, now + mAuthIssuerConfig.authorizationValidityMs);
            Log.d(TAG, "Reissued AuthRecord: " + reissued);
            revoke(authRecord);
            // Note: reissue is net-neutral on the number of authorizations per identity, so there's
            // no need to check that we have not exceeded the authorization limit here.
        }

        return reissued;
    }

    public synchronized boolean revoke(@NonNull AuthRecord authRecord) {
        final SQLiteDatabase db = ensureStarted();

        Log.d(TAG, "Revoking AuthRecord " + authRecord);

        final SQLiteStatement deleteAuthorizations = db
                .compileStatement(
                        "DELETE FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                        " WHERE " + AuthDatabase.COLUMN_AUTHORIZATIONS_ID + "=?");
        deleteAuthorizations.bindLong(1, authRecord.id);
        final int deleteCount = deleteAuthorizations.executeUpdateDelete();

        if (deleteCount != 0) {
            // There may now be unreferenced identities; if so, delete them
            final SQLiteStatement deleteUnreferencedIdentities = db.compileStatement(
                    "DELETE FROM " + AuthDatabase.TABLE_IDENTITIES +
                            " WHERE " + AuthDatabase.COLUMN_IDENTITIES_ID + " NOT IN " +
                            "(SELECT DISTINCT " + AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID +
                            " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS + ')');
            deleteUnreferencedIdentities.executeUpdateDelete();
        }

        return (deleteCount != 0);
    }

    public synchronized boolean revoke(@NonNull IdentityRecord identityRecord) {
        final SQLiteDatabase db = ensureStarted();

        Log.d(TAG, "Revoking IdentityRecord " + identityRecord + " and all related AuthRecords");

        final SQLiteStatement deleteAuthorizations = db
                .compileStatement("DELETE FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                        " WHERE " + AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?");
        deleteAuthorizations.bindLong(1, identityRecord.id);
        deleteAuthorizations.executeUpdateDelete();

        final SQLiteStatement deleteIdentity = db
                .compileStatement("DELETE FROM " + AuthDatabase.TABLE_IDENTITIES +
                        " WHERE " + AuthDatabase.COLUMN_IDENTITIES_ID + "=?");
        deleteIdentity.bindLong(1, identityRecord.id);
        final int deleteCount = deleteIdentity.executeUpdateDelete();

        return (deleteCount != 0);
    }

    @NonNull
    public synchronized List<IdentityRecord> getAuthorizedIdentities() {
        final SQLiteDatabase db = ensureStarted();

        final ArrayList<IdentityRecord> identities = new ArrayList<>();
        try (final Cursor c = db.query(AuthDatabase.TABLE_IDENTITIES,
                new String[] { AuthDatabase.COLUMN_IDENTITIES_ID,
                        AuthDatabase.COLUMN_IDENTITIES_NAME,
                        AuthDatabase.COLUMN_IDENTITIES_URI,
                        AuthDatabase.COLUMN_IDENTITIES_ICON_RELATIVE_URI,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY_IV },
                null,
                null,
                null,
                null,
                AuthDatabase.COLUMN_IDENTITIES_NAME)) {
            while (c.moveToNext()) {
                final int id = c.getInt(0);
                final String name = c.getString(1);
                final String uri = c.getString(2);
                final String iconRelativeUri = c.getString(3);
                final byte[] identityKeyCiphertext = c.getBlob(4);
                final byte[] identityKeyIV = c.getBlob(5);
                // Note: values should never be null, but protect against bugs and corruption
                final IdentityRecord identity = new IdentityRecord(id, name, Uri.parse(uri),
                        Uri.parse(iconRelativeUri), identityKeyCiphertext, identityKeyIV);
                identities.add(identity);
            }
        }
        return identities;
    }

    @NonNull
    @GuardedBy("this")
    private Pair<byte[], byte[]> createEncryptedAES128SecretKey() {
        final SecureRandom sr = new SecureRandom();
        final byte[] aes128KeyBytes = new byte[16];
        sr.nextBytes(aes128KeyBytes);

        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, mSecretKey, sr);
            final byte[] ciphertext = cipher.doFinal(aes128KeyBytes);
            final byte[] iv = cipher.getIV();
            return new Pair<>(ciphertext, iv);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException |
                BadPaddingException | NoSuchPaddingException e) {
            throw new RuntimeException("Error while encrypting the identity key", e);
        }
    }

    @NonNull
    @GuardedBy("this")
    private SecretKeySpec decryptAES128SecretKey(@NonNull byte[] keyCiphertext,
                                                 @NonNull byte[] keyIV) {
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, keyIV);
            cipher.init(Cipher.DECRYPT_MODE, mSecretKey, gcmParameterSpec);
            final byte[] keyBytes = cipher.doFinal(keyCiphertext);
            return new SecretKeySpec(keyBytes, KeyProperties.KEY_ALGORITHM_AES);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                InvalidAlgorithmParameterException |  InvalidKeyException | BadPaddingException e) {
            throw new RuntimeException("Error while decrypting identity key", e);
        }
    }

    private static int privilegedMethodsToBitmask(@NonNull Set<PrivilegedMethod> privilegedMethods) {
        int mask = 0;
        for (PrivilegedMethod pm : privilegedMethods) {
            switch (pm) {
                case SignTransaction:
                    mask |= PRIVILEGED_METHOD_SIGN_TRANSACTION;
                    break;
                case SignAndSendTransaction:
                    mask |= PRIVILEGED_METHOD_SIGN_MESSAGE;
                    break;
                case SignMessage:
                    mask |= PRIVILEGED_METHOD_SIGN_AND_SEND_TRANSACTION;
                    break;
                default:
                    throw new UnsupportedOperationException("Unknown privileged method type");
            }
        }
        return mask;
    }

    @NonNull
    private static ArraySet<PrivilegedMethod> bitmaskToPrivilegedMethods(int bitmask) {
        final ArraySet<PrivilegedMethod> privilegedMethods = new ArraySet<>(Integer.bitCount(bitmask));
        if ((bitmask & ~(PRIVILEGED_METHOD_SIGN_TRANSACTION |
                PRIVILEGED_METHOD_SIGN_MESSAGE |
                PRIVILEGED_METHOD_SIGN_AND_SEND_TRANSACTION)) != 0) {
            throw new IllegalArgumentException("Unsupported privileged method bits are set");
        }
        if ((bitmask & PRIVILEGED_METHOD_SIGN_TRANSACTION) != 0) {
            privilegedMethods.add(PrivilegedMethod.SignTransaction);
        }
        if ((bitmask & PRIVILEGED_METHOD_SIGN_MESSAGE) != 0) {
            privilegedMethods.add(PrivilegedMethod.SignMessage);
        }
        if ((bitmask & PRIVILEGED_METHOD_SIGN_AND_SEND_TRANSACTION) != 0) {
            privilegedMethods.add(PrivilegedMethod.SignAndSendTransaction);
        }
        return privilegedMethods;
    }
}
