/** The few Solana JSON-RPC calls the example makes, over plain fetch. */
import { base58Decode } from './base58.js';

export const LAMPORTS_PER_SOL = 1_000_000_000;

async function rpc<T>(url: string, method: string, params: unknown[] = []): Promise<T> {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ jsonrpc: '2.0', id: 1, method, params }),
    });
    if (!response.ok) throw new Error(`${method} failed: HTTP ${response.status}`);
    const body = (await response.json()) as { result?: T; error?: { message: string } };
    if (body.error) throw new Error(`${method} failed: ${body.error.message}`);
    return body.result as T;
}

export async function getLatestBlockhash(url: string): Promise<Uint8Array> {
    const { value } = await rpc<{ value: { blockhash: string } }>(url, 'getLatestBlockhash', [
        { commitment: 'confirmed' },
    ]);
    return base58Decode(value.blockhash);
}

export async function getBalance(url: string, address: string): Promise<number> {
    const { value } = await rpc<{ value: number }>(url, 'getBalance', [address, { commitment: 'confirmed' }]);
    return value;
}

export function requestAirdrop(url: string, address: string, lamports: number): Promise<string> {
    return rpc<string>(url, 'requestAirdrop', [address, lamports]);
}

/** Polls until the signature is confirmed. Rejects if it fails or is still unconfirmed after `timeoutMs`. */
export async function confirmSignature(url: string, signature: string, timeoutMs = 30_000, pollMs = 1_000) {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        const { value } = await rpc<{
            value: ({ err: unknown; confirmationStatus?: string } | null)[];
        }>(url, 'getSignatureStatuses', [[signature]]);
        const status = value[0];
        if (status?.err) throw new Error(`Transaction failed: ${JSON.stringify(status.err)}`);
        if (status?.confirmationStatus === 'confirmed' || status?.confirmationStatus === 'finalized') return;
        if (Date.now() >= deadline) throw new Error('Timed out waiting for confirmation');
        await new Promise((resolve) => setTimeout(resolve, pollMs));
    }
}
