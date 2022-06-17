/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.walletlib.protocol;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public abstract class JsonRpc20Server implements MessageReceiver {
    private static final String TAG = JsonRpc20Server.class.getSimpleName();

    public static final int ERROR_PARSE = -32700;
    public static final int ERROR_INVALID_REQUEST = -32600;
    public static final int ERROR_METHOD_NOT_FOUND = -32601;
    public static final int ERROR_INVALID_PARAMS = -32602;
    public static final int ERROR_INTERNAL = -32603;

    private MessageSender mSender;

    @Override
    public void receiverConnected(@NonNull MessageSender messageSender) {
        Log.v(TAG, "JSON-RPC 2.0 server connected");
        synchronized (this) {
            mSender = messageSender;
        }
    }

    @Override
    public void receiverDisconnected() {
        synchronized (this) {
            mSender = null;
        }
        Log.v(TAG, "JSON-RPC 2.0 server disconnected");
    }

    @Override
    public void receiverMessageReceived(@NonNull byte[] payload) {
        Log.v(TAG, "JSON-RPC 2.0 message received");

        final String jsonStr;
        try {
            jsonStr = decodeAsUtf8String(payload);
        } catch (CharacterCodingException e) {
            Log.w(TAG, "Incoming JSON-RPC 2.0 payload contains UTF-8 errors; not decoding", e);
            return;
        }

        // Validate that the input is JSON-RPC 2.0. If not, do not attempt to decode further.
        final JSONObject o;
        try {
            o = new JSONObject(jsonStr);
        } catch (JSONException e) {
            Log.w(TAG, "Request JSON-RPC 2.0 payload is not valid", e);
            try {
                respondWithError(ERROR_PARSE, "Request JSON-RPC 2.0 payload is not valid", null, null);
            } catch (IOException e2) {
                Log.e(TAG, "Failed sending ERROR_PARSE response", e2);
            }
            return;
        }
        if (!"2.0".equals(o.optString("jsonrpc")) || !o.has("method") || !o.has("id")) {
            Log.w(TAG, "Request is other than a JSON-RPC 2.0 message");
            try {
                respondWithError(ERROR_INVALID_REQUEST, "Request is other than a JSON-RPC 2.0 message", null, null);
            } catch (IOException e) {
                Log.e(TAG, "Failed sending ERROR_INVALID_REQUEST response", e);
            }
            return;
        }

        // Read the request ID. If the request does not have an ID, it is a notification and will
        // not receive a reply.
        final Object id = o.opt("id");
        if (id != null && !(id instanceof Number) && !(id instanceof String) && id != JSONObject.NULL) {
            Log.w(TAG, "Request does not contain a valid id");
            try {
                respondWithError(ERROR_INVALID_REQUEST, "Request does not contain a valid id", null, null);
            } catch (IOException e) {
                Log.e(TAG, "Failed sending ERROR_INVALID_REQUEST response", e);
            }
            return;
        }

        // Read the method name. If not found, blank, or starts with "rpc.", this is not a
        // well-formed request.
        final String method = o.optString("method");
        if (method.isEmpty() || method.startsWith("rpc.")) {
            Log.w(TAG, "Request references method with illegal name='" + method + "'");
            try {
                respondWithError(ERROR_METHOD_NOT_FOUND, "Method '" + method + "' not available", null, id);
            } catch (IOException e) {
                Log.e(TAG, "Failed sending ERROR_METHOD_NOT_FOUND response", e);
            }
            return;
        }

        final Object params = o.opt("params");
        if (params != null && !(params instanceof JSONObject) && !(params instanceof JSONArray)) {
            Log.w(TAG, "params must be a structured value");
            try {
                respondWithError(ERROR_INVALID_PARAMS, "params must be a structured value", null, id);
            } catch (IOException e) {
                Log.e(TAG, "Failed sending ERROR_INVALID_PARAMS response", e);
            }
            return;
        }

        dispatchRpc(id, method, params);
    }

    protected abstract void dispatchRpc(@Nullable Object id,
                                        @NonNull String method,
                                        @Nullable Object params);

    protected void handleRpcResult(@Nullable Object id,
                                   @NonNull Object result)
            throws IOException {
        if (id == null) {
            Log.v(TAG, "Discarding response for notification");
            return;
        }
        Log.d(TAG, "Responding with result for id=" + id);
        respondWithResult(result, id);
    }

    protected void handleRpcError(@Nullable Object id,
                                  int code,
                                  @NonNull String message,
                                  @Nullable String data)
            throws IOException {
        if (id == null) {
            Log.w(TAG, "Discarding error '" + message + "' for notification");
            return;
        }
        Log.d(TAG, "Responding with error for id=" + id + " (code=" + code + ", message=" + message + ")");
        respondWithError(code, message, data, id);
    }

    // If non-null, data should be a type compatible with JSONObject.put(...)
    private void respondWithError(int code,
                                  @NonNull String message,
                                  @Nullable Object data,
                                  @Nullable Object id)
            throws IOException {
        final JSONObject o = new JSONObject();
        try {
            final JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            error.put("data", data);
            o.put("jsonrpc", "2.0");
            o.put("error", error);
            o.put("id", (id != null) ? id : JSONObject.NULL);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Error creating JSON-RPC 2.0 response object", e);
        }

        synchronized (this) {
            if (mSender == null) {
                throw new IOException("JSON-RPC 2.0 server is disconnected");
            }
            mSender.send(o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    // result should be a type compatible with JSONObject.put(...)
    private void respondWithResult(@NonNull Object result,
                                   @NonNull Object id)
            throws IOException {
        final JSONObject o = new JSONObject();
        try {
            o.put("jsonrpc", "2.0");
            o.put("result", result);
            o.put("id", id);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Error creating JSON-RPC 2.0 response object", e);
        }

        synchronized (this) {
            if (mSender == null) {
                throw new IOException("JSON-RPC 2.0 server is disconnected");
            }
            mSender.send(o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @NonNull
    private static String decodeAsUtf8String(@NonNull byte[] b) throws CharacterCodingException {
        final CharsetDecoder utf8Dec = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        final ByteBuffer bb = ByteBuffer.wrap(b);
        return utf8Dec.decode(bb).toString();
    }
}
