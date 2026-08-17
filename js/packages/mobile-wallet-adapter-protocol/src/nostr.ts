import { schnorr } from '@noble/curves/secp256k1.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, hexToBytes } from '@noble/hashes/utils.js';

export const NOSTR_EVENT_KIND_MWA = 20012;

export interface NostrEvent {
    id: string;
    pubkey: string;
    created_at: number;
    kind: number;
    tags: string[][];
    content: string;
    sig: string;
}

export function generateNostrKeypair(): { privateKey: Uint8Array; publicKey: string } {
    const privateKey = schnorr.utils.randomSecretKey();
    const publicKey = bytesToHex(schnorr.getPublicKey(privateKey));
    return { privateKey, publicKey };
}

export async function deriveSessionIdentifier(associationPublicKey: CryptoKey): Promise<string> {
    const rawKey = await crypto.subtle.exportKey('raw', associationPublicKey);
    const hash = sha256(new Uint8Array(rawKey));
    return bytesToHex(hash);
}

function serializeEvent(pubkey: string, createdAt: number, kind: number, tags: string[][], content: string): string {
    return JSON.stringify([0, pubkey, createdAt, kind, tags, content]);
}

function computeEventId(pubkey: string, createdAt: number, kind: number, tags: string[][], content: string): string {
    const serialized = serializeEvent(pubkey, createdAt, kind, tags, content);
    return bytesToHex(sha256(new TextEncoder().encode(serialized)));
}

export function createNostrEvent(kind: number, content: string, tags: string[][], privateKey: Uint8Array): NostrEvent {
    const pubkey = bytesToHex(schnorr.getPublicKey(privateKey));
    const createdAt = Math.floor(Date.now() / 1000);
    const id = computeEventId(pubkey, createdAt, kind, tags, content);
    const sig = bytesToHex(schnorr.sign(hexToBytes(id), privateKey));
    return { id, pubkey, created_at: createdAt, kind, tags, content, sig };
}

export function verifyNostrEvent(event: NostrEvent): boolean {
    const expectedId = computeEventId(event.pubkey, event.created_at, event.kind, event.tags, event.content);
    if (expectedId !== event.id) {
        return false;
    }
    try {
        return schnorr.verify(hexToBytes(event.sig), hexToBytes(event.id), hexToBytes(event.pubkey));
    } catch {
        return false;
    }
}
