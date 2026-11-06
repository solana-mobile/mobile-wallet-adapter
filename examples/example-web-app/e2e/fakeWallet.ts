/**
 * A wallet endpoint speaking MWA over Nostr, written from the MWA spec with WebCrypto and
 * `@noble/*` only, so it shares no code with the SDK under test. It signs with a real ed25519 key,
 * which lets the app verify sign-in and lets the e2e check every signature it shows.
 */
import { ed25519 } from '@noble/curves/ed25519.js';
import { schnorr as secp256k1Schnorr } from '@noble/curves/secp256k1.js';
import { sha256 } from '@noble/hashes/sha2.js';
import { bytesToHex, hexToBytes } from '@noble/hashes/utils.js';
import { createSignInMessage } from '@solana/wallet-standard-util';
import WebSocket from 'ws';

import { base58Encode } from '../src/base58.ts';

const NOSTR_EVENT_KIND_MWA = 20012;
const P256_PUBLIC_KEY_BYTES = 65;
const SEQUENCE_NUMBER_BYTES = 4;
const IV_BYTES = 12;
// The dapp subscribes as soon as it shows the QR code; a short delay keeps the wallet's
// announcement from reaching the relay first, since relays do not replay ephemeral events.
const ANNOUNCE_DELAY_MS = 500;

interface NostrEvent {
    id: string;
    pubkey: string;
    created_at: number;
    kind: number;
    tags: string[][];
    content: string;
    sig: string;
}

interface JsonRpcRequest {
    id: number;
    method: string;
    params: Record<string, unknown>;
}

const toBase64 = (bytes: Uint8Array) => Buffer.from(bytes).toString('base64');
const fromBase64 = (base64: string) => new Uint8Array(Buffer.from(base64, 'base64'));

/** Concatenates byte arrays. */
function concat(...arrays: Uint8Array[]): Uint8Array {
    const out = new Uint8Array(arrays.reduce((n, a) => n + a.length, 0));
    let offset = 0;
    for (const a of arrays) {
        out.set(a, offset);
        offset += a.length;
    }
    return out;
}

/** A Nostr event's id: the SHA-256 of its NIP-01 serialization. */
function eventId(event: Omit<NostrEvent, 'id' | 'sig'>): string {
    const serialized = JSON.stringify([0, event.pubkey, event.created_at, event.kind, event.tags, event.content]);
    return bytesToHex(sha256(new TextEncoder().encode(serialized)));
}

/** Builds a Nostr event of the MWA kind and signs it with `privateKey`. */
function signEvent(content: string, tags: string[][], privateKey: Uint8Array): NostrEvent {
    const unsigned = {
        pubkey: bytesToHex(secp256k1Schnorr.getPublicKey(privateKey)),
        created_at: Math.floor(Date.now() / 1000),
        kind: NOSTR_EVENT_KIND_MWA,
        tags,
        content,
    };
    const id = eventId(unsigned);
    return { ...unsigned, id, sig: bytesToHex(secp256k1Schnorr.sign(hexToBytes(id), privateKey)) };
}

/** Whether a Nostr event's id and Schnorr signature are valid. */
function verifyEvent(event: NostrEvent): boolean {
    try {
        return (
            eventId(event) === event.id &&
            secp256k1Schnorr.verify(hexToBytes(event.sig), hexToBytes(event.id), hexToBytes(event.pubkey))
        );
    } catch {
        return false;
    }
}

/** HELLO_REQ verification, ECDH, HKDF-SHA256 and AES-128-GCM with sequence numbers (MWA spec, session establishment). */
class SessionCrypto {
    #sharedKey: CryptoKey | undefined;
    #sequenceNumber = 0;

    readonly associationPublicKey: Uint8Array;

    constructor(associationPublicKey: Uint8Array) {
        this.associationPublicKey = associationPublicKey;
    }

    get established() {
        return this.#sharedKey !== undefined;
    }

    async handleHelloReq(helloReq: Uint8Array): Promise<Uint8Array | undefined> {
        const dappPublicKey = helloReq.subarray(0, P256_PUBLIC_KEY_BYTES);
        const associationKey = await crypto.subtle.importKey(
            'raw',
            this.associationPublicKey as BufferSource,
            { name: 'ECDSA', namedCurve: 'P-256' },
            false,
            ['verify'],
        );
        const valid = await crypto.subtle.verify(
            { name: 'ECDSA', hash: 'SHA-256' },
            associationKey,
            helloReq.subarray(P256_PUBLIC_KEY_BYTES) as BufferSource,
            dappPublicKey as BufferSource,
        );
        if (!valid) return undefined;
        const dappEcdhKey = await crypto.subtle.importKey(
            'raw',
            dappPublicKey as BufferSource,
            { name: 'ECDH', namedCurve: 'P-256' },
            false,
            [],
        );
        const walletKeypair = await crypto.subtle.generateKey({ name: 'ECDH', namedCurve: 'P-256' }, false, [
            'deriveBits',
        ]);
        const sharedBits = await crypto.subtle.deriveBits(
            { name: 'ECDH', public: dappEcdhKey },
            walletKeypair.privateKey,
            256,
        );
        const hkdfKey = await crypto.subtle.importKey('raw', sharedBits, 'HKDF', false, ['deriveKey']);
        this.#sharedKey = await crypto.subtle.deriveKey(
            { name: 'HKDF', hash: 'SHA-256', salt: this.associationPublicKey as BufferSource, info: new Uint8Array() },
            hkdfKey,
            { name: 'AES-GCM', length: 128 },
            false,
            ['encrypt', 'decrypt'],
        );
        const walletPublicKey = new Uint8Array(await crypto.subtle.exportKey('raw', walletKeypair.publicKey));
        return concat(walletPublicKey, await this.encrypt(JSON.stringify({ v: 'v1' })));
    }

    async encrypt(plaintext: string): Promise<Uint8Array> {
        const sequence = new Uint8Array(SEQUENCE_NUMBER_BYTES);
        new DataView(sequence.buffer).setUint32(0, ++this.#sequenceNumber, false);
        const iv = crypto.getRandomValues(new Uint8Array(IV_BYTES));
        const ciphertext = await crypto.subtle.encrypt(
            { name: 'AES-GCM', iv, additionalData: sequence, tagLength: 128 },
            this.#sharedKey!,
            new TextEncoder().encode(plaintext),
        );
        return concat(sequence, iv, new Uint8Array(ciphertext));
    }

    async decrypt(message: Uint8Array): Promise<string> {
        const sequence = message.subarray(0, SEQUENCE_NUMBER_BYTES);
        const iv = message.subarray(SEQUENCE_NUMBER_BYTES, SEQUENCE_NUMBER_BYTES + IV_BYTES);
        const plaintext = await crypto.subtle.decrypt(
            { name: 'AES-GCM', iv: iv as BufferSource, additionalData: sequence as BufferSource, tagLength: 128 },
            this.#sharedKey!,
            message.subarray(SEQUENCE_NUMBER_BYTES + IV_BYTES) as BufferSource,
        );
        return new TextDecoder().decode(plaintext);
    }
}

/** Signs the fee payer slot of a single-signer wire transaction. */
function signTransaction(transaction: Uint8Array, secretKey: Uint8Array): Uint8Array {
    if (transaction[0] !== 1) throw new Error('expected exactly one signature slot');
    const signed = transaction.slice();
    signed.set(ed25519.sign(transaction.subarray(65), secretKey), 1);
    return signed;
}

export class FakeWallet {
    readonly secretKey = ed25519.utils.randomSecretKey();
    readonly publicKey = ed25519.getPublicKey(this.secretKey);
    readonly address = base58Encode(this.publicKey);
    readonly authToken = 'e2e-auth-token';
    /** Decrypted requests, in order. */
    readonly requests: JsonRpcRequest[] = [];
    readonly errors: string[] = [];

    #nostrKey = secp256k1Schnorr.utils.randomSecretKey();
    #nostrPubkey = bytesToHex(secp256k1Schnorr.getPublicKey(this.#nostrKey));
    #sockets = new Set<WebSocket>();

    /** Joins the session described by a Nostr association URL, as a wallet does after scanning the QR code. */
    serve(associationUrl: string): void {
        const url = new URL(associationUrl);
        const association = url.searchParams.get('association');
        const dappPubkey = url.searchParams.get('pubkey');
        const relay = url.searchParams.get('relay');
        if (!association || !dappPubkey || !relay) {
            this.errors.push(`not a Nostr association URL: ${associationUrl}`);
            return;
        }
        // Reverse MWA's URL-safe base64 substitutions.
        const associationPublicKey = fromBase64(
            association.replace(/[_\-.]/g, (c) => ({ _: '/', '-': '+', '.': '=' })[c]!),
        );
        const sessionId = bytesToHex(sha256(associationPublicKey));
        const session = new SessionCrypto(associationPublicKey);

        const socket = new WebSocket(`wss://${relay}`, { rejectUnauthorized: false });
        this.#sockets.add(socket);
        const publish = (content: string) =>
            socket.send(
                JSON.stringify([
                    'EVENT',
                    signEvent(
                        content,
                        [
                            ['d', sessionId],
                            ['p', dappPubkey],
                        ],
                        this.#nostrKey,
                    ),
                ]),
            );

        socket.on('open', () => {
            socket.send(
                JSON.stringify([
                    'REQ',
                    `wallet-${sessionId.slice(0, 8)}`,
                    { kinds: [NOSTR_EVENT_KIND_MWA], '#d': [sessionId] },
                ]),
            );
            setTimeout(() => publish(''), ANNOUNCE_DELAY_MS);
        });
        socket.on('message', (data) => {
            void (async () => {
                const [type, , event] = JSON.parse(String(data)) as [string, string, NostrEvent];
                if (type !== 'EVENT' || event.pubkey === this.#nostrPubkey) return;
                if (!verifyEvent(event)) {
                    this.errors.push('dapp sent an invalid Nostr event');
                    return;
                }
                if (event.tags.some(([key, value]) => key === 'msg' && value === 'SESSION_END')) {
                    socket.close();
                    return;
                }
                if (!session.established) {
                    const helloRsp = await session.handleHelloReq(fromBase64(event.content));
                    if (helloRsp) publish(toBase64(helloRsp));
                    else this.errors.push('HELLO_REQ signature did not verify');
                    return;
                }
                const request = JSON.parse(await session.decrypt(fromBase64(event.content))) as JsonRpcRequest;
                this.requests.push(request);
                const body = this.#handle(request);
                publish(toBase64(await session.encrypt(JSON.stringify({ jsonrpc: '2.0', id: request.id, ...body }))));
            })().catch((error: unknown) => this.errors.push(error instanceof Error ? error.message : String(error)));
        });
    }

    close(): void {
        for (const socket of this.#sockets) socket.terminate();
        this.#sockets.clear();
    }

    #handle(request: JsonRpcRequest): Record<string, unknown> {
        const params = request.params;
        switch (request.method) {
            case 'get_capabilities':
                return {
                    result: {
                        max_transactions_per_request: 10,
                        max_messages_per_request: 10,
                        supported_transaction_versions: ['legacy', 0],
                        features: ['solana:signTransactions', 'solana:signInWithSolana'],
                        supports_clone_authorization: false,
                        supports_sign_and_send_transactions: true,
                    },
                };
            case 'authorize': {
                if (params.auth_token !== undefined && params.auth_token !== this.authToken) {
                    return { error: { code: -1, message: 'auth_token not valid for this wallet' } };
                }
                const signInPayload = params.sign_in_payload as Parameters<typeof createSignInMessage>[0] | undefined;
                let signInResult;
                if (signInPayload) {
                    const message = createSignInMessage({ ...signInPayload, address: this.address });
                    signInResult = {
                        address: toBase64(this.publicKey),
                        signed_message: toBase64(message),
                        signature: toBase64(ed25519.sign(message, this.secretKey)),
                    };
                }
                return {
                    result: {
                        accounts: [{ address: toBase64(this.publicKey), label: 'E2E Account' }],
                        auth_token: this.authToken,
                        ...(signInResult ? { sign_in_result: signInResult } : {}),
                    },
                };
            }
            case 'deauthorize':
                return { result: {} };
            case 'sign_messages':
                // A signed message is the message followed by its signature.
                return {
                    result: {
                        signed_payloads: (params.payloads as string[]).map((payload) => {
                            const message = fromBase64(payload);
                            return toBase64(concat(message, ed25519.sign(message, this.secretKey)));
                        }),
                    },
                };
            case 'sign_transactions':
                return {
                    result: {
                        signed_payloads: (params.payloads as string[]).map((payload) =>
                            toBase64(signTransaction(fromBase64(payload), this.secretKey)),
                        ),
                    },
                };
            case 'sign_and_send_transactions':
                return {
                    result: {
                        signatures: (params.payloads as string[]).map((payload) =>
                            toBase64(signTransaction(fromBase64(payload), this.secretKey).subarray(1, 65)),
                        ),
                    },
                };
            default:
                return { error: { code: -32601, message: `Method not found: ${request.method}` } };
        }
    }
}
