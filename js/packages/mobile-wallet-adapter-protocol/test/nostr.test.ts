// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
    createNostrEvent,
    deriveSessionIdentifier,
    generateNostrKeypair,
    NOSTR_EVENT_KIND_MWA,
    verifyNostrEvent,
} from '../src/nostr.js';

describe('generateNostrKeypair', () => {
    it('returns a 32-byte private key and 64-char hex public key', () => {
        const { privateKey, publicKey } = generateNostrKeypair();
        expect(privateKey).toBeInstanceOf(Uint8Array);
        expect(privateKey.length).toBe(32);
        expect(publicKey).toMatch(/^[0-9a-f]{64}$/);
    });

    it('generates distinct keypairs each call', () => {
        const a = generateNostrKeypair();
        const b = generateNostrKeypair();
        expect(a.publicKey).not.toBe(b.publicKey);
    });
});

describe('deriveSessionIdentifier', () => {
    const EXPORTED_KEY_BYTES = Uint8Array.of(1, 2, 3, 4);

    const { mockExportKey } = vi.hoisted(() => ({
        mockExportKey: vi.fn(),
    }));

    beforeEach(() => {
        mockExportKey.mockResolvedValue(EXPORTED_KEY_BYTES.buffer);
        vi.stubGlobal('crypto', {
            subtle: {
                exportKey: mockExportKey,
            },
        });
    });

    afterEach(() => {
        mockExportKey.mockReset();
        vi.restoreAllMocks();
        vi.unstubAllGlobals();
    });

    it('exports the raw key and returns a 64-char lowercase hex string', async () => {
        const associationPublicKey = {} as CryptoKey;

        const sessionId = await deriveSessionIdentifier(associationPublicKey);

        expect(mockExportKey).toHaveBeenCalledWith('raw', associationPublicKey);
        expect(sessionId).toMatch(/^[0-9a-f]{64}$/);
    });

    it('returns the same identifier for the same key bytes', async () => {
        const key = {} as CryptoKey;

        const a = await deriveSessionIdentifier(key);
        const b = await deriveSessionIdentifier(key);

        expect(a).toBe(b);
    });

    it('returns different identifiers for different key bytes', async () => {
        const key1 = {} as CryptoKey;
        const id1 = await deriveSessionIdentifier(key1);

        mockExportKey.mockResolvedValue(Uint8Array.of(5, 6, 7, 8).buffer);
        const key2 = {} as CryptoKey;
        const id2 = await deriveSessionIdentifier(key2);

        expect(id1).not.toBe(id2);
    });
});

describe('createNostrEvent', () => {
    it('produces a valid event with correct fields', () => {
        const { privateKey, publicKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'hello', [['d', 'abc']], privateKey);

        expect(event.pubkey).toBe(publicKey);
        expect(event.kind).toBe(NOSTR_EVENT_KIND_MWA);
        expect(event.content).toBe('hello');
        expect(event.tags).toEqual([['d', 'abc']]);
        expect(event.id).toMatch(/^[0-9a-f]{64}$/);
        expect(event.sig).toMatch(/^[0-9a-f]{128}$/);
        expect(event.created_at).toBeGreaterThan(0);
    });

    it('produces an event that passes verification', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, '', [['d', 'session123']], privateKey);
        expect(verifyNostrEvent(event)).toBe(true);
    });

    it('handles empty content and tags', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, '', [], privateKey);
        expect(verifyNostrEvent(event)).toBe(true);
        expect(event.content).toBe('');
        expect(event.tags).toEqual([]);
    });

    it('handles content with special characters', () => {
        const { privateKey } = generateNostrKeypair();
        const content = 'line1\nline2\t"quoted"\\backslash';
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, content, [], privateKey);
        expect(verifyNostrEvent(event)).toBe(true);
        expect(event.content).toBe(content);
    });
});

describe('verifyNostrEvent', () => {
    it('rejects an event with a tampered id', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], privateKey);
        const tampered = { ...event, id: '00'.repeat(32) };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event with a tampered signature', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], privateKey);
        const tampered = { ...event, sig: '00'.repeat(64) };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event with tampered content', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'original', [], privateKey);
        const tampered = { ...event, content: 'modified' };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event with a tampered pubkey', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], privateKey);
        const otherPubkey = generateNostrKeypair().publicKey;
        const tampered = { ...event, pubkey: otherPubkey };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event with tampered tags', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [['d', 'abc']], privateKey);
        const tampered = { ...event, tags: [['d', 'xyz']] };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event with tampered created_at', () => {
        const { privateKey } = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], privateKey);
        const tampered = { ...event, created_at: event.created_at + 1 };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });

    it('rejects an event signed by a different key', () => {
        const key1 = generateNostrKeypair();
        const key2 = generateNostrKeypair();
        const event = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], key1.privateKey);
        const wrongSigner = createNostrEvent(NOSTR_EVENT_KIND_MWA, 'test', [], key2.privateKey);
        const tampered = { ...event, sig: wrongSigner.sig };
        expect(verifyNostrEvent(tampered)).toBe(false);
    });
});
