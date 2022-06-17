/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.mobilewalletadapter.clientlib.protocol;

import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.solana.mobilewalletadapter.common.protocol.MessageReceiver;
import com.solana.mobilewalletadapter.common.protocol.MessageSender;
import com.solana.mobilewalletadapter.common.util.NotifyOnCompleteFuture;
import com.solana.mobilewalletadapter.common.util.NotifyingCompletableFuture;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeoutException;

public class JsonRpc20Client implements MessageReceiver {
    private static final String TAG = JsonRpc20Client.class.getSimpleName();

    private int mNextMessageId = 1;
    private MethodCallResultFuture mOutstandingRequest;
    private MessageSender mSender;
    private Timer mTimer;

    // Throws UnsupportedOperationException
    @NonNull
    public NotifyOnCompleteFuture<Object> methodCall(@NonNull String method,
                                                     @Nullable Object params,
                                                     @IntRange(from = 0) int timeoutMs)
            throws IOException {
        if (method.isEmpty()) {
            throw new IllegalArgumentException("method cannot be empty");
        } else if (method.startsWith("rpc.")) {
            throw new IllegalArgumentException("reserved method name (starts with 'rpc.'");
        } else if (params != null && !(params instanceof JSONObject) &&
                !(params instanceof JSONArray)) {
            throw new IllegalArgumentException("params must be JSONObject or JSONArray");
        }

        final int id = mNextMessageId++;
        final JSONObject o;
        try {
            o = new JSONObject();
            o.put("jsonrpc", "2.0");
            o.put("method", method);
            o.put("params", params); // OK if params is null
            o.put("id", id);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Error preparing JSON-RPC 2.0 message", e);
        }

        final MethodCallResultFuture future;
        synchronized (this) {
            if (mSender == null) {
                throw new IOException("JSON-RPC 2.0 client is disconnected");
            } else if (mOutstandingRequest != null) {
                throw new UnsupportedOperationException("Only a single request may be outstanding");
            }

            mSender.send(o.toString().getBytes(StandardCharsets.UTF_8));

            future = new MethodCallResultFuture(id);
            mOutstandingRequest = future;

            if (timeoutMs > 0) {
                if (mTimer == null) {
                    mTimer = new Timer(JsonRpc20Client.class.getSimpleName(), true);
                }
                mTimer.schedule(new PendingRequestTimeoutTask(future), timeoutMs);
            }
        }

        return future;
    }

    public void notification(@NonNull String method,
                             @Nullable Object params)
            throws IOException {
        if (method.isEmpty()) {
            throw new IllegalArgumentException("notification cannot be empty");
        } else if (method.startsWith("rpc.")) {
            throw new IllegalArgumentException("reserved notification name (starts with 'rpc.'");
        } else if (params != null && !(params instanceof JSONObject) &&
                !(params instanceof JSONArray)) {
            throw new IllegalArgumentException("params must be JSONObject or JSONArray");
        }

        final JSONObject o;
        try {
            o = new JSONObject();
            o.put("jsonrpc", "2.0");
            o.put("method", method);
            o.put("params", params); // OK if params is null
        } catch (JSONException e) {
            throw new IllegalArgumentException("Error preparing JSON-RPC 2.0 message", e);
        }

        synchronized (this) {
            if (mSender == null) {
                throw new IOException("JSON-RPC 2.0 client is disconnected");
            }

            Log.d(TAG, "Sending notification '" + method + "' with params=" + params);
            mSender.send(o.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @Override
    public void receiverConnected(@NonNull MessageSender messageSender) {
        Log.v(TAG, "JSON-RPC 2.0 client connected");

        synchronized (this) {
            mSender = messageSender;
        }
    }

    @Override
    public void receiverDisconnected() {
        synchronized (this) {
            if (mOutstandingRequest != null) {
                mOutstandingRequest.cancel(true);
                clearOutstandingRequest();
            }
        }

        Log.v(TAG, "JSON-RPC 2.0 client disconnected");
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
            if (!"2.0".equals(o.getString("jsonrpc"))) {
                Log.w(TAG, "Received other than a JSON-RPC 2.0 message");
                return;
            }
        } catch (JSONException e) {
            Log.w(TAG, "Incoming JSON-RPC 2.0 payload is not valid", e);
            return;
        }

        // Try and get an ID, to look up the corresponding request. Without an ID, there's not much
        // interesting we can do with a response (result or error), so bail.
        final String id = o.optString("id");
        final int idAsInt;
        try {
            idAsInt = Integer.parseInt(id);
        } catch (NumberFormatException ignored) {
            Log.w(TAG, "Request id=" + id + " could not be interpreted as an int, aborting");
            return;
        }
        final MethodCallResultFuture r;
        synchronized (this) {
            if (mOutstandingRequest != null && mOutstandingRequest.mId == idAsInt) {
                r = mOutstandingRequest;
                clearOutstandingRequest();
            } else {
                r = null;
            }
        }
        if (r == null) {
            Log.w(TAG, "Unable to locate a request with id=" + id);
            return;
        }

        // Now, try and interpret this as an error
        final JSONObject error = o.optJSONObject("error");
        if (error != null) {
            final int code;
            try {
                code = error.getInt("code");
            } catch (JSONException e) {
                Log.w(TAG, "Received malformed error response for request with id=" + id);
                r.completeExceptionally(new JsonRpc20InvalidResponseException(
                        "Received malformed error response for request with id=" + id));
                return;
            }

            final String message = error.optString("message", "");
            final String data = error.optString("data");
            r.completeExceptionally(new JsonRpc20RemoteException(code, message, data));
            return;
        }

        // Now, try and interpret this as a result.
        final Object result = o.opt("result");
        if (result != null) {
            r.complete(result);
            return;
        }

        // Finally, if we get here, it is a malformed message. Inform client.
        Log.w(TAG, "Received a response with neither error nor result for request with id=" + id);
        r.completeExceptionally(new JsonRpc20InvalidResponseException(
                "Received a response with neither error nor result for request with id=" + id));
    }

    @GuardedBy("this")
    private void clearOutstandingRequest() {
        mOutstandingRequest = null;
        if (mTimer != null) {
            mTimer.cancel();
            mTimer = null;
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

    private class MethodCallResultFuture extends NotifyingCompletableFuture<Object> {
        private final int mId;

        public MethodCallResultFuture(int id) {
            mId = id;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            synchronized (JsonRpc20Client.this) {
                if (mOutstandingRequest == this) {
                    clearOutstandingRequest();
                }
            }

            return super.cancel(mayInterruptIfRunning);
        }
    }

    private class PendingRequestTimeoutTask extends TimerTask {
        @NonNull
        private final MethodCallResultFuture mFuture;

        public PendingRequestTimeoutTask(@NonNull MethodCallResultFuture future) {
            mFuture = future;
        }

        @Override
        public void run() {
            synchronized (JsonRpc20Client.this) {
                if (mOutstandingRequest == mFuture) {
                    clearOutstandingRequest();
                }
            }
            mFuture.completeExceptionally(new TimeoutException(
                    "Timed out waiting for response with id=" + mFuture.mId));
        }
    }

    public static abstract class JsonRpc20Exception extends Exception {
        public JsonRpc20Exception(@Nullable String m) { super(m); }
    }

    public static class JsonRpc20InvalidResponseException extends JsonRpc20Exception {
        public JsonRpc20InvalidResponseException(@Nullable String m) { super(m); }
    }

    public static class JsonRpc20RemoteException extends JsonRpc20Exception {
        public static final int SERVER_RESERVED_ERROR_MIN = -32768;
        public static final int SERVER_RESERVED_ERROR_MAX = -32000;

        public final int code;
        @Nullable
        public final String data;

        public JsonRpc20RemoteException(int code, @NonNull String message, @Nullable String data) {
            super(message);
            this.code = code;
            this.data = data;
        }

        public boolean isReservedError() {
            return code >= SERVER_RESERVED_ERROR_MIN && code <= SERVER_RESERVED_ERROR_MAX;
        }

        @NonNull
        @Override
        public String getMessage() {
            String message = super.getMessage();
            if (message == null) {
                return Integer.toString(code);
            } else {
                return code + "/" + message;
            }
        }
    }
}
