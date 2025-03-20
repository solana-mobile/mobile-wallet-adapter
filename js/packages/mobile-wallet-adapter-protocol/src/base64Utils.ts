export function encode(input: string): string {
    return window.btoa(input);
}

export function fromUint8Array(byteArray: Uint8Array, urlsafe?: boolean): string {
    const base64 = window.btoa(String.fromCharCode.call(null, ...byteArray));
    if (urlsafe) {
        return base64
            .replace(/\+/g, '-')
            .replace(/\//g, '_')
            .replace(/=+$/, '');
    } else return base64;
}

export function toUint8Array(base64EncodedByteArray: string): Uint8Array {
    return new Uint8Array(
        window
            .atob(base64EncodedByteArray)
            .split('')
            .map((c) => c.charCodeAt(0)),
    );
}