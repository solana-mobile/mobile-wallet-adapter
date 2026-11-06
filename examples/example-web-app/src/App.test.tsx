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
    features: ['solana:signAndSendTransaction', 'solana:signTransaction', 'solana:signMessage', 'solana:signIn'],
};

function createFakeWallet({ name = SolanaMobileWalletAdapterRemoteWalletName, tamperSignIn = false } = {}) {
    let accounts: WalletAccount[] = [];
    const listeners = new Set<(properties: { accounts?: WalletAccount[] }) => void>();
    const wallet = {
        version: '1.0.0',
        name,
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
const unregisters: (() => void)[] = [];

beforeEach(() => {
    vi.stubGlobal(
        'fetch',
        vi.fn(async (_url: string, init: RequestInit) => {
            const { method } = JSON.parse(init.body as string);
            const results: Record<string, unknown> = {
                getBalance: { value: 2_500_000_000 },
                getLatestBlockhash: {
                    context: { slot: 11 },
                    value: { blockhash: base58Encode(new Uint8Array(32).fill(3)), lastValidBlockHeight: 500 },
                },
                getSignatureStatuses: { value: [{ err: null, confirmationStatus: 'confirmed' }] },
                requestAirdrop: base58Encode(new Uint8Array(64).fill(5)),
                sendTransaction: base58Encode(new Uint8Array(64).fill(6)),
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
    unregisters.splice(0).forEach((unregister) => unregister());
    vi.unstubAllGlobals();
});

async function render(...wallets: Wallet[]) {
    for (const wallet of wallets) unregisters.push(getWallets().register(wallet));
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
    it('explains when no wallet is available', async () => {
        await render();

        expect(byTestId('wallet-status')?.textContent).toContain('No Solana wallet was found');
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
        const toast = byTestId('toast');
        expect(toast?.getAttribute('role')).toBe('alert');
        expect(toast?.textContent).toContain('Sign in ✗ Sign-in verification failed');
    });

    it('lists every Solana wallet, Mobile Wallet Adapter first, and connects the chosen one', async () => {
        const mwa = createFakeWallet();
        const extension = createFakeWallet({ name: 'Extension Wallet' });
        await render(extension, mwa);

        const select = byTestId('wallet-select') as HTMLSelectElement;
        expect([...select.options].map((option) => option.value)).toEqual([
            SolanaMobileWalletAdapterRemoteWalletName,
            'Extension Wallet',
        ]);
        expect(select.value).toBe(SolanaMobileWalletAdapterRemoteWalletName);

        await act(async () => {
            select.value = 'Extension Wallet';
            select.dispatchEvent(new Event('change', { bubbles: true }));
        });
        await click('connect');

        expect(extension.features['standard:connect'].connect).toHaveBeenCalledTimes(1);
        expect(mwa.features['standard:connect'].connect).not.toHaveBeenCalled();
        expect(byTestId('wallet-status')?.textContent).toBe('Extension Wallet · solana:devnet');
    });

    it('disables actions the wallet does not support', async () => {
        const wallet = createFakeWallet();
        const features = wallet.features as Record<string, unknown>;
        delete features['solana:signIn'];
        delete features['solana:signMessage'];
        await render(wallet);

        expect((byTestId('sign-in') as HTMLButtonElement).disabled).toBe(true);

        await click('connect');
        await typeMessage('gm');

        expect((byTestId('sign-message') as HTMLButtonElement).disabled).toBe(true);
        expect((byTestId('sign-transaction') as HTMLButtonElement).disabled).toBe(false);
    });

    it('disables actions the connected account does not support', async () => {
        const wallet = createFakeWallet();
        wallet.features['standard:connect'].connect.mockResolvedValueOnce({
            accounts: [{ ...account, features: ['solana:signTransaction'] }],
        });
        await render(wallet);
        await click('connect');
        await typeMessage('gm');

        expect((byTestId('sign-message') as HTMLButtonElement).disabled).toBe(true);
        expect((byTestId('sign-transaction') as HTMLButtonElement).disabled).toBe(false);
        // Can still record: signed by the wallet, sent over RPC.
        expect((byTestId('record-message') as HTMLButtonElement).disabled).toBe(false);
    });

    it('records through RPC when the wallet can sign but not send', async () => {
        const wallet = createFakeWallet();
        delete (wallet.features as Record<string, unknown>)['solana:signAndSendTransaction'];
        await render(wallet);
        await click('connect');
        await typeMessage('sign only');

        await click('record-message');

        const sent = base58Encode(new Uint8Array(64).fill(6));
        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain(`Record message ✓ ${sent}`));
        const [[{ options }]] = wallet.features['solana:signTransaction'].signTransaction.mock.calls as unknown as [
            [{ options: unknown }],
        ];
        expect(options).toEqual({ preflightCommitment: 'confirmed', minContextSlot: 11 });
        const methods = vi.mocked(fetch).mock.calls.map(([, init]) => JSON.parse(init!.body as string).method);
        expect(methods).toContain('sendTransaction');
    });

    it('passes the blockhash context slot to sign and send', async () => {
        const wallet = createFakeWallet();
        await render(wallet);
        await click('connect');
        await typeMessage('on chain');

        await click('record-message');

        await vi.waitFor(() => expect(byTestId('log')?.textContent).toContain('Record message ✓'));
        const [[input]] = wallet.features['solana:signAndSendTransaction'].signAndSendTransaction.mock
            .calls as unknown as [[{ options: unknown }]];
        expect(input.options).toEqual({ preflightCommitment: 'confirmed', minContextSlot: 11 });
    });

    it('names wallet failures and never shows [object Object]', async () => {
        const wallet = createFakeWallet();
        wallet.features['standard:connect'].connect.mockRejectedValueOnce({ code: 4001 });
        wallet.features['solana:signMessage'].signMessage.mockRejectedValueOnce(
            new Error('declined', { cause: Object.assign(new Error('declined'), { code: -3 }) }),
        );
        await render(wallet);

        await click('connect');
        expect(await logText()).toContain('Connect ✗ WalletConnectionError: {"code":4001}');

        await click('connect');
        await typeMessage('gm');
        await click('sign-message');

        await vi.waitFor(() =>
            expect(byTestId('log')?.textContent).toContain('Sign message ✗ WalletSignMessageError: declined (-3)'),
        );
        expect(byTestId('log')?.textContent).not.toContain('[object');
    });

    it('clears the account when the wallet fails to disconnect', async () => {
        const wallet = createFakeWallet();
        wallet.features['standard:disconnect'].disconnect.mockRejectedValueOnce(new Error('socket gone'));
        await render(wallet);
        await click('connect');

        await click('disconnect');

        expect(byTestId('account-address')).toBeNull();
        expect(byTestId('connect')).not.toBeNull();
        await vi.waitFor(() =>
            expect(byTestId('log')?.textContent).toContain('Disconnect ✗ WalletDisconnectionError: socket gone'),
        );
    });
});
