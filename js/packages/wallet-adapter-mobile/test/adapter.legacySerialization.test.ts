// @vitest-environment node
// Node, not jsdom: under jsdom, @solana/buffer-layout's Uint8Array check fails across realms, so a real
// web3.js Transaction can't be serialized there.

import { SolanaSignAndSendTransaction, SolanaSignTransaction } from '@solana/wallet-standard-features';
import { PublicKey, SystemProgram, Transaction, VersionedMessage } from '@solana/web3.js';
import { StandardEvents } from '@wallet-standard/core';
import { describe, expect, it, vi } from 'vitest';

import { LocalSolanaMobileWalletAdapter } from '../src/adapter.js';

const { walletInstances } = vi.hoisted(() => ({ walletInstances: [] as WalletMock[] }));

type WalletMock = {
    emitAccounts(accounts: unknown[]): void;
    signAndSendImpl: ReturnType<typeof vi.fn>;
    signTransactionImpl: ReturnType<typeof vi.fn>;
};

vi.mock('../src/getIsSupported.js', () => ({ getIsSupported: () => true }));

vi.mock('@solana-mobile/wallet-standard-mobile', async () => {
    const { SolanaSignAndSendTransaction, SolanaSignTransaction } = await import('@solana/wallet-standard-features');
    const { StandardEvents } = await import('@wallet-standard/core');

    class LocalWalletMock {
        currentAuthorization: { chain: string } | undefined;
        icon = 'data:image/svg+xml;base64,icon';
        isAuthorized = false;
        listeners: Array<(properties: { accounts?: unknown[] }) => unknown> = [];
        name = 'Mock Mobile Wallet';
        signAndSendImpl = vi.fn();
        signTransactionImpl = vi.fn();
        url = 'https://example.test/wallet';
        features = {
            [SolanaSignAndSendTransaction]: {
                signAndSendTransaction: async (input: unknown) => await this.signAndSendImpl(input),
            },
            [SolanaSignTransaction]: {
                signTransaction: async (...inputs: unknown[]) => await this.signTransactionImpl(...inputs),
            },
            [StandardEvents]: {
                on: (_event: unknown, listener: (properties: { accounts?: unknown[] }) => unknown) => {
                    this.listeners.push(listener);
                    return () => {};
                },
            },
        };

        constructor() {
            walletInstances.push(this);
        }

        emitAccounts(accounts: unknown[]) {
            this.currentAuthorization = { chain: 'solana:mainnet' };
            this.isAuthorized = true;
            this.listeners.forEach((listener) => listener({ accounts }));
        }
    }

    return {
        LocalSolanaMobileWalletAdapterWallet: LocalWalletMock,
        RemoteSolanaMobileWalletAdapterWallet: class {},
        SolanaMobileWalletAdapterRemoteWalletName: 'Remote Mobile Wallet Adapter',
        SolanaMobileWalletAdapterWalletName: 'Mobile Wallet Adapter',
        createDefaultChainSelector: vi.fn(),
    };
});

async function createConnectedAdapter() {
    const feePayer = new PublicKey(Uint8Array.from({ length: 32 }, (_, index) => index + 1));
    const account = {
        address: feePayer.toBase58(),
        chains: ['solana:mainnet'],
        features: [SolanaSignAndSendTransaction, SolanaSignTransaction, StandardEvents],
        publicKey: feePayer.toBytes(),
    };
    const adapter = new LocalSolanaMobileWalletAdapter({
        addressSelector: { select: async (addresses: string[]) => addresses[0] },
        appIdentity: { name: 'Example App' },
        authorizationResultCache: { clear: vi.fn(), get: vi.fn(), set: vi.fn() },
        chain: 'solana:mainnet',
        onWalletNotFound: vi.fn(),
    });
    const wallet = walletInstances[walletInstances.length - 1];
    wallet.emitAccounts([account]);
    await new Promise((resolve) => setTimeout(resolve, 0));

    const transaction = new Transaction({ feePayer, recentBlockhash: PublicKey.default.toBase58() }).add(
        SystemProgram.transfer({ fromPubkey: feePayer, lamports: 1, toPubkey: feePayer }),
    );
    return { account, adapter, transaction, wallet };
}

describe('unsigned legacy transactions', () => {
    it('are serialized and sent to the wallet by signTransaction', async () => {
        const { account, adapter, transaction, wallet } = await createConnectedAdapter();
        const unsignedBytes = transaction.serialize({ requireAllSignatures: false, verifySignatures: false });
        wallet.signTransactionImpl.mockResolvedValue([{ signedTransaction: unsignedBytes }]);
        vi.spyOn(VersionedMessage, 'deserializeMessageVersion').mockReturnValue('legacy');

        await expect(adapter.signTransaction(transaction)).resolves.toBeInstanceOf(Transaction);
        expect(wallet.signTransactionImpl).toHaveBeenCalledWith(
            expect.objectContaining({ account, transaction: unsignedBytes }),
        );
    });

    it('are serialized and sent to the wallet by sendTransaction', async () => {
        const { account, adapter, transaction, wallet } = await createConnectedAdapter();
        const unsignedBytes = transaction.serialize({ requireAllSignatures: false, verifySignatures: false });
        wallet.signAndSendImpl.mockResolvedValue([{ signature: new Uint8Array(64).fill(1) }]);

        await expect(adapter.sendTransaction(transaction, {} as never)).resolves.toEqual(expect.any(String));
        expect(wallet.signAndSendImpl).toHaveBeenCalledWith(
            expect.objectContaining({ account, transaction: unsignedBytes }),
        );
    });
});
