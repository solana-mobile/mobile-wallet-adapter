/** Minimal base58 (Bitcoin alphabet) for addresses, blockhashes and signatures. */
const ALPHABET = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';

/** Encodes bytes as base58; each leading zero byte becomes a leading '1'. */
export function base58Encode(bytes: Uint8Array): string {
    let value = 0n;
    for (const byte of bytes) value = value * 256n + BigInt(byte);
    let out = '';
    while (value > 0n) {
        out = ALPHABET[Number(value % 58n)] + out;
        value /= 58n;
    }
    for (const byte of bytes) {
        if (byte !== 0) break;
        out = '1' + out;
    }
    return out;
}

/** Decodes base58 to bytes; each leading '1' becomes a zero byte. Throws on a character outside the alphabet. */
export function base58Decode(encoded: string): Uint8Array {
    let value = 0n;
    for (const char of encoded) {
        const index = ALPHABET.indexOf(char);
        if (index < 0) throw new Error(`Invalid base58 character: ${char}`);
        value = value * 58n + BigInt(index);
    }
    const bytes: number[] = [];
    while (value > 0n) {
        bytes.unshift(Number(value % 256n));
        value /= 256n;
    }
    for (const char of encoded) {
        if (char !== '1') break;
        bytes.unshift(0);
    }
    return Uint8Array.from(bytes);
}
