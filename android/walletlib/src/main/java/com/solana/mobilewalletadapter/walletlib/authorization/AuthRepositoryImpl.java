/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.walletlib.scenario.AuthorizedAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// TODO: record package name (when available) and purge AuthRepository on uninstall

public class AuthRepositoryImpl implements AuthRepository {
    private static final String TAG = AuthRepositoryImpl.class.getSimpleName();

    private static final String AUTH_TOKEN_CONTENT_TYPE = "typ";
    private static final String AUTH_TOKEN_CONTENT_TYPE_SUFFIX = "-auth-token";
    private static final String AUTH_TOKEN_IDENTITY_ID = "iid";
    private static final String AUTH_TOKEN_TOKEN_ID = "tid";

    private static final int AUTH_TOKEN_HMAC_LENGTH_BYTES = 32;

    @NonNull
    private final Context mContext;

    @NonNull
    private final AuthIssuerConfig mAuthIssuerConfig;

    private boolean mInitialized = false;
    private SecretKey mSecretKey;
    private AuthDatabase mAuthDb;
    private IdentityRecordDao mIdentityRecordDao;
    private AuthorizationsDao mAuthorizationsDao;
    private WalletUriBaseDao mWalletUriBaseDao;
    @Deprecated
    private PublicKeysDao mPublicKeysDao;
    private AccountRecordsDao mAccountsDao;

    public AuthRepositoryImpl(@NonNull Context context, @NonNull AuthIssuerConfig authIssuerConfig) {
        mContext = context;
        mAuthIssuerConfig = authIssuerConfig;
    }

    @Override
    public synchronized void start() {
        Log.v(TAG, "Starting AuthRepository");
        ensureStarted();
    }

    @Override
    public synchronized void stop() {
        Log.v(TAG, "Stopping AuthRepository");
        mAuthDb.close();
    }

    @GuardedBy("this")
    private void ensureStarted() {
        if (!mInitialized) {
            mSecretKey = getSecretKey();
            if (mSecretKey == null) {
                Log.d(TAG, "No secret key found, recreating secret key and database");
                mSecretKey = createSecretKey();
                SQLiteDatabase.deleteDatabase(
                        mContext.getDatabasePath(AuthDatabase.getDatabaseName(mAuthIssuerConfig)));
            }

            mAuthDb = new AuthDatabase(mContext, mAuthIssuerConfig);
            final SQLiteDatabase database = mAuthDb.getWritableDatabase();
            mIdentityRecordDao = new IdentityRecordDao(database);
            mAuthorizationsDao = new AuthorizationsDao(database, mAuthIssuerConfig);
            mWalletUriBaseDao = new WalletUriBaseDao(database);
            mPublicKeysDao = new PublicKeysDao(database);
            mAccountsDao = new AccountRecordsDao(database);
            mInitialized = true;
        }
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
    @Override
    public synchronized AuthRecord fromAuthToken(@NonNull String authToken) {
        ensureStarted();

        final byte[] payload = Base64.decode(authToken, Base64.DEFAULT);
        if (payload.length < AUTH_TOKEN_HMAC_LENGTH_BYTES) {
            Log.w(TAG, "Invalid auth token");
            return null;
        }
        final JSONObject o;
        try {
            o = new JSONObject(
                    new String(payload, 0, payload.length - AUTH_TOKEN_HMAC_LENGTH_BYTES));
        } catch (JSONException e) {
            Log.w(TAG, "Auth token is not a JSON object", e);
            return null;
        }
        final String contentType;
        final String identityIdStr;
        final String tokenIdStr;
        try {
            contentType = o.getString(AUTH_TOKEN_CONTENT_TYPE);
            identityIdStr = o.getString(AUTH_TOKEN_IDENTITY_ID);
            tokenIdStr = o.getString(AUTH_TOKEN_TOKEN_ID);
        } catch (JSONException e) {
            Log.w(TAG, "Auth token does not contain expected fields", e);
            return null;
        }

        final String expectedContentType = getAuthTokenContentType();
        if (!expectedContentType.equals(contentType)) {
            Log.w(TAG, "Content type is incorrect: expected=" + expectedContentType +
                    ", actual=" + contentType);
            return null;
        }

        // Look up the identity secret key for the key specified in this JWT
        final IdentityRecord identityRecord = mIdentityRecordDao.findIdentityById(identityIdStr);
        if (identityRecord == null) {
            Log.w(TAG, "Identity not found: " + identityIdStr);
            return null;
        }

        // Verify the HMAC on the auth token
        final SecretKeySpec identityKey = decryptHmacSha256SecretKey(
                identityRecord.getSecretKeyCiphertext(), identityRecord.getSecretKeyIV());
        final boolean verified;
        try {
            final Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(identityKey);
            hmac.update(payload, 0, payload.length - AUTH_TOKEN_HMAC_LENGTH_BYTES);
            final byte[] decodedHmac = hmac.doFinal();
            final byte[] payloadHmac = Arrays.copyOfRange(
                    payload, payload.length - AUTH_TOKEN_HMAC_LENGTH_BYTES, payload.length);
            verified = Arrays.equals(decodedHmac, payloadHmac);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            Log.w(TAG, "Failed performing HMAC on the auth token buffer", e);
            return null;
        }
        if (!verified) {
            Log.w(TAG, "Auth token HMAC does not match");
            return null;
        }

        // Create an AuthRecord for the auth token
        final AuthRecord authRecord = mAuthorizationsDao.getAuthorization(identityRecord, tokenIdStr);
        if (authRecord == null) {
            Log.w(TAG, "Auth token has been revoked, or has expired and been purged");
            return null;
        }

        // Revoke this authorization if it is either from the future, or too old to be reissuable
        if (revokeNonReissuableAuthRecord(authRecord)) {
            return null;
        }

        Log.v(TAG, "Returning AuthRecord from auth token: " + authRecord);

        return authRecord;
    }

    @NonNull
    @Override
    public synchronized String toAuthToken(@NonNull AuthRecord authRecord) {
        assert (!authRecord.isRevoked());
        if (authRecord.isRevoked()) {
            // Don't fail here if asserts are not enabled. Returning an invalid auth token is better
            // than crashing the app.
            Log.e(TAG, "Issuing auth record for revoked auth token");
        }

        final JSONObject o = new JSONObject();
        try {
            o.put(AUTH_TOKEN_CONTENT_TYPE, getAuthTokenContentType());
            o.put(AUTH_TOKEN_IDENTITY_ID, Integer.toString(authRecord.identity.getId()));
            o.put(AUTH_TOKEN_TOKEN_ID, Integer.toString(authRecord.id));
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Error encoding auth record as JSON", e);
        }
        final byte[] payload = o.toString().getBytes(StandardCharsets.UTF_8);

        // To create an AuthRecord requires that the DB have been previously opened; we can thus
        // rely on mSecretKey being initialized and valid.
        final SecretKeySpec identityKey = decryptHmacSha256SecretKey(
                authRecord.identity.getSecretKeyCiphertext(), authRecord.identity.getSecretKeyIV());

        // Verify the HMAC on the auth token
        final byte[] payloadHmac;
        try {
            final Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(identityKey);
            payloadHmac = hmac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Error generating HMAC for auth token payload", e);
        }

        assert (payloadHmac.length == AUTH_TOKEN_HMAC_LENGTH_BYTES);
        final byte[] authToken = Arrays.copyOf(payload, payload.length + AUTH_TOKEN_HMAC_LENGTH_BYTES);
        System.arraycopy(payloadHmac, 0, authToken, payload.length, AUTH_TOKEN_HMAC_LENGTH_BYTES);

        Log.v(TAG, "Returning auth token for AuthRecord: " + authRecord);

        return Base64.encodeToString(authToken, Base64.NO_PADDING | Base64.NO_WRAP);
    }

    @NonNull
    @GuardedBy("this")
    private String getAuthTokenContentType() {
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

    @Deprecated
    @NonNull
    @Override
    public synchronized AuthRecord issue(@NonNull String name,
                                         @NonNull Uri uri,
                                         @NonNull Uri relativeIconUri,
                                         @NonNull byte[] publicKey,
                                         @Nullable String accountLabel,
                                         @NonNull String cluster,
                                         @Nullable Uri walletUriBase,
                                         @Nullable byte[] scope) {
        return issue(name, uri, relativeIconUri,
                new AuthorizedAccount(publicKey, accountLabel, null, null, null),
                cluster, walletUriBase, scope);
    }

    @Deprecated
    @NonNull
    @Override
    public AuthRecord issue(@NonNull String name,
                            @NonNull Uri uri,
                            @NonNull Uri relativeIconUri,
                            @NonNull AuthorizedAccount account,
                            @NonNull String cluster,
                            @Nullable Uri walletUriBase,
                            @Nullable byte[] scope) {
        return issue(name, uri, relativeIconUri,
                new AuthorizedAccount[]{ account },
                cluster, walletUriBase, scope);
    }

    @NonNull
    @Override
    public AuthRecord issue(@NonNull String name,
                            @NonNull Uri uri,
                            @NonNull Uri relativeIconUri,
                            @NonNull AuthorizedAccount[] accounts,
                            @NonNull String cluster,
                            @Nullable Uri walletUriBase,
                            @Nullable byte[] scope) {
        ensureStarted();

        if (scope == null) {
            scope = new byte[0];
        }

        // First, try and look up a matching identity
        IdentityRecord identityRecord = mIdentityRecordDao
                .findIdentityByParams(name, uri.toString(), relativeIconUri.toString());

        // If no matching identity exists, create one
        if (identityRecord == null) {
            Log.d(TAG, "Creating IdentityRecord for " + name + '/' + uri + '/' + relativeIconUri);

            final Pair<byte[], byte[]> p = createEncryptedHmacSha256SecretKey();
            final byte[] identityKeyCiphertext = p.first;
            final byte[] identityKeyIV = p.second;

            int identityId = (int) mIdentityRecordDao.insert(name, uri.toString(), 
                    relativeIconUri.toString(), identityKeyCiphertext, identityKeyIV);

            if (identityId >= 1) {
                identityRecord = new IdentityRecord.IdentityRecordBuilder()
                        .setId(identityId)
                        .setName(name)
                        .setUri(uri)
                        .setRelativeIconUri(relativeIconUri)
                        .setSecretKeyCiphertext(identityKeyCiphertext)
                        .setSecretKeyIV(identityKeyIV)
                        .build();
            } else {
                throw new SQLException("Error inserting IdentityRecord");
            }
        }

        // Next, try and look up the wallet URI base
        final WalletUri walletUri = mWalletUriBaseDao.getByUri(walletUriBase);

        final int walletUriBaseId;
        // If no matching wallet URI base exists, create one
        if (walletUri == null) {
            walletUriBaseId = (int) mWalletUriBaseDao.insert(walletUriBase);
        } else {
            walletUriBaseId = walletUri.id;
        }

        final long now = System.currentTimeMillis();

        final int authRecordId = (int) mAuthorizationsDao.insert(identityRecord.getId(), now, cluster, walletUriBaseId, scope);

        // If needed, purge oldest entries for this identity
        final int purgeCount = mAuthorizationsDao.purgeOldestEntries(identityRecord.getId());
        if (purgeCount > 0) {
            Log.v(TAG, "Purged " + purgeCount + " oldest authorizations for identity: " + identityRecord);
            // Note: we only purge if we exceeded the max outstanding authorizations per identity. We
            // thus know that the identity remains referenced; no need to purge unused identities.
            deleteUnreferencedAccounts();
            deleteUnreferencedWalletUriBase();
        }

        // Finally, try and look up the accounts
        final List<AccountRecord> accountRecords = new ArrayList<>();
        for (AuthorizedAccount account: accounts) {
            // create an account record for each account in this auth record
            final int accountId = (int) mAccountsDao.insert(authRecordId, account.publicKey,
                        account.accountLabel, account.accountIcon, account.chains, account.features);
            final AccountRecord accountRecord = new AccountRecord(accountId, authRecordId, account.publicKey,
                        account.accountLabel, account.accountIcon, account.chains, account.features);
            accountRecords.add(accountRecord);
        }

        return new AuthRecord(authRecordId, identityRecord, accountRecords.toArray(new AccountRecord[0]),
                cluster, scope, walletUriBase, walletUriBaseId, now,
                now + mAuthIssuerConfig.authorizationValidityMs);
    }

    @Nullable
    @Override
    public synchronized AuthRecord reissue(@NonNull AuthRecord authRecord) {
        ensureStarted();
        assert (!authRecord.isRevoked());

        final long now = System.currentTimeMillis();
        final long authRecordAgeMs = now - authRecord.issued;
        final AuthRecord reissued;
        if (authRecord.isRevoked()) {
            Log.e(TAG, "Attempt to reissue a revoked auth record: " + authRecord);
            reissued = null;
        } else if (revokeNonReissuableAuthRecord(authRecord)) {
            reissued = null;
        } else if (authRecordAgeMs < mAuthIssuerConfig.reauthorizationNopDurationMs) {
            Log.d(TAG, "AuthRecord still valid; reissuing same AuthRecord: " + authRecord);
            reissued = authRecord;
        } else {
            final int id = (int) mAuthorizationsDao.insert(authRecord.identity.getId(), now,
                    authRecord.chain, authRecord.walletUriBaseId, authRecord.scope);
            mAccountsDao.updateParentId(authRecord.id, id);
            reissued = new AuthRecord(id, authRecord.identity, authRecord.accounts,
                    authRecord.chain, authRecord.scope, authRecord.walletUriBase,
                    authRecord.walletUriBaseId, now,
                    now + mAuthIssuerConfig.authorizationValidityMs);
            Log.d(TAG, "Reissued AuthRecord: " + reissued);
            revoke(authRecord);
            // Note: reissue is net-neutral on the number of authorizations per identity, so there's
            // no need to check that we have not exceeded the authorization limit here.
        }

        return reissued;
    }

    @Override
    public synchronized boolean revoke(@NonNull AuthRecord authRecord) {
        ensureStarted();

        Log.d(TAG, "Revoking AuthRecord " + authRecord);
        authRecord.setRevoked();

        final int deleteCount = mAuthorizationsDao.deleteByAuthRecordId(authRecord.id);

        // There may now be unreferenced authorization data; if so, delete them
        deleteUnreferencedIdentities();
        deleteUnreferencedAccounts();
        deleteUnreferencedWalletUriBase();

        return (deleteCount != 0);
    }

    @Override
    public synchronized boolean revoke(@NonNull IdentityRecord identityRecord) {
        ensureStarted();

        Log.d(TAG, "Revoking IdentityRecord " + identityRecord + " and all related AuthRecords");

        mAuthorizationsDao.deleteByIdentityRecordId(identityRecord.getId());
        final int deleteCount = mIdentityRecordDao.deleteById(identityRecord.getId());

        // There may now be unreferenced authorization data; if so, delete them
        deleteUnreferencedAccounts();
        deleteUnreferencedWalletUriBase();

        return (deleteCount != 0);
    }

    @GuardedBy("this")
    private void deleteUnreferencedIdentities() {
        mIdentityRecordDao.deleteUnreferencedIdentities();
    }

    @GuardedBy("this")
    private void deleteUnreferencedAccounts() {
        mAccountsDao.deleteUnreferencedAccounts();
    }

    @GuardedBy("this")
    private void deleteUnreferencedWalletUriBase() {
        mWalletUriBaseDao.deleteUnreferencedWalletUriBase();
    }

    @NonNull
    @Override
    public synchronized List<IdentityRecord> getAuthorizedIdentities() {
        ensureStarted();
        return mIdentityRecordDao.getAuthorizedIdentities();
    }

    @NonNull
    @Override
    public synchronized List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord) {
        ensureStarted();
        return mAuthorizationsDao.getAuthorizations(identityRecord);
    }

    @NonNull
    @GuardedBy("this")
    private Pair<byte[], byte[]> createEncryptedHmacSha256SecretKey() {
        final SecureRandom sr = new SecureRandom();
        final byte[] hmacSHA256KeyBytes = new byte[32];
        sr.nextBytes(hmacSHA256KeyBytes);

        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, mSecretKey, sr);
            final byte[] ciphertext = cipher.doFinal(hmacSHA256KeyBytes);
            final byte[] iv = cipher.getIV();
            return new Pair<>(ciphertext, iv);
        } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalBlockSizeException |
                BadPaddingException | NoSuchPaddingException e) {
            throw new RuntimeException("Error while encrypting the identity key", e);
        }
    }

    @NonNull
    @GuardedBy("this")
    private SecretKeySpec decryptHmacSha256SecretKey(@NonNull byte[] keyCiphertext,
                                                     @NonNull byte[] keyIV) {
        try {
            final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            final GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, keyIV);
            cipher.init(Cipher.DECRYPT_MODE, mSecretKey, gcmParameterSpec);
            final byte[] keyBytes = cipher.doFinal(keyCiphertext);
            return new SecretKeySpec(keyBytes, "HmacSHA256");
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                InvalidAlgorithmParameterException | InvalidKeyException | BadPaddingException e) {
            throw new RuntimeException("Error while decrypting identity key", e);
        }
    }
}
