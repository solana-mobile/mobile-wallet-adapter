package com.solana.mobilewalletadapter.walletlib.scenario;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

public class DefaultWalletIconProvider implements WalletIconProvider{
    private final Drawable mIconDrawable;
    public DefaultWalletIconProvider(Context context) {
        this.mIconDrawable = context.getPackageManager().getApplicationIcon(context.getApplicationInfo());
    }

    @Override
    public Uri getWalletIconDataUri() {
        int width = mIconDrawable.getIntrinsicWidth();
        int height = mIconDrawable.getIntrinsicHeight();
        Bitmap iconBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        mIconDrawable.setBounds(0, 0, width, height);
        mIconDrawable.draw(new Canvas(iconBitmap));
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        iconBitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Uri.parse("data:image/png;base64," + Base64.encodeToString(byteStream.toByteArray(), Base64.DEFAULT));
    }
}
