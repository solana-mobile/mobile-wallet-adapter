export function getIsLocalAssociationSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        /android/i.test(navigator.userAgent)
    );
}

export function getIsRemoteAssociationSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        !/Android|webOS|iPhone|iPad|iPod|BlackBerry|IEMobile|Opera Mini/i.test(navigator.userAgent)
    );
}

// Source: https://github.com/anza-xyz/wallet-adapter/blob/master/packages/core/react/src/getEnvironment.ts#L14
// This is the same implementation that gated MWA in the Anza wallet-adapter-react library.
export function isWebView(userAgentString: string) {
    return /(WebView|Version\/.+(Chrome)\/(\d+)\.(\d+)\.(\d+)\.(\d+)|; wv\).+(Chrome)\/(\d+)\.(\d+)\.(\d+)\.(\d+))/i.test(
        userAgentString
    );
}