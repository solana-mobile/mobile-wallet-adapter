/**
 * Browser e2e for the example app.
 *
 * The real app runs in Chrome, driven by agent-browser. It talks to a local Nostr relay and a local
 * JSON-RPC stub (see localServices.ts), and a fake wallet in this process joins each session over
 * the relay. The one test-only seam is the association URL capture in vite.config.ts: a desktop
 * test browser has no camera, so the fake wallet reads the URL the QR code would show.
 *
 * Run from examples/example-web-app with `pnpm e2e`. Needs `openssl` and agent-browser's Chrome
 * (`pnpm exec agent-browser install`).
 */
import { execFileSync, spawn } from 'node:child_process';
import { setTimeout as sleep } from 'node:timers/promises';
import { fileURLToPath } from 'node:url';

import { ed25519 } from '@noble/curves/ed25519.js';

import { base58Decode } from '../src/base58.ts';
import { FakeWallet } from './fakeWallet.ts';
import { startRelay, startRpcStub } from './localServices.ts';

const APP_DIR = fileURLToPath(new URL('..', import.meta.url));
const AGENT_BROWSER = fileURLToPath(new URL('../node_modules/.bin/agent-browser', import.meta.url));
const VITE = fileURLToPath(new URL('../node_modules/.bin/vite', import.meta.url));
const APP_ORIGIN = 'http://localhost:3002';
const SESSION = 'mwa-example-e2e';

function ab(...args: string[]): string {
    return execFileSync(AGENT_BROWSER, ['--session', SESSION, ...args], {
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
    }).trim();
}

function evalInPage<T>(expression: string): T {
    const envelope = JSON.parse(ab('eval', '--json', expression)) as {
        success: boolean;
        data: { result: T };
        error: string | null;
    };
    if (!envelope.success) throw new Error(envelope.error ?? 'eval failed');
    return envelope.data.result;
}

const text = (testId: string) =>
    evalInPage<string | null>(`document.querySelector('[data-testid=${testId}]')?.textContent ?? null`);
const exists = (testId: string) => text(testId) !== null;

async function until<T>(
    label: string,
    probe: () => T | false | undefined | null | Promise<T | false | undefined | null>,
    timeoutMs = 30_000,
): Promise<T> {
    const deadline = Date.now() + timeoutMs;
    for (;;) {
        try {
            const value = await probe();
            if (value !== undefined && value !== null && value !== false) return value;
        } catch {
            // The page may be mid-navigation; keep polling.
        }
        if (Date.now() > deadline) throw new Error(`Timed out waiting for ${label}`);
        await sleep(300);
    }
}

/** Waits for a new activity log entry starting with `prefix` and returns its text. */
function logEntry(prefix: string): Promise<string> {
    return until(`"${prefix}" in the activity log`, () =>
        evalInPage<string | null>(
            `[...document.querySelectorAll('[data-testid=log] li span')].map((e) => e.textContent).find((t) => t.startsWith(${JSON.stringify(prefix)})) ?? null`,
        ),
    );
}

let passed = 0;
function check(label: string, condition: boolean): void {
    if (!condition) throw new Error(`Check failed: ${label}`);
    passed += 1;
    console.log(`  ✓ ${label}`);
}

async function main(): Promise<void> {
    const relay = await startRelay();
    const rpc = await startRpcStub();
    const wallet = new FakeWallet();

    const vite = spawn(VITE, ['--port', '3002', '--strictPort'], {
        cwd: APP_DIR,
        env: { ...process.env, MWA_E2E: '1' },
        stdio: 'ignore',
    });
    let stopping = false;
    vite.on('exit', (code) => {
        if (!stopping) {
            console.error(`vite exited early (code ${code}); is something else using port 3002?`);
            process.exit(1);
        }
    });

    // Hand every association URL the app shows to the fake wallet.
    const served = new Set<string>();
    let pumping = true;
    const pump = (async () => {
        while (pumping) {
            try {
                for (const url of evalInPage<string[]>('window.__mwaE2eAssociationUrls ?? []')) {
                    if (!served.has(url)) {
                        served.add(url);
                        wallet.serve(url);
                    }
                }
            } catch {
                // No page yet.
            }
            await sleep(300);
        }
    })();

    try {
        await until('the vite dev server', async () => (await fetch(APP_ORIGIN)).ok);

        console.log('discovery');
        ab(
            'open',
            '--ignore-https-errors',
            '--args',
            '--no-sandbox',
            `${APP_ORIGIN}/?relay=${relay.domain}&rpc=${rpc.url}`,
        );
        await until('the wallet status', () => text('wallet-status')?.includes('Remote Mobile Wallet Adapter'));
        check('the remote MWA wallet is registered on a desktop browser', true);

        console.log('connect');
        ab('click', '[data-testid=connect]');
        await until('the connected account', () => exists('account-address'));
        check("connect shows the wallet's account", text('account-address') === wallet.address);
        check(
            'connect authorized through the fake wallet',
            wallet.requests.some((r) => r.method === 'authorize'),
        );
        await until('the balance', () => text('balance') === '◎0');
        check('the balance loads from the RPC', true);

        console.log('fund account');
        ab('click', '[data-testid=fund-account]');
        await logEntry('Funding successful');
        check('the airdrop went to the connected account', rpc.airdrops.at(-1) === wallet.address);
        await until('the new balance', () => text('balance') === '◎0.1');
        check('the balance refreshes after funding', true);

        const message = `gm from the e2e at ${Date.now()}`;
        ab('fill', '[data-testid=message]', message);

        console.log('sign message');
        ab('click', '[data-testid=sign-message]');
        const signMessageEntry = await logEntry('Message signed: ');
        const messageSignature = base58Decode(signMessageEntry.split(' ').at(-1)!);
        check(
            'the message signature verifies against the account',
            ed25519.verify(messageSignature, new TextEncoder().encode(message), wallet.publicKey),
        );

        console.log('sign transaction');
        ab('click', '[data-testid=sign-transaction]');
        const signTransactionEntry = await logEntry('Transaction signed: ');
        const [signRequest] = wallet.requests.filter((r) => r.method === 'sign_transactions').slice(-1);
        const transaction = Buffer.from((signRequest!.params.payloads as string[])[0]!, 'base64');
        check('the memo transaction carries the message', transaction.toString('utf8').includes(message));
        check('the wallet supports v0, so the app sent a v0 transaction', transaction[65] === 0x80);
        check(
            'the app shows the fee payer signature from the signed transaction',
            ed25519.verify(
                base58Decode(signTransactionEntry.split(' ').at(-1)!),
                transaction.subarray(65),
                wallet.publicKey,
            ),
        );

        console.log('record message');
        ab('click', '[data-testid=record-message]');
        const recordEntry = await logEntry('Message recorded');
        check('the app reports the recorded message', recordEntry === 'Message recorded');
        check(
            'the explorer link points at the sign-and-send signature',
            /\/tx\/\w{80,90}\?cluster=devnet$/.test(
                evalInPage<string>(`document.querySelector('[data-testid=log] li a').href`),
            ),
        );

        console.log('disconnect');
        ab('click', '[data-testid=disconnect]');
        await until('the connect button', () => exists('connect'));
        check('disconnect returns to the connect screen', !exists('account-address'));

        console.log('sign in');
        const sessionsBefore = served.size;
        ab('click', '[data-testid=sign-in]');
        const signInEntry = await logEntry('Sign in');
        check('sign-in opens a new wallet session', served.size > sessionsBefore);
        check(
            'the app verifies the sign-in message and signature',
            signInEntry === `Sign in ✓ verified ${wallet.address}`,
        );
        check('sign-in connects the account', text('account-address') === wallet.address);

        check('the fake wallet saw no protocol errors', wallet.errors.length === 0);
        console.log(`\n${passed} checks passed`);
    } finally {
        if (wallet.errors.length) console.error('fake wallet errors:', wallet.errors);
        pumping = false;
        await pump;
        stopping = true;
        try {
            ab('close');
        } catch {
            // Already closed.
        }
        vite.kill();
        wallet.close();
        await Promise.all([relay.close(), rpc.close()]);
    }
}

main().catch((error: unknown) => {
    console.error(error instanceof Error ? error.message : error);
    process.exit(1);
});
