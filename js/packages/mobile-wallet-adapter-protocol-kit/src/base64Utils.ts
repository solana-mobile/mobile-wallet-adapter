export function fromUint8Array(byteArray: Uint8Array): string {
    return window.btoa(String.fromCharCode.call(null, ...byteArray));
}

export function toUint8Array(base64EncodedByteArray: string): Uint8Array {
    return new Uint8Array(
        window
            .atob(base64EncodedByteArray)
            .split('')
            .map((c) => c.charCodeAt(0)),
    );
}
