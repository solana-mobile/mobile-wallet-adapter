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
        !/android/i.test(navigator.userAgent) &&
        !/iphone|ipad|ipod/i.test(navigator.userAgent)
    );
}