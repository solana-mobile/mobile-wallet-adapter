export default function getIsLocalAssociationSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        /android/i.test(navigator.userAgent)
    );
}
