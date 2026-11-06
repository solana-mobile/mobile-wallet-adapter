import { afterEach, describe, expect, it, vi } from 'vitest';

import { confirmSignature, getLatestBlockhash, sendTransaction } from './rpc.js';

function stubRpc(handler: (method: string, params: unknown[]) => unknown) {
    const fetchMock = vi.fn(async (_url: string, init: RequestInit) => {
        const { method, params } = JSON.parse(init.body as string);
        const result = handler(method, params);
        return new Response(
            JSON.stringify(result instanceof Error ? { error: { code: -32005, message: result.message } } : { result }),
        );
    });
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
}

afterEach(() => {
    vi.unstubAllGlobals();
});

describe('confirmSignature', () => {
    it('polls until the signature is confirmed', async () => {
        const statuses = [
            null,
            { err: null, confirmationStatus: 'processed' },
            { err: null, confirmationStatus: 'confirmed' },
        ];
        const fetchMock = stubRpc(() => ({ value: [statuses.shift()] }));

        await confirmSignature('http://rpc.test', 'sig', { timeoutMs: 1_000, pollMs: 0 });

        expect(fetchMock).toHaveBeenCalledTimes(3);
    });

    it('rejects when the transaction failed', async () => {
        stubRpc(() => ({ value: [{ err: { InstructionError: [0, 'Custom'] } }] }));

        await expect(confirmSignature('http://rpc.test', 'sig', { pollMs: 0 })).rejects.toThrow('Transaction failed');
    });

    it('rejects after the timeout', async () => {
        stubRpc(() => ({ value: [null] }));

        await expect(confirmSignature('http://rpc.test', 'sig', { timeoutMs: 0, pollMs: 0 })).rejects.toThrow(
            'Timed out waiting for confirmation',
        );
    });

    it('rejects once the blockhash has expired, not after the timeout', async () => {
        const heights = [99, 100, 101];
        const fetchMock = stubRpc((method) => (method === 'getBlockHeight' ? heights.shift() : { value: [null] }));

        await expect(
            confirmSignature('http://rpc.test', 'sig', { lastValidBlockHeight: 100, timeoutMs: 0, pollMs: 0 }),
        ).rejects.toThrow('Transaction expired: block height 101 passed 100');
        expect(fetchMock).toHaveBeenCalledTimes(6);
    });
});

describe('rpc errors', () => {
    it('surfaces JSON-RPC errors with their code', async () => {
        stubRpc(() => new Error('node is behind'));

        await expect(getLatestBlockhash('http://rpc.test')).rejects.toThrow(
            'getLatestBlockhash failed: node is behind (-32005)',
        );
    });

    it('bounds every request with a timeout and names the method when it fails', async () => {
        const timeout = new DOMException('signal timed out', 'TimeoutError');
        const fetchMock = vi.fn(async (_url: string, init: RequestInit) => {
            expect(init.signal).toBeInstanceOf(AbortSignal);
            throw timeout;
        });
        vi.stubGlobal('fetch', fetchMock);

        await expect(confirmSignature('http://rpc.test', 'sig', { lastValidBlockHeight: 100 })).rejects.toMatchObject({
            message: 'getSignatureStatuses failed',
            cause: timeout,
        });
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    it('gives every request its own id', async () => {
        const fetchMock = stubRpc(() => ({ context: { slot: 1 }, value: { blockhash: '1', lastValidBlockHeight: 1 } }));

        await Promise.all([getLatestBlockhash('http://rpc.test'), getLatestBlockhash('http://rpc.test')]);

        const ids = fetchMock.mock.calls.map(([, init]) => JSON.parse(init.body as string).id);
        expect(new Set(ids).size).toBe(2);
    });
});

describe('getLatestBlockhash', () => {
    it('returns the blockhash with its expiry height and context slot', async () => {
        stubRpc(() => ({ context: { slot: 7 }, value: { blockhash: '2', lastValidBlockHeight: 300 } }));

        await expect(getLatestBlockhash('http://rpc.test')).resolves.toEqual({
            blockhash: Uint8Array.of(1),
            lastValidBlockHeight: 300,
            slot: 7,
        });
    });
});

describe('sendTransaction', () => {
    it('sends the transaction base64-encoded with the preflight options', async () => {
        const fetchMock = stubRpc(() => 'signature');

        await expect(sendTransaction('http://rpc.test', Uint8Array.of(1, 2, 3), 42)).resolves.toBe('signature');

        expect(JSON.parse(fetchMock.mock.calls[0]![1].body as string).params).toEqual([
            'AQID',
            { encoding: 'base64', preflightCommitment: 'confirmed', minContextSlot: 42 },
        ]);
    });
});
