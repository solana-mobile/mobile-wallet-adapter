/**
 * Builds an unsigned legacy transaction with one Memo program instruction, serialized to the
 * wire format wallets sign. Hand-rolled so the example needs no Solana SDK.
 */
import { base58Decode } from './base58.js';

export const MEMO_PROGRAM_ID = base58Decode('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr');

/** Solana shortvec: little-endian base-128 length prefix. */
export function shortvec(length: number): number[] {
    const out: number[] = [];
    let remaining = length;
    for (;;) {
        const byte = remaining & 0x7f;
        remaining >>= 7;
        if (remaining === 0) {
            out.push(byte);
            return out;
        }
        out.push(byte | 0x80);
    }
}

export function buildMemoTransaction(feePayer: Uint8Array, recentBlockhash: Uint8Array, memo: string): Uint8Array {
    const data = new TextEncoder().encode(memo);
    const message = [
        // Header: 1 required signature, 0 read-only signed accounts, 1 read-only unsigned account.
        1,
        0,
        1,
        ...shortvec(2),
        ...feePayer,
        ...MEMO_PROGRAM_ID,
        ...recentBlockhash,
        ...shortvec(1),
        1, // program id index: the memo program
        ...shortvec(0), // no instruction accounts
        ...shortvec(data.length),
        ...data,
    ];
    // One zeroed signature slot for the fee payer, then the message.
    return Uint8Array.from([...shortvec(1), ...new Array(64).fill(0), ...message]);
}
