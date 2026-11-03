import { ed25519 } from '@noble/curves/ed25519.js';
import type { SolanaSignInInput } from '@solana/wallet-standard-features';
import { createSignInMessage } from '@solana/wallet-standard-util';
import { SolanaMobileWalletAdapterRemoteWalletName } from '@solana-mobile/wallet-standard-mobile';
import { getWallets } from '@wallet-standard/app';
import type { Wallet, WalletAccount } from '@wallet-standard/base';
import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { App } from './App.js';
import { base58Encode } from './base58.js';

// Lets React flush act() updates outside a test renderer.
(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const secretKey = ed25519.utils.randomSecretKey();
const publicKey = ed25519.getPublicKey(secretKey);
const account: WalletAccount = {
    address: base58Encode(publicKey),
    publicKey,
    chains: ['solana:devnet'],
    features: [],
};

function createFakeWallet({ tamperSignIn = false } = {}) {
    let accounts: WalletAccount[] = [];
    const listeners = new Set<(properties: { accounts?: WalletAccount[] }) => void>();
    const wallet = {
        version: '1.0.0',
        name: SolanaMobileWalletAdapterRemoteWalletName,
        icon: 'data:image/svg+xml;base64,',
        chains: ['solana:devnet'],
        get accounts() {
            return accounts;
        },
        features: {
            'standard:connect': {
                version: '1.0.0',
                connect: vi.fn(async () => {
                    accounts = [account];
                    return { accounts };
                }),
            },
            'standard:disconnect': {
                version: '1.0.0',
                disconnect: vi.fn(async () => {
                    accounts = [];
                }),
            },
            'standard:events': {
                version: '1.0.0',
                on: (_event: 'change', listener: (properties: { accounts?: WalletAccount[] }) => void) => {
                    listeners.add(listener);
                    return () => listeners.delete(listener);
                },
            },
            'solana:signMessage': {
                version: '1.0.0',
                signMessage: vi.fn(async ({ message }: { message: Uint8Array }) => [
                    { signedMessage: message, signature: ed25519.sign(message, secretKey) },
                ]),
            },
            'solana:signIn': {
                version: '1.0.0',
                signIn: vi.fn(async (input: SolanaSignInInput) => {
                    accounts = [account];
                    const signedMessage = createSignInMessage({
                        ...input,
                        domain: input.domain!,
                        address: account.address,
                    });
                    const signature = ed25519.sign(signedMessage, secretKey);
                    if (tamperSignIn) signature[0] ^= 0xff;
                    return [{ account, signedMessage, signature }];
                }),
            },
            'solana:signTransaction': {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy'],
                signTransaction: vi.fn(async ({ transaction }: { transaction: Uint8Array }) => {
                    const signed = transaction.slice();
                    signed.set(ed25519.sign(transaction.slice(65), secretKey), 1);
                    return [{ signedTransaction: signed }];
                }),
            },
            'solana:signAndSendTransaction': {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy'],
                signAndSendTransaction: vi.fn(async ({ transaction }: { transaction: Uint8Array }) => [
                    { signature: ed25519.sign(transaction.slice(65), secretKey) },
                ]),
            },
        },
    };
    return wallet as typeof wallet & Wallet;
}

let container: HTMLDivElement;
let root: Root;
let unregister: (() => void) | undefined;

beforeEach(() => {
    vi.stubGlobal(
        'fetch',
        vi.fn(async (_url: string, init: RequestInit) => {
            const { method } = JSON.parse(init.body as string);
            const results: Record<string, unknown> = {
                getBalance: { value: 2_500_000_000 },
                getLatestBlockhash: { value: { blockhash: base58Encode(new Uint8Array(32).fill(3)) } },
                getSignatureStatuses: { value: [{ err: null, confirmationStatus: 'confirmed' }] },
                requestAirdrop: base58Encode(new Uint8Array(64).fill(5)),
            };
            return new Response(JSON.stringify({ result: results[method] }));
        }),
    );
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});

afterEach(() => {
    act(() => root.unmount());
    container.remove();
    unregister?.();
    unregister = undefined;
    vi.unstubAllGlobals();
});

async function render(wallet?: Wallet) {
    if (wallet) unregister = getWallets().register(wallet);
    await act(async () => root.render(<App />));
}

const byTestId = (id: string) => container.querySelector<HTMLElement>(`[data-testid="${id}"]`);

async function click(id: string) {
    await act(async () => byTestId(id)!.click());
}

async function typeMessage(text: string) {
    const textarea = byTestId('message') as HTMLTextAreaElement;
    await act(async () => {
        // React tracks the value through the native setter, so set it there before dispatching.
        Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value')!.set!.call(textarea, text);
        textarea.dispatchEvent(new Event('input', { bubbles: true }));
    });
}

async function logText() {
    await vi.waitFor(() => expect(byTestId('log')?.textContent).not.toContain('No activity yet.'));
    return byTestId('log')!.textContent!;
}

describe('App', () => {
    it('explains when Mobile Wallet Adapter is unavailable', async () => {
        await render();

        expect(byTestId('wallet-status')?.textContent).toContain("Mobile Wallet Adapter isn't available");
        expect(byTestId('connect')).toBeNull();
    });

    it('connects, shows the account and balance, then disconnects', async () => {
        const wallet = createFakeWallet();
        await render(wallet);

        expect(byTestId('wallet-status')?.textContent).toBe(
            `${SolanaMobileWalletAdapterRemoteWalletName} · solana:devnet`,
        );

        await click('connect');

        expect(byTestId('account-address')?.textContent).toBe(account.address);
        await vi.waitFor(() => expect(byTestId('balance')?.textContent).toBe('◎2.5'));
        expect(await logText()).toContain(`Connect ✓ ${account.address}`);

        await click('disconnect');

        expect(wallet.features['standard:disconnect'].disconnect).toHaveBeenCalledTimes(1);
        expect(byTestId('connect')).not.toBeNull();
    });

    it('keeps the message actions disabled until a message is entered', async () => {
        await render(createFakeWallet());
        await click('connect');

        for (const id of ['record-message', 'sign-transaction', 'sign-message']) {
            expect((byTestId(id) as HTMLButtonElement).disabled).toBe(true);
        }

        await typeMessage('gm');

        for (const id of ['record-message', 'sign-transaction', 'sign-message']) {
            expect((byTestId(id) as HTMLButtonElement).disabled).toBe(false);
        }
    });

    it('signs the message text', async () => {
        const wallet = createFakeWallet();
        await render(wallet);
        await click('connect');
        await typeMessage('gm from the test');

        await click('sign-message');

        const [[input]] = wallet.features['solana:signMessage'].signMessage.mock.calls;
        expect(new TextDecoder().decode(input.message)).toBe('gm from the test');
        const signature = base58Encode(ed25519.sign(input.message, secretKey));
        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain(`Sign message ✓ ${signature}`));
    });

    it('signs a memo transaction carrying the message', async () => {
        const wallet = createFakeWallet();
        await render(wallet);
        await click('connect');
        await typeMessage('memo text');

        await click('sign-transaction');

        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain('Sign transaction ✓'));
        const [[{ transaction }]] = wallet.features['solana:signTransaction'].signTransaction.mock.calls;
        expect(new TextDecoder().decode(transaction.slice(-9))).toBe('memo text');
        expect(transaction.slice(69, 101)).toEqual(publicKey); // fee payer
    });

    it('records the message and links the confirmed transaction', async () => {
        await render(createFakeWallet());
        await click('connect');
        await typeMessage('on chain');

        await click('record-message');

        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain('Record message ✓'));
        expect(container.querySelector('.log a')?.getAttribute('href')).toMatch(
            /^https:\/\/explorer\.solana\.com\/tx\/\w+\?cluster=devnet$/,
        );
    });

    it('funds the account from the devnet faucet', async () => {
        await render(createFakeWallet());
        await click('connect');

        await click('fund-account');

        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain('Fund account ✓ received ◎0.1'));
    });

    it('verifies a sign-in', async () => {
        await render(createFakeWallet());

        await click('sign-in');

        expect(await logText()).toContain(`Sign in ✓ verified ${account.address}`);
        expect(byTestId('account-address')?.textContent).toBe(account.address);
    });

    it('rejects a sign-in with a bad signature', async () => {
        await render(createFakeWallet({ tamperSignIn: true }));

        await click('sign-in');

        expect(await logText()).toContain('Sign in ✗ Sign-in verification failed');
    });
});
