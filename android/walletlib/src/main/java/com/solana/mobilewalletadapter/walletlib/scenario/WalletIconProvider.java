package com.solana.mobilewalletadapter.walletlib.scenario;

import android.net.Uri;

import androidx.annotation.Nullable;

/*package*/ interface WalletIconProvider {
    /**
     * Returns a base64 encoded data URI of the wallet icon. This will be the icon dapps display
     * for your wallet. The wallet icon must be an SVG, PNG, WebP, or GIF image.
     * <p>
     * You can use a tool like <a href="https://base64.guru/converter/encode/image">https://base64.guru/converter/encode/image</a>
     * to encode an image using the "Data URI" setting. It's a good idea to compress your image
     * losslessly with a tool like <a href="https://imageoptim.com">https://imageoptim.com</a> first.
     *
     * @return a base64 encoded data URI of an SVG, PNG, WebP, or GIF image, or null
     */
    @Nullable Uri getWalletIconDataUri();
}
