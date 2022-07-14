package com.solana.mobilewalletadapter.clientlib;

import android.content.Intent;

/**
 * Used to abstract away which API is used for starting/launching an Activity for result, depending
 * on API level, etc...
 */
public interface ActivityResultSender {

    void launch(Intent intent);
}
