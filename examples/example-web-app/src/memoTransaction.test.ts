import { describe, expect, it } from 'vitest';

import { buildMemoTransaction, MEMO_PROGRAM_ID, shortvec } from './memoTransaction.js';

describe('shortvec', () => {
    it('encodes lengths as little-endian base-128', () => {
        expect(shortvec(0)).toEqual([0]);
        expect(shortvec(127)).toEqual([0x7f]);
        expect(shortvec(128)).toEqual([0x80, 0x01]);
        expect(shortvec(16384)).toEqual([0x80, 0x80, 0x01]);
    });
});

describe('buildMemoTransaction', () => {
    it('lays out an unsigned legacy transaction with one memo instruction', () => {
        const feePayer = new Uint8Array(32).fill(7);
        const blockhash = new Uint8Array(32).fill(9);
        const memo = 'a'.repeat(200);

        const tx = buildMemoTransaction(feePayer, blockhash, memo);

        let offset = 0;
        const take = (n: number) => tx.slice(offset, (offset += n));
        expect(take(1)).toEqual(Uint8Array.of(1)); // signature count
        expect(take(64)).toEqual(new Uint8Array(64)); // empty fee payer signature
        expect(take(3)).toEqual(Uint8Array.of(1, 0, 1)); // message header
        expect(take(1)).toEqual(Uint8Array.of(2)); // account count
        expect(take(32)).toEqual(feePayer);
        expect(take(32)).toEqual(MEMO_PROGRAM_ID);
        expect(take(32)).toEqual(blockhash);
        expect(take(1)).toEqual(Uint8Array.of(1)); // instruction count
        expect(take(2)).toEqual(Uint8Array.of(1, 0)); // program index, no accounts
        expect(take(2)).toEqual(Uint8Array.from(shortvec(200)));
        expect(new TextDecoder().decode(take(200))).toBe(memo);
        expect(offset).toBe(tx.length);
    });
});

describe('buildMemoTransaction v0', () => {
    it('wraps the same message in the v0 prefix and an empty address table list', () => {
        const feePayer = new Uint8Array(32).fill(7);
        const blockhash = new Uint8Array(32).fill(9);

        const legacy = buildMemoTransaction(feePayer, blockhash, 'gm');
        const v0 = buildMemoTransaction(feePayer, blockhash, 'gm', 0);

        expect(v0.slice(0, 65)).toEqual(legacy.slice(0, 65)); // signature section
        expect(v0[65]).toBe(0x80);
        expect(v0.slice(66, -1)).toEqual(legacy.slice(65));
        expect(v0.at(-1)).toBe(0);
    });
});
