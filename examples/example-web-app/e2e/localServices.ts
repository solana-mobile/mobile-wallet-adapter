/**
 * Local stand-ins for the network services the example talks to, so the e2e needs no secrets or
 * outside hosts:
 *
 * - a Nostr relay over wss with a throwaway self-signed certificate (MWA only connects to wss://)
 * - a Solana JSON-RPC stub that answers the handful of methods the app calls
 */
import { execFileSync } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createServer as createHttpServer, type Server } from 'node:http';
import { createServer as createHttpsServer } from 'node:https';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

import { type WebSocket, WebSocketServer } from 'ws';

import { base58Encode } from '../src/base58.ts';

interface Filter {
    kinds?: number[];
    '#d'?: string[];
    '#p'?: string[];
}

interface Event {
    kind: number;
    tags: string[][];
}

/** Whether an event matches a relay subscription filter (kinds, and #d and #p tags). */
function matches(filter: Filter, event: Event): boolean {
    const tag = (name: string) => event.tags.filter(([key]) => key === name).map(([, value]) => value);
    if (filter.kinds && !filter.kinds.includes(event.kind)) return false;
    if (filter['#d'] && !tag('d').some((value) => filter['#d']!.includes(value))) return false;
    if (filter['#p'] && !tag('p').some((value) => filter['#p']!.includes(value))) return false;
    return true;
}

/** Starts the server on a free local port and resolves with that port. */
function listen(server: Server): Promise<number> {
    return new Promise((resolve) => {
        server.listen(0, '127.0.0.1', () => {
            const address = server.address();
            resolve(typeof address === 'object' && address ? address.port : 0);
        });
    });
}

/** A minimal NIP-01 relay: forwards each EVENT to the subscriptions it matches and stores nothing. */
export async function startRelay(): Promise<{ domain: string; close: () => Promise<void> }> {
    const certDir = mkdtempSync(join(tmpdir(), 'mwa-e2e-relay-'));
    const key = join(certDir, 'key.pem');
    const cert = join(certDir, 'cert.pem');
    execFileSync(
        'openssl',
        [
            'req',
            '-x509',
            '-newkey',
            'rsa:2048',
            '-nodes',
            '-days',
            '1',
            '-subj',
            '/CN=localhost',
            '-keyout',
            key,
            '-out',
            cert,
        ],
        { stdio: 'ignore' },
    );
    const server = createHttpsServer({ key: readFileSync(key), cert: readFileSync(cert) });
    rmSync(certDir, { recursive: true, force: true });

    const subscriptions = new Map<WebSocket, Map<string, Filter[]>>();
    const wss = new WebSocketServer({ server });
    wss.on('connection', (socket) => {
        const own = new Map<string, Filter[]>();
        subscriptions.set(socket, own);
        socket.on('close', () => subscriptions.delete(socket));
        socket.on('message', (data) => {
            const [type, ...rest] = JSON.parse(String(data)) as [string, ...unknown[]];
            if (type === 'REQ') {
                const [id, ...filters] = rest as [string, ...Filter[]];
                own.set(id, filters);
                socket.send(JSON.stringify(['EOSE', id]));
            } else if (type === 'CLOSE') {
                own.delete(rest[0] as string);
            } else if (type === 'EVENT') {
                const event = rest[0] as Event & { id: string };
                socket.send(JSON.stringify(['OK', event.id, true, '']));
                for (const [peer, peerSubscriptions] of subscriptions) {
                    for (const [id, filters] of peerSubscriptions) {
                        if (filters.some((filter) => matches(filter, event))) {
                            peer.send(JSON.stringify(['EVENT', id, event]));
                        }
                    }
                }
            }
        });
    });

    const port = await listen(server);
    return {
        domain: `127.0.0.1:${port}`,
        close: () =>
            new Promise((resolve) => {
                for (const client of wss.clients) client.terminate();
                wss.close(() => server.close(() => resolve()));
            }),
    };
}

/** Answers the JSON-RPC methods the app uses. Every signature reports as confirmed. */
export async function startRpcStub(): Promise<{ url: string; airdrops: string[]; close: () => Promise<void> }> {
    const balances = new Map<string, number>();
    const airdrops: string[] = [];
    let airdropCount = 0;

    const handlers: Record<string, (params: unknown[]) => unknown> = {
        getLatestBlockhash: () => ({
            context: { slot: 1 },
            value: { blockhash: base58Encode(new Uint8Array(32).fill(7)), lastValidBlockHeight: 1000 },
        }),
        getBalance: ([address]) => ({ context: { slot: 1 }, value: balances.get(address as string) ?? 0 }),
        requestAirdrop: ([address, lamports]) => {
            balances.set(address as string, (balances.get(address as string) ?? 0) + (lamports as number));
            airdrops.push(address as string);
            return base58Encode(new Uint8Array(64).fill(++airdropCount));
        },
        getSignatureStatuses: ([signatures]) => ({
            context: { slot: 1 },
            value: (signatures as string[]).map(() => ({ err: null, confirmationStatus: 'confirmed' })),
        }),
    };

    const server = createHttpServer((request, response) => {
        response.setHeader('access-control-allow-origin', '*');
        response.setHeader('access-control-allow-headers', 'content-type');
        if (request.method === 'OPTIONS') {
            response.end();
            return;
        }
        let body = '';
        request.on('data', (chunk) => (body += chunk));
        request.on('end', () => {
            const { id, method, params } = JSON.parse(body) as { id: number; method: string; params: unknown[] };
            const handler = handlers[method];
            response.setHeader('content-type', 'application/json');
            response.end(
                JSON.stringify(
                    handler
                        ? { jsonrpc: '2.0', id, result: handler(params ?? []) }
                        : { jsonrpc: '2.0', id, error: { code: -32601, message: `Method not found: ${method}` } },
                ),
            );
        });
    });

    const port = await listen(server);
    return {
        url: `http://127.0.0.1:${port}`,
        airdrops,
        close: () => new Promise((resolve) => server.close(() => resolve())),
    };
}
