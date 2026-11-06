/** The few Solana JSON-RPC calls the example makes, over plain fetch. */
import { base58Decode } from './base58.js';

export const LAMPORTS_PER_SOL = 1_000_000_000;
const COMMITMENT = 'confirmed';

let nextRequestId = 1;

/** Sends one JSON-RPC request; rejects on an HTTP error or a JSON-RPC error (with its code), or when `signal` aborts. */
async function rpc<T>(url: string, method: string, params: unknown[] = [], signal?: AbortSignal): Promise<T> {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ jsonrpc: '2.0', id: nextRequestId++, method, params }),
        signal,
    });
    if (!response.ok) throw new Error(`${method} failed: HTTP ${response.status}`);
    const body = (await response.json()) as { result?: T; error?: { code?: number; message: string } };
    if (body.error) {
        const code = body.error.code === undefined ? '' : ` (${body.error.code})`;
        throw new Error(`${method} failed: ${body.error.message}${code}`);
    }
    return body.result as T;
}

export interface LatestBlockhash {
    blockhash: Uint8Array;
    /** The last block height at which a transaction using this blockhash can land. */
    lastValidBlockHeight: number;
    /** The slot the blockhash was read at; pass as `minContextSlot` so preflight doesn't run on an older node. */
    slot: number;
}

/** The latest blockhash, the block height it expires after, and the slot it was read at. */
export async function getLatestBlockhash(url: string): Promise<LatestBlockhash> {
    const { context, value } = await rpc<{
        context: { slot: number };
        value: { blockhash: string; lastValidBlockHeight: number };
    }>(url, 'getLatestBlockhash', [{ commitment: COMMITMENT }]);
    return {
        blockhash: base58Decode(value.blockhash),
        lastValidBlockHeight: value.lastValidBlockHeight,
        slot: context.slot,
    };
}

/** The account's balance in lamports. */
export async function getBalance(url: string, address: string): Promise<number> {
    const { value } = await rpc<{ value: number }>(url, 'getBalance', [address, { commitment: COMMITMENT }]);
    return value;
}

/** Requests a devnet airdrop to `address`; resolves with the airdrop transaction's signature. */
export function requestAirdrop(url: string, address: string, lamports: number): Promise<string> {
    return rpc<string>(url, 'requestAirdrop', [address, lamports]);
}

/** Sends a signed transaction, for wallets that can sign but not send. Returns its signature. */
export function sendTransaction(url: string, transaction: Uint8Array, minContextSlot: number): Promise<string> {
    return rpc<string>(url, 'sendTransaction', [
        btoa(String.fromCharCode(...transaction)),
        { encoding: 'base64', preflightCommitment: COMMITMENT, minContextSlot },
    ]);
}

/**
 * Polls until the signature is confirmed. Rejects if the transaction failed, if its blockhash expired
 * (past `lastValidBlockHeight`, when given), or if it isn't confirmed within `timeoutMs`. A request
 * still in flight at the deadline is aborted.
 */
export async function confirmSignature(
    url: string,
    signature: string,
    {
        lastValidBlockHeight,
        timeoutMs = 30_000,
        pollMs = 1_000,
    }: {
        lastValidBlockHeight?: number;
        timeoutMs?: number;
        pollMs?: number;
    } = {},
) {
    const deadline = Date.now() + timeoutMs;
    const timedOut = (cause?: unknown) => new Error('Timed out waiting for confirmation', { cause });
    for (;;) {
        if (Date.now() >= deadline) throw timedOut();
        // Abort the poll's requests when the deadline passes, so a stalled request can't hold the action.
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), Math.max(0, deadline - Date.now()));
        try {
            const { value } = await rpc<{
                value: ({ err: unknown; confirmationStatus?: string } | null)[];
            }>(url, 'getSignatureStatuses', [[signature]], controller.signal);
            // A status that arrives after the deadline doesn't count.
            if (Date.now() >= deadline) throw timedOut();
            const status = value[0];
            if (status?.err) throw new Error(`Transaction failed: ${JSON.stringify(status.err)}`);
            if (status?.confirmationStatus === 'confirmed' || status?.confirmationStatus === 'finalized') return;
            if (lastValidBlockHeight !== undefined) {
                const blockHeight = await rpc<number>(
                    url,
                    'getBlockHeight',
                    [{ commitment: COMMITMENT }],
                    controller.signal,
                );
                if (blockHeight > lastValidBlockHeight) {
                    throw new Error(`Transaction expired: block height ${blockHeight} passed ${lastValidBlockHeight}`);
                }
            }
            // Never wait past the deadline; the next iteration then times out before polling again.
            await new Promise((resolve) => setTimeout(resolve, Math.min(pollMs, Math.max(0, deadline - Date.now()))));
        } catch (error) {
            if (controller.signal.aborted) throw timedOut(error);
            throw error;
        } finally {
            clearTimeout(timer);
        }
    }
}
