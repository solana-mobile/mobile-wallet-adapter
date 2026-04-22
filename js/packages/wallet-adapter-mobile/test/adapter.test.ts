// @vitest-environment jsdom

import {
    WalletConnectionError,
    WalletNotConnectedError,
    WalletNotReadyError,
    WalletReadyState,
    WalletSignMessageError,
} from '@solana/wallet-adapter-base';
import { SolanaSignAndSendTransaction } from '@solana/wallet-standard-features';
import { PublicKey, Transaction, VersionedMessage } from '@solana/web3.js';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const {
    createDefaultChainSelectorMock,
    getIsSupportedMock,
    localWalletConfigs,
    localWalletInstances,
    remoteWalletConfigs,
    remoteWalletInstances,
} = vi.hoisted(() => ({
    createDefaultChainSelectorMock: vi.fn(),
    getIsSupportedMock: vi.fn(),
    localWalletConfigs: [] as unknown[],
    localWalletInstances: [] as unknown[],
    remoteWalletConfigs: [] as unknown[],
    remoteWalletInstances: [] as unknown[],
}));

vi.mock('../src/getIsSupported.js', () => ({
    default: getIsSupportedMock,
}));

vi.mock('@solana-mobile/wallet-standard-mobile', async () => {
    const { SolanaSignAndSendTransaction, SolanaSignIn, SolanaSignMessage, SolanaSignTransaction } =
        await import('@solana/wallet-standard-features');
    const { StandardConnect, StandardDisconnect, StandardEvents } = await import('@wallet-standard/core');

    class WalletMock {
        accounts: unknown[] = [];
        cachedAuthorization: unknown;
        config: unknown;
        connectImpl = vi.fn(async () => ({ accounts: this.accounts }));
        connected = false;
        currentAuthorization: { chain: string } | undefined;
        disconnectImpl = vi.fn(async () => {
            this.connected = false;
            this.currentAuthorization = undefined;
            this.isAuthorized = false;
        });
        features: Record<PropertyKey, unknown>;
        icon = 'data:image/svg+xml;base64,icon';
        isAuthorized = false;
        listeners: Array<(properties: { accounts?: unknown[] }) => unknown> = [];
        name = 'Mock Mobile Wallet';
        signAndSendImpl = vi.fn(async () => [{ signature: Uint8Array.of(1, 2, 3) }]);
        signInImpl = vi.fn(async () => []);
        signMessageImpl = vi.fn(async () => [{ signature: Uint8Array.of(9, 9, 9) }]);
        signTransactionImpl = vi.fn(async () => []);
        url = 'https://example.test/wallet';

        constructor(config: unknown) {
            this.config = config;
            this.features = {
                [SolanaSignAndSendTransaction]: {
                    signAndSendTransaction: async (input: unknown) => await this.signAndSendImpl(input),
                    supportedTransactionVersions: ['legacy', 0],
                    version: '1.0.0',
                },
                [SolanaSignIn]: {
                    signIn: async (input: unknown) => await this.signInImpl(input),
                    version: '1.0.0',
                },
                [SolanaSignMessage]: {
                    signMessage: async (input: unknown) => await this.signMessageImpl(input),
                    version: '1.0.0',
                },
                [SolanaSignTransaction]: {
                    signTransaction: async (...inputs: unknown[]) => await this.signTransactionImpl(...inputs),
                    supportedTransactionVersions: ['legacy', 0],
                    version: '1.0.0',
                },
                [StandardConnect]: {
                    connect: async (input: unknown) => await this.connectImpl(input),
                    version: '1.0.0',
                },
                [StandardDisconnect]: {
                    disconnect: async () => await this.disconnectImpl(),
                    version: '1.0.0',
                },
                [StandardEvents]: {
                    on: (_event: unknown, listener: (properties: { accounts?: unknown[] }) => unknown) => {
                        this.listeners.push(listener);
                        return (): void => {
                            this.listeners = this.listeners.filter((existingListener) => existingListener !== listener);
                        };
                    },
                    version: '1.0.0',
                },
            };
        }

        get cachedAuthorizationResult() {
            return Promise.resolve(this.cachedAuthorization);
        }

        emitAccounts(accounts: unknown[], chain: string = 'mainnet-beta') {
            this.accounts = accounts;
            this.connected = accounts.length > 0;
            this.currentAuthorization = this.connected ? { chain } : undefined;
            this.isAuthorized = this.connected;
            this.listeners.forEach((listener) => {
                listener({ accounts });
            });
        }
    }

    class LocalWalletMock extends WalletMock {
        constructor(config: unknown) {
            super(config);
            localWalletConfigs.push(config);
            localWalletInstances.push(this);
        }
    }

    class RemoteWalletMock extends WalletMock {
        constructor(config: unknown) {
            super(config);
            remoteWalletConfigs.push(config);
            remoteWalletInstances.push(this);
        }
    }

    return {
        LocalSolanaMobileWalletAdapterWallet: LocalWalletMock,
        RemoteSolanaMobileWalletAdapterWallet: RemoteWalletMock,
        SolanaMobileWalletAdapterRemoteWalletName: 'Remote Mobile Wallet Adapter',
        SolanaMobileWalletAdapterWalletName: 'Mobile Wallet Adapter',
        createDefaultChainSelector: createDefaultChainSelectorMock,
    };
});

import { LocalSolanaMobileWalletAdapter, RemoteSolanaMobileWalletAdapter } from '../src/adapter.js';

type MockWalletInstance = {
    accounts: unknown[];
    cachedAuthorization: unknown;
    config: unknown;
    connectImpl: ReturnType<typeof vi.fn>;
    connected: boolean;
    currentAuthorization: { chain: string } | undefined;
    disconnectImpl: ReturnType<typeof vi.fn>;
    emitAccounts: (accounts: unknown[], chain?: string) => void;
    features: Record<PropertyKey, unknown>;
    isAuthorized: boolean;
    signAndSendImpl: ReturnType<typeof vi.fn>;
    signInImpl: ReturnType<typeof vi.fn>;
    signMessageImpl: ReturnType<typeof vi.fn>;
    signTransactionImpl: ReturnType<typeof vi.fn>;
};

beforeEach(() => {
    createDefaultChainSelectorMock.mockReset();
    createDefaultChainSelectorMock.mockImplementation(() => ({
        select: vi.fn(),
    }));
    getIsSupportedMock.mockReset();
    getIsSupportedMock.mockReturnValue(true);
});

afterEach(() => {
    localWalletConfigs.length = 0;
    localWalletInstances.length = 0;
    remoteWalletConfigs.length = 0;
    remoteWalletInstances.length = 0;
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
});

describe('adapter', () => {
    it('configures the local wallet with a mapped cluster chain', () => {
        new LocalSolanaMobileWalletAdapter({
            addressSelector: createAddressSelector(),
            appIdentity: createAppIdentity(),
            authorizationResultCache: createAuthorizationResultCache(),
            cluster: 'devnet',
            onWalletNotFound: vi.fn().mockResolvedValue(undefined),
        });

        expect(createDefaultChainSelectorMock).toHaveBeenCalledTimes(1);
        expect(getLastItem(localWalletConfigs)).toEqual(
            expect.objectContaining({
                chains: ['solana:devnet'],
            }),
        );
    });

    it('configures the remote wallet with the provided host authority', () => {
        new RemoteSolanaMobileWalletAdapter({
            addressSelector: createAddressSelector(),
            appIdentity: createAppIdentity(),
            authorizationResultCache: createAuthorizationResultCache(),
            chain: 'solana:mainnet',
            onWalletNotFound: vi.fn().mockResolvedValue(undefined),
            remoteHostAuthority: 'remote.example.com',
        });

        expect(createDefaultChainSelectorMock).toHaveBeenCalledTimes(1);
        expect(getLastItem(remoteWalletConfigs)).toEqual(
            expect.objectContaining({
                chains: ['solana:mainnet'],
                remoteHostAuthority: 'remote.example.com',
            }),
        );
    });

    it('selects the requested account and emits readyStateChange on connect', async () => {
        const firstAccount = createWalletAccount(1);
        const secondAccount = createWalletAccount(2);
        const addressSelector = createAddressSelector(encodeAddress(secondAccount.publicKey));
        const { adapter, wallet } = createLocalAdapter({ addressSelector });
        const connectListener = vi.fn();
        const readyStateChangeListener = vi.fn();

        adapter.on('connect', connectListener);
        adapter.on('readyStateChange', readyStateChangeListener);
        wallet.connectImpl.mockImplementation(async (input: unknown) => {
            expect(input).toEqual({ silent: false });
            wallet.emitAccounts([firstAccount, secondAccount]);
            return { accounts: wallet.accounts };
        });

        await adapter.connect();
        await flushPromises();

        expect(addressSelector.select).toHaveBeenCalledWith([
            encodeAddress(firstAccount.publicKey),
            encodeAddress(secondAccount.publicKey),
        ]);
        expect(adapter.connected).toBe(true);
        expect(adapter.publicKey?.toBase58()).toBe(new PublicKey(secondAccount.publicKey).toBase58());
        expect(adapter.readyState).toBe(WalletReadyState.Installed);
        expect(connectListener).toHaveBeenCalledTimes(1);
        expect(connectListener.mock.calls[0][0].toBase58()).toBe(new PublicKey(secondAccount.publicKey).toBase58());
        expect(readyStateChangeListener).toHaveBeenCalledWith(WalletReadyState.Installed);
    });

    it('passes a silent connect request during autoConnect', async () => {
        const { adapter, wallet } = createLocalAdapter();

        await adapter.autoConnect();

        expect(wallet.connectImpl).toHaveBeenCalledWith({ silent: true });
    });

    it('passes the adapter instance to the local onWalletNotFound handler', async () => {
        const onWalletNotFound = vi.fn().mockResolvedValue(undefined);
        const { adapter } = createLocalAdapter({ onWalletNotFound });

        await (getLastItem(localWalletConfigs) as { onWalletNotFound: () => Promise<void> }).onWalletNotFound();

        expect(onWalletNotFound).toHaveBeenCalledTimes(1);
        expect(onWalletNotFound).toHaveBeenCalledWith(adapter);
    });

    it('passes the adapter instance to the remote onWalletNotFound handler', async () => {
        const onWalletNotFound = vi.fn().mockResolvedValue(undefined);
        const { adapter } = createRemoteAdapter({ onWalletNotFound });

        await (getLastItem(remoteWalletConfigs) as { onWalletNotFound: () => Promise<void> }).onWalletNotFound();

        expect(onWalletNotFound).toHaveBeenCalledTimes(1);
        expect(onWalletNotFound).toHaveBeenCalledWith(adapter);
    });

    it('performs authorization through connect when no sign-in payload is provided', async () => {
        const authorizationResult = { auth_token: 'token-from-connect' };
        const { adapter, wallet } = createLocalAdapter();

        wallet.connectImpl.mockImplementation(async () => {
            wallet.cachedAuthorization = authorizationResult;
            return { accounts: wallet.accounts };
        });

        await expect(adapter.performAuthorization()).resolves.toBe(authorizationResult);
        expect(wallet.connectImpl).toHaveBeenCalledWith(undefined);
    });

    it('performs authorization through signIn when a sign-in payload is provided', async () => {
        const authorizationResult = { auth_token: 'token-from-sign-in' };
        const signInPayload = { domain: 'example.test' };
        const { adapter, wallet } = createLocalAdapter();

        wallet.signInImpl.mockImplementation(async (payload: unknown) => {
            expect(payload).toEqual(signInPayload);
            wallet.cachedAuthorization = authorizationResult;
            return [{ signedMessage: 'ignored' }];
        });

        await expect(adapter.performAuthorization(signInPayload as never)).resolves.toBe(authorizationResult);
        expect(wallet.signInImpl).toHaveBeenCalledTimes(1);
        expect(wallet.connectImpl).not.toHaveBeenCalled();
    });

    it('reuses the cached authorization result in performAuthorization', async () => {
        const cachedAuthorization = { auth_token: 'token' };
        const { adapter, wallet } = createLocalAdapter();

        wallet.cachedAuthorization = cachedAuthorization;

        await expect(adapter.performAuthorization()).resolves.toBe(cachedAuthorization);
        expect(wallet.connectImpl).toHaveBeenCalledWith({ silent: true });
    });

    it('clears the selected account and emits disconnect', async () => {
        const account = createWalletAccount(3);
        const { adapter, wallet } = createLocalAdapter();
        const disconnectListener = vi.fn();

        adapter.on('disconnect', disconnectListener);
        wallet.emitAccounts([account]);
        await flushPromises();

        expect(adapter.publicKey).not.toBeNull();

        await adapter.disconnect();

        expect(adapter.connected).toBe(false);
        expect(adapter.publicKey).toBeNull();
        expect(disconnectListener).toHaveBeenCalledTimes(1);
        expect(wallet.disconnectImpl).toHaveBeenCalledTimes(1);
    });

    it('defaults signIn to the current location host and returns the first result', async () => {
        const result = { account: createWalletAccount(4) };
        const { adapter, wallet } = createLocalAdapter();

        wallet.signInImpl.mockResolvedValue([result, { ignored: true }]);

        await expect(adapter.signIn({ statement: 'Sign in' })).resolves.toBe(result);
        expect(wallet.signInImpl).toHaveBeenCalledWith({
            domain: window.location.host,
            statement: 'Sign in',
        });
    });

    it('throws WalletConnectionError when signIn returns no results', async () => {
        const { adapter } = createLocalAdapter();
        const errorListener = vi.fn();

        adapter.on('error', errorListener);

        await expect(adapter.signIn()).rejects.toBeInstanceOf(WalletConnectionError);
        expect(errorListener).toHaveBeenCalledTimes(1);
        expect(errorListener.mock.calls[0][0]).toBeInstanceOf(WalletConnectionError);
    });

    it('throws WalletNotReadyError when signIn is attempted while unsupported', async () => {
        const errorListener = vi.fn();

        getIsSupportedMock.mockReturnValue(false);
        const { adapter } = createLocalAdapter();
        adapter.on('error', errorListener);

        await expect(adapter.signIn()).rejects.toBeInstanceOf(WalletNotReadyError);
        expect(errorListener).toHaveBeenCalledTimes(1);
        expect(errorListener.mock.calls[0][0]).toBeInstanceOf(WalletNotReadyError);
    });

    it('throws WalletNotConnectedError when signing without an authorized account', async () => {
        const { adapter } = createLocalAdapter();
        const errorListener = vi.fn();

        adapter.on('error', errorListener);

        await expect(adapter.signMessage(Uint8Array.of(1, 2, 3))).rejects.toBeInstanceOf(WalletNotConnectedError);
        expect(errorListener).toHaveBeenCalledTimes(1);
        expect(errorListener.mock.calls[0][0]).toBeInstanceOf(WalletNotConnectedError);
    });

    it('wraps signMessage failures in WalletSignMessageError', async () => {
        const account = createWalletAccount(5);
        const { adapter, wallet } = createLocalAdapter();
        const errorListener = vi.fn();

        adapter.on('error', errorListener);
        wallet.emitAccounts([account]);
        wallet.signMessageImpl.mockRejectedValue(new Error('Signature rejected'));
        await flushPromises();

        await expect(adapter.signMessage(Uint8Array.of(4, 5, 6))).rejects.toBeInstanceOf(WalletSignMessageError);
        expect(errorListener).toHaveBeenCalledTimes(1);
        expect(errorListener.mock.calls[0][0]).toBeInstanceOf(WalletSignMessageError);
    });

    it('uses signAndSendTransaction when the wallet supports it', async () => {
        const account = createWalletAccount(6);
        const { adapter, wallet } = createLocalAdapter();
        const transaction = {
            serialize: vi.fn(() => Uint8Array.of(7, 8, 9)),
        };

        wallet.emitAccounts([account], 'mainnet-beta');
        wallet.signAndSendImpl.mockResolvedValue([{ signature: Uint8Array.of(8, 8, 8) }]);
        await flushPromises();

        await expect(
            adapter.sendTransaction(transaction as never, {} as never, { maxRetries: 2, skipPreflight: true }),
        ).resolves.toBe(encodeAddress(Uint8Array.of(8, 8, 8)));
        expect(wallet.signAndSendImpl).toHaveBeenCalledWith({
            account,
            chain: 'solana:mainnet',
            options: {
                maxRetries: 2,
                skipPreflight: true,
            },
            transaction: Uint8Array.of(7, 8, 9),
        });
    });

    it('falls back to signTransaction and sendRawTransaction when signAndSend is unavailable', async () => {
        const account = createWalletAccount(7);
        const connection = {
            commitment: 'confirmed',
            sendRawTransaction: vi.fn().mockResolvedValue('signature'),
        };
        const signedTransaction = {
            serialize: vi.fn(() => Uint8Array.of(4, 5, 6)),
        };
        const transaction = {
            serialize: vi.fn(() => Uint8Array.of(1, 2, 3)),
        };
        const { adapter, wallet } = createLocalAdapter();

        wallet.emitAccounts([account]);
        delete wallet.features[SolanaSignAndSendTransaction];
        wallet.signTransactionImpl.mockResolvedValue([{ signedTransaction: Uint8Array.of(1) }]);
        vi.spyOn(Transaction, 'from').mockReturnValue(signedTransaction as never);
        vi.spyOn(VersionedMessage, 'deserializeMessageVersion').mockReturnValue('legacy');
        await flushPromises();

        await expect(
            adapter.sendTransaction(transaction as never, connection as never, { preflightCommitment: 'processed' }),
        ).resolves.toBe('signature');
        expect(connection.sendRawTransaction).toHaveBeenCalledWith(Uint8Array.of(4, 5, 6), {
            preflightCommitment: 'processed',
        });
        expect(wallet.signTransactionImpl).toHaveBeenCalledWith({
            account,
            transaction: Uint8Array.of(1, 2, 3),
        });
    });
});

function createAddressSelector(selectedAddress?: string) {
    return {
        select: vi.fn(async (addresses: string[]) => selectedAddress ?? addresses[0]),
    };
}

function createAppIdentity() {
    return {
        icon: 'favicon.ico',
        name: 'Example App',
        uri: 'https://example.test',
    };
}

function createAuthorizationResultCache() {
    return {
        clear: vi.fn().mockResolvedValue(undefined),
        get: vi.fn().mockResolvedValue(undefined),
        set: vi.fn().mockResolvedValue(undefined),
    };
}

function createLocalAdapter({
    addressSelector = createAddressSelector(),
    onWalletNotFound = vi.fn().mockResolvedValue(undefined),
}: {
    addressSelector?: ReturnType<typeof createAddressSelector>;
    onWalletNotFound?: ReturnType<typeof vi.fn>;
} = {}) {
    const adapter = new LocalSolanaMobileWalletAdapter({
        addressSelector,
        appIdentity: createAppIdentity(),
        authorizationResultCache: createAuthorizationResultCache(),
        chain: 'solana:mainnet',
        onWalletNotFound,
    });

    return {
        adapter,
        wallet: getLastItem(localWalletInstances) as MockWalletInstance,
    };
}

function createRemoteAdapter({
    addressSelector = createAddressSelector(),
    onWalletNotFound = vi.fn().mockResolvedValue(undefined),
}: {
    addressSelector?: ReturnType<typeof createAddressSelector>;
    onWalletNotFound?: ReturnType<typeof vi.fn>;
} = {}) {
    const adapter = new RemoteSolanaMobileWalletAdapter({
        addressSelector,
        appIdentity: createAppIdentity(),
        authorizationResultCache: createAuthorizationResultCache(),
        chain: 'solana:mainnet',
        onWalletNotFound,
        remoteHostAuthority: 'remote.example.com',
    });

    return {
        adapter,
        wallet: getLastItem(remoteWalletInstances) as MockWalletInstance,
    };
}

function createWalletAccount(seed: number) {
    return {
        address: `address-${seed}`,
        chains: ['solana:mainnet'],
        features: [],
        icon: 'data:image/svg+xml;base64,icon',
        label: `Account ${seed}`,
        publicKey: createPublicKey(seed),
    };
}

function createPublicKey(seed: number) {
    return Uint8Array.from(Array.from({ length: 32 }, (_, index) => (seed + index) % 256));
}

function encodeAddress(byteArray: Uint8Array) {
    return window.btoa(String.fromCharCode(...byteArray));
}

async function flushPromises() {
    await Promise.resolve();
    await Promise.resolve();
}

function getLastItem<T>(items: T[]) {
    const item = items.at(-1);

    if (!item) {
        throw new Error('Expected an item to be present');
    }

    return item;
}
