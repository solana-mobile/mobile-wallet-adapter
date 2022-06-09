// https://stackoverflow.com/a/9458996/802047
export default function arrayBufferToBase64String(buffer: ArrayBuffer) {
    let binary = '';
    const bytes = new Uint8Array(buffer);
    const len = bytes.byteLength;
    for (let ii = 0; ii < len; ii++) {
        binary += String.fromCharCode(bytes[ii]);
    }
    return window.btoa(binary);
}
