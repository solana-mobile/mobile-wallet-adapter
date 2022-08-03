/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.authorization;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

public class AuthRepository {
    private static final String TAG = AuthRepository.class.getSimpleName();

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
        final IdentityRecord identityRecord;
        try (final Cursor c = db.query(AuthDatabase.TABLE_IDENTITIES,
                new String[] { AuthDatabase.COLUMN_IDENTITIES_ID,
                        AuthDatabase.COLUMN_IDENTITIES_NAME,
                        AuthDatabase.COLUMN_IDENTITIES_URI,
                        AuthDatabase.COLUMN_IDENTITIES_ICON_RELATIVE_URI,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY,
                        AuthDatabase.COLUMN_IDENTITIES_SECRET_KEY_IV },
                AuthDatabase.COLUMN_IDENTITIES_ID + "=?",
                new String[] { identityIdStr },
                null,
                null,
                null)) {
            if (!c.moveToNext()) {
                Log.w(TAG, "Identity not found: " + identityIdStr);
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

        // Verify the HMAC on the auth token
        final SecretKeySpec identityKey = decryptHmacSha256SecretKey(
                identityRecord.secretKeyCiphertext, identityRecord.secretKeyIV);
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
        final AuthRecord authRecord;
        try (final Cursor c = db.rawQuery("SELECT " +
                AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_SCOPE +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_CLUSTER +
                ", " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_RAW +
                ", " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_LABEL +
                ", " + AuthDatabase.TABLE_WALLET_URI_BASE + '.' + AuthDatabase.COLUMN_WALLET_URI_BASE_URI +
                " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                " INNER JOIN " + AuthDatabase.TABLE_PUBLIC_KEYS +
                " ON " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                " = " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_ID +
                " INNER JOIN " + AuthDatabase.TABLE_WALLET_URI_BASE +
                " ON " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                " = " + AuthDatabase.TABLE_WALLET_URI_BASE + '.' + AuthDatabase.COLUMN_WALLET_URI_BASE_ID +
                " WHERE " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_ID + "=?",
                new String[] { tokenIdStr })) {
            if (!c.moveToNext()) {
                Log.w(TAG, "Auth token has been revoked, or has expired and been purged");
                return null;
            }

            final int id = c.getInt(0);
            final long issued = c.getLong(1);
            final int publicKeyId = c.getInt(2);
            final int walletUriBaseId = c.getInt(3);
            final byte[] scope = c.getBlob(4);
            final String cluster = c.getString(5);
            final byte[] publicKey = c.getBlob(6);
            final String accountLabel = c.isNull(7) ? null : c.getString(7);
            final Uri walletUriBase = c.isNull(8) ? null : Uri.parse(c.getString(8));
            authRecord = new AuthRecord(id, identityRecord, publicKey, accountLabel, cluster, scope,
                    walletUriBase, publicKeyId, walletUriBaseId, issued,
                    issued + mAuthIssuerConfig.authorizationValidityMs);
        }

        // Revoke this authorization if it is either from the future, or too old to be reissuable
        if (revokeNonReissuableAuthRecord(authRecord)) {
            return null;
        }

        Log.v(TAG, "Returning AuthRecord from auth token: " + authRecord);

        return authRecord;
    }

    @NonNull
    public synchronized String toAuthToken(@NonNull AuthRecord authRecord) {
        assert(!authRecord.isRevoked());
        if (authRecord.isRevoked()) {
            // Don't fail here if asserts are not enabled. Returning an invalid auth token is better
            // than crashing the app.
            Log.e(TAG, "Issuing auth record for revoked auth token");
        }

        final JSONObject o = new JSONObject();
        try {
            o.put(AUTH_TOKEN_CONTENT_TYPE, getAuthTokenContentType());
            o.put(AUTH_TOKEN_IDENTITY_ID, Integer.toString(authRecord.identity.id));
            o.put(AUTH_TOKEN_TOKEN_ID, Integer.toString(authRecord.id));
        } catch (JSONException e) {
            throw new UnsupportedOperationException("Error encoding auth record as JSON", e);
        }
        final byte[] payload = o.toString().getBytes(StandardCharsets.UTF_8);

        // To create an AuthRecord requires that the DB have been previously opened; we can thus
        // rely on mSecretKey being initialized and valid.
        final SecretKeySpec identityKey = decryptHmacSha256SecretKey(
                authRecord.identity.secretKeyCiphertext, authRecord.identity.secretKeyIV);

        // Verify the HMAC on the auth token
        final byte[] payloadHmac;
        try {
            final Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(identityKey);
            payloadHmac = hmac.doFinal(payload);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new UnsupportedOperationException("Error generating HMAC for auth token payload", e);
        }

        assert(payloadHmac.length == AUTH_TOKEN_HMAC_LENGTH_BYTES);
        final byte[] authToken = Arrays.copyOf(payload, payload.length + AUTH_TOKEN_HMAC_LENGTH_BYTES);
        System.arraycopy(payloadHmac, 0, authToken, payload.length, AUTH_TOKEN_HMAC_LENGTH_BYTES);

        Log.v(TAG, "Returning auth token for AuthRecord: " + authRecord);

        return Base64.encodeToString(authToken,Base64.NO_PADDING | Base64.NO_WRAP);
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

    @NonNull
    public synchronized AuthRecord issue(@NonNull String name,
                                         @NonNull Uri uri,
                                         @NonNull Uri relativeIconUri,
                                         @NonNull byte[] publicKey,
                                         @Nullable String accountLabel,
                                         @NonNull String cluster,
                                         @Nullable Uri walletUriBase,
                                         @Nullable byte[] scope) {
        final SQLiteDatabase db = ensureStarted();

        if (scope == null) {
            scope = new byte[0];
        }

        // First, try and look up a matching identity
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

        // If no matching identity exists, create one
        if (identityId == -1) {
            Log.d(TAG, "Creating IdentityRecord for " + name + '/' + uri + '/' + relativeIconUri);

            final Pair<byte[], byte[]> p = createEncryptedHmacSha256SecretKey();
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

        // Next, try and look up the public key
        int publicKeyId = -1;
        final SQLiteDatabase.CursorFactory publicKeyCursorFactory = (db1, masterQuery, editTable, query) -> {
            query.bindBlob(1, publicKey);
            return new SQLiteCursor(masterQuery, editTable, query);
        };
        try (final Cursor c = db.queryWithFactory(publicKeyCursorFactory,
                false,
                AuthDatabase.TABLE_PUBLIC_KEYS,
                new String[] { AuthDatabase.COLUMN_PUBLIC_KEYS_ID },
                AuthDatabase.COLUMN_PUBLIC_KEYS_RAW + "=?",
                null,
                null,
                null,
                null,
                null)) {
            if (c.moveToNext()) {
                publicKeyId = c.getInt(0);
            }
        }

        // If no matching public key exists, create one
        if (publicKeyId == -1) {
            final ContentValues publicKeyContentValues = new ContentValues(2);
            publicKeyContentValues.put(AuthDatabase.COLUMN_PUBLIC_KEYS_RAW, publicKey);
            publicKeyContentValues.put(AuthDatabase.COLUMN_PUBLIC_KEYS_LABEL, accountLabel);
            publicKeyId = (int) db.insert(AuthDatabase.TABLE_PUBLIC_KEYS, null, publicKeyContentValues);
        }

        // Next, try and look up the wallet URI base
        int walletUriBaseId = -1;
        try (final Cursor c = db.query(AuthDatabase.TABLE_WALLET_URI_BASE,
                new String[] { AuthDatabase.COLUMN_WALLET_URI_BASE_ID },
                AuthDatabase.COLUMN_WALLET_URI_BASE_URI +
                        (walletUriBase != null ? "=?" : " IS NULL"),
                (walletUriBase != null ? new String[] { walletUriBase.toString() } : null),
                null,
                null,
                null)) {
            if (c.moveToNext()) {
                walletUriBaseId = c.getInt(0);
            }
        }

        // If no matching wallet URI base exists, create one
        if (walletUriBaseId == -1) {
            final ContentValues walletUriBaseContentValues = new ContentValues(1);
            walletUriBaseContentValues.put(AuthDatabase.COLUMN_WALLET_URI_BASE_URI,
                    walletUriBase != null ? walletUriBase.toString() : null);
            walletUriBaseId = (int) db.insert(AuthDatabase.TABLE_WALLET_URI_BASE, null, walletUriBaseContentValues);
        }

        final long now = System.currentTimeMillis();

        final ContentValues authContentValues = new ContentValues(6);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID, identityRecord.id);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED, now);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID, publicKeyId);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_CLUSTER, cluster);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, walletUriBaseId);
        authContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_SCOPE, scope);
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
            // Note: we only purge if we exceeded the max outstanding authorizations per identity. We
            // thus know that the identity remains referenced; no need to purge unused identities.
            deleteUnreferencedPublicKeys(db);
            deleteUnreferencedWalletUriBase(db);
        }

        return new AuthRecord(id, identityRecord, publicKey, accountLabel, cluster, scope,
                walletUriBase, publicKeyId, walletUriBaseId, now,
                now + mAuthIssuerConfig.authorizationValidityMs);
    }

    @Nullable
    public synchronized AuthRecord reissue(@NonNull AuthRecord authRecord) {
        final SQLiteDatabase db = ensureStarted();
        assert(!authRecord.isRevoked());

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
            final ContentValues reissueContentValues = new ContentValues(6);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID, authRecord.identity.id);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED, now);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID, authRecord.publicKeyId);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_CLUSTER, authRecord.cluster);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID, authRecord.walletUriBaseId);
            reissueContentValues.put(AuthDatabase.COLUMN_AUTHORIZATIONS_SCOPE, authRecord.scope);
            final int id = (int) db.insert(AuthDatabase.TABLE_AUTHORIZATIONS, null, reissueContentValues);
            reissued = new AuthRecord(id, authRecord.identity, authRecord.publicKey,
                    authRecord.accountLabel, authRecord.cluster, authRecord.scope,
                    authRecord.walletUriBase, authRecord.publicKeyId, authRecord.walletUriBaseId,
                    now, now + mAuthIssuerConfig.authorizationValidityMs);
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
        authRecord.setRevoked();

        final SQLiteStatement deleteAuthorizations = db
                .compileStatement(
                        "DELETE FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                        " WHERE " + AuthDatabase.COLUMN_AUTHORIZATIONS_ID + "=?");
        deleteAuthorizations.bindLong(1, authRecord.id);
        final int deleteCount = deleteAuthorizations.executeUpdateDelete();

        // There may now be unreferenced authorization data; if so, delete them
        deleteUnreferencedIdentities(db);
        deleteUnreferencedPublicKeys(db);
        deleteUnreferencedWalletUriBase(db);

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

        // There may now be unreferenced authorization data; if so, delete them
        deleteUnreferencedPublicKeys(db);
        deleteUnreferencedWalletUriBase(db);

        return (deleteCount != 0);
    }

    @GuardedBy("this")
    private void deleteUnreferencedIdentities(@NonNull SQLiteDatabase db) {
        final SQLiteStatement deleteUnreferencedIdentities = db.compileStatement(
                "DELETE FROM " + AuthDatabase.TABLE_IDENTITIES +
                        " WHERE " + AuthDatabase.COLUMN_IDENTITIES_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID +
                        " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedIdentities.executeUpdateDelete();
    }

    @GuardedBy("this")
    private void deleteUnreferencedPublicKeys(@NonNull SQLiteDatabase db) {
        final SQLiteStatement deleteUnreferencedPublicKeys = db.compileStatement(
                "DELETE FROM " + AuthDatabase.TABLE_PUBLIC_KEYS +
                        " WHERE " + AuthDatabase.COLUMN_PUBLIC_KEYS_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                        " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedPublicKeys.executeUpdateDelete();
    }

    @GuardedBy("this")
    private void deleteUnreferencedWalletUriBase(@NonNull SQLiteDatabase db) {
        final SQLiteStatement deleteUnreferencedWalletUriBase = db.compileStatement(
                "DELETE FROM " + AuthDatabase.TABLE_WALLET_URI_BASE +
                        " WHERE " + AuthDatabase.COLUMN_WALLET_URI_BASE_ID + " NOT IN " +
                        "(SELECT DISTINCT " + AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                        " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS + ')');
        deleteUnreferencedWalletUriBase.executeUpdateDelete();
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
    public synchronized List<AuthRecord> getAuthorizations(@NonNull IdentityRecord identityRecord) {
        final SQLiteDatabase db = ensureStarted();

        final ArrayList<AuthRecord> authorizations = new ArrayList<>();
        try (final Cursor c = db.rawQuery("SELECT " +
                AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_ISSUED +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_SCOPE +
                ", " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_CLUSTER +
                ", " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_RAW +
                ", " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_LABEL +
                ", " + AuthDatabase.TABLE_WALLET_URI_BASE + '.' + AuthDatabase.COLUMN_WALLET_URI_BASE_URI +
                " FROM " + AuthDatabase.TABLE_AUTHORIZATIONS +
                " INNER JOIN " + AuthDatabase.TABLE_PUBLIC_KEYS +
                " ON " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_PUBLIC_KEY_ID +
                " = " + AuthDatabase.TABLE_PUBLIC_KEYS + '.' + AuthDatabase.COLUMN_PUBLIC_KEYS_ID +
                " INNER JOIN " + AuthDatabase.TABLE_WALLET_URI_BASE +
                " ON " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_WALLET_URI_BASE_ID +
                " = " + AuthDatabase.TABLE_WALLET_URI_BASE + '.' + AuthDatabase.COLUMN_WALLET_URI_BASE_ID +
                " WHERE " + AuthDatabase.TABLE_AUTHORIZATIONS + '.' + AuthDatabase.COLUMN_AUTHORIZATIONS_IDENTITY_ID + "=?",
                new String[] { Integer.toString(identityRecord.id) })) {
            while (c.moveToNext()) {
                final int id = c.getInt(0);
                final long issued = c.getLong(1);
                final int publicKeyId = c.getInt(2);
                final int walletUriBaseId = c.getInt(3);
                final byte[] scope = c.getBlob(4);
                final String cluster = c.getString(5);
                final byte[] publicKey = c.getBlob(6);
                final String accountLabel = c.isNull(7) ? null : c.getString(7);
                final Uri walletUriBase = c.isNull(8) ? null : Uri.parse(c.getString(8));
                final AuthRecord authRecord = new AuthRecord(id, identityRecord, publicKey,
                        accountLabel, cluster, scope, walletUriBase, publicKeyId, walletUriBaseId,
                        issued, issued + mAuthIssuerConfig.authorizationValidityMs);
                authorizations.add(authRecord);
            }
        }
        return authorizations;
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
                InvalidAlgorithmParameterException |  InvalidKeyException | BadPaddingException e) {
            throw new RuntimeException("Error while decrypting identity key", e);
        }
    }
}
