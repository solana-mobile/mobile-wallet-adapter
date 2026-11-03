import { afterEach, describe, expect, it, vi } from 'vitest';

import { confirmSignature, getLatestBlockhash } from './rpc.js';

function stubRpc(handler: (method: string, params: unknown[]) => unknown) {
    const fetchMock = vi.fn(async (_url: string, init: RequestInit) => {
        const { method, params } = JSON.parse(init.body as string);
        const result = handler(method, params);
        return new Response(
            JSON.stringify(result instanceof Error ? { error: { message: result.message } } : { result }),
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

        await confirmSignature('http://rpc.test', 'sig', 1_000, 0);

        expect(fetchMock).toHaveBeenCalledTimes(3);
    });

    it('rejects when the transaction failed', async () => {
        stubRpc(() => ({ value: [{ err: { InstructionError: [0, 'Custom'] } }] }));

        await expect(confirmSignature('http://rpc.test', 'sig', 1_000, 0)).rejects.toThrow('Transaction failed');
    });

    it('rejects after the timeout', async () => {
        stubRpc(() => ({ value: [null] }));

        await expect(confirmSignature('http://rpc.test', 'sig', 0, 0)).rejects.toThrow(
            'Timed out waiting for confirmation',
        );
    });
});

describe('rpc errors', () => {
    it('surfaces JSON-RPC errors', async () => {
        stubRpc(() => new Error('node is behind'));

        await expect(getLatestBlockhash('http://rpc.test')).rejects.toThrow(
            'getLatestBlockhash failed: node is behind',
        );
    });
});
