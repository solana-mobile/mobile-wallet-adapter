export function getIsSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        /android/i.test(navigator.userAgent)
    );
}

/**
 * @deprecated Use {@link getIsSupported} instead.
 */
export default getIsSupported;
