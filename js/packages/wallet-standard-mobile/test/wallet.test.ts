// @vitest-environment jsdom
import { SOLANA_MAINNET_CHAIN } from '@solana/wallet-standard-chains';
import {
    SolanaSignAndSendTransaction,
    type SolanaSignAndSendTransactionFeature,
    SolanaSignIn,
    SolanaSignMessage,
    SolanaSignTransaction,
    type SolanaSignTransactionFeature,
} from '@solana/wallet-standard-features';
import {
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import type { WalletAccount } from '@wallet-standard/base';
import { StandardConnect, StandardDisconnect, StandardEvents } from '@wallet-standard/features';
import base58 from 'bs58';
import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockStartRemoteScenario, mockStartScenario } = vi.hoisted(() => ({
    mockStartRemoteScenario: vi.fn(),
    mockStartScenario: vi.fn(),
}));

const { loadingSpinnerInstances, LoadingSpinnerMock, resetLoadingSpinnerMocks } = vi.hoisted(() => {
    type LoadingSpinnerInstance = {
        addEventListener: ReturnType<typeof vi.fn>;
        close: ReturnType<typeof vi.fn>;
        init: ReturnType<typeof vi.fn>;
        open: ReturnType<typeof vi.fn>;
    };

    const loadingSpinnerInstances: LoadingSpinnerInstance[] = [];

    class LoadingSpinnerMock {
        addEventListener = vi.fn();
        close = vi.fn();
        init = vi.fn();
        open = vi.fn();

        constructor() {
            loadingSpinnerInstances.push(this);
        }
    }

    const resetLoadingSpinnerMocks = () => {
        loadingSpinnerInstances.length = 0;
    };

    return {
        loadingSpinnerInstances,
        LoadingSpinnerMock,
        resetLoadingSpinnerMocks,
    };
});

const { mockCheckLocalNetworkAccessPermission } = vi.hoisted(() => ({
    mockCheckLocalNetworkAccessPermission: vi.fn(),
}));

const { remoteModalInstances, RemoteModalMock, resetRemoteModalMocks } = vi.hoisted(() => {
    type RemoteModalInstance = {
        addEventListener: ReturnType<typeof vi.fn>;
        close: ReturnType<typeof vi.fn>;
        init: ReturnType<typeof vi.fn>;
        open: ReturnType<typeof vi.fn>;
        populateQRCode: ReturnType<typeof vi.fn>;
    };

    const remoteModalInstances: RemoteModalInstance[] = [];

    class RemoteModalMock {
        addEventListener = vi.fn(() => vi.fn());
        close = vi.fn();
        init = vi.fn();
        open = vi.fn();
        populateQRCode = vi.fn();

        constructor() {
            remoteModalInstances.push(this);
        }
    }

    const resetRemoteModalMocks = () => {
        remoteModalInstances.length = 0;
    };

    return {
        remoteModalInstances,
        RemoteModalMock,
        resetRemoteModalMocks,
    };
});

vi.mock('@solana-mobile/mobile-wallet-adapter-protocol', async () => {
    const actual = await vi.importActual<typeof import('@solana-mobile/mobile-wallet-adapter-protocol')>(
        '@solana-mobile/mobile-wallet-adapter-protocol',
    );

    return {
        ...actual,
        startRemoteScenario: mockStartRemoteScenario,
        startScenario: mockStartScenario,
    };
});

vi.mock('../src/embedded-modal/loadingSpinner.js', () => ({
    default: LoadingSpinnerMock,
}));

vi.mock('../src/embedded-modal/remoteConnectionModal.js', () => ({
    default: RemoteModalMock,
}));

vi.mock('../src/getIsSupported.js', async () => {
    const actual = await vi.importActual<typeof import('../src/getIsSupported.js')>('../src/getIsSupported.js');

    return {
        ...actual,
        checkLocalNetworkAccessPermission: mockCheckLocalNetworkAccessPermission,
    };
});

import {
    type Authorization,
    LocalSolanaMobileWalletAdapterWallet,
    RemoteSolanaMobileWalletAdapterWallet,
} from '../src/wallet.js';

const DEFAULT_CAPABILITIES: Authorization['capabilities'] = {
    features: ['solana:signTransactions'],
    max_messages_per_request: 2,
    max_transactions_per_request: 2,
    supported_transaction_versions: ['legacy', 0],
    supports_clone_authorization: false,
    supports_sign_and_send_transactions: true,
};

afterEach(() => {
    mockCheckLocalNetworkAccessPermission.mockReset();
    mockStartRemoteScenario.mockReset();
    mockStartScenario.mockReset();
    resetLoadingSpinnerMocks();
    resetRemoteModalMocks();
    vi.restoreAllMocks();
});

function getSignAndSendFeature(wallet: { features: object }) {
    return (wallet.features as SolanaSignAndSendTransactionFeature)[SolanaSignAndSendTransaction];
}

function getSignTransactionFeature(wallet: { features: object }) {
    return (wallet.features as SolanaSignTransactionFeature)[SolanaSignTransaction];
}

function getOptionalSignTransactionFeature(wallet: { features: object }) {
    return (wallet.features as Partial<SolanaSignTransactionFeature>)[SolanaSignTransaction];
}

describe('LocalSolanaMobileWalletAdapterWallet', () => {
    it('exposes wallet metadata and default wallet-standard features', async () => {
        const { authorizationCache, wallet } = createLocalWallet();

        expect(wallet.version).toBe('1.0.0');
        expect(wallet.name).toBe('Mobile Wallet Adapter');
        expect(wallet.url).toBe('https://solanamobile.com/wallets');
        expect(wallet.icon).toMatch(/^data:image\/svg\+xml;base64,/);
        expect(wallet.chains).toEqual([SOLANA_MAINNET_CHAIN]);
        expect(wallet.accounts).toEqual([]);
        expect(wallet.connected).toBe(false);
        expect(wallet.currentAuthorization).toBeUndefined();
        expect(wallet.isAuthorized).toBe(false);
        await expect(wallet.cachedAuthorizationResult).resolves.toBeUndefined();

        expect(authorizationCache.get).toHaveBeenCalledTimes(1);
        expect(getSignAndSendFeature(wallet)).toBeDefined();
        expect(wallet.features[SolanaSignIn]).toBeDefined();
        expect(wallet.features[SolanaSignMessage]).toBeDefined();
        expect(getSignTransactionFeature(wallet)).toBeDefined();
        expect(wallet.features[StandardConnect]).toBeDefined();
        expect(wallet.features[StandardDisconnect]).toBeDefined();
        expect(wallet.features[StandardEvents]).toBeDefined();
    });

    it('connects through local association, converts accounts, caches authorization, and disconnects', async () => {
        const accountPublicKey = Uint8Array.of(1, 2, 3);
        const authorization = createMwaAuthorization(accountPublicKey);
        const mobileWallet = createMobileWallet({ authorization });
        const scenarioClose = vi.fn();
        const { authorizationCache, chainSelector, wallet } = createLocalWallet();
        const changeListener = vi.fn();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: scenarioClose,
            wallet: Promise.resolve(mobileWallet),
        });

        const removeListener = wallet.features[StandardEvents].on('change', changeListener);
        const connectResult = await wallet.features[StandardConnect].connect();

        expect(mockCheckLocalNetworkAccessPermission).toHaveBeenCalledTimes(1);
        expect(mockStartScenario).toHaveBeenCalledWith(undefined);
        expect(chainSelector.select).toHaveBeenCalledWith([SOLANA_MAINNET_CHAIN]);
        expect(mobileWallet.authorize).toHaveBeenCalledWith({
            chain: SOLANA_MAINNET_CHAIN,
            identity: {
                icon: 'favicon.ico',
                name: 'Example App',
                uri: 'https://example.test',
            },
            sign_in_payload: undefined,
        });
        expect(authorizationCache.set).toHaveBeenCalledWith(
            expect.objectContaining({
                accounts: [
                    expect.objectContaining({
                        address: base58.encode(accountPublicKey),
                        publicKey: accountPublicKey,
                    }),
                ],
                auth_token: 'auth-token',
                chain: SOLANA_MAINNET_CHAIN,
                capabilities: DEFAULT_CAPABILITIES,
            }),
        );
        expect(connectResult.accounts).toEqual(wallet.accounts);
        expect(wallet.connected).toBe(true);
        expect(wallet.accounts[0].address).toBe(base58.encode(accountPublicKey));
        expect(wallet.accounts[0].publicKey).toEqual(accountPublicKey);
        expect(changeListener).toHaveBeenCalledWith({ accounts: wallet.accounts });
        expect(loadingSpinnerInstances).toHaveLength(1);
        expect(loadingSpinnerInstances[0].init).toHaveBeenCalledTimes(1);
        expect(loadingSpinnerInstances[0].open).toHaveBeenCalledTimes(1);
        expect(loadingSpinnerInstances[0].close).toHaveBeenCalledTimes(1);
        expect(scenarioClose).toHaveBeenCalledTimes(1);

        const spinnerCloseListener = loadingSpinnerInstances[0].addEventListener.mock.calls[0][1];
        spinnerCloseListener(new Event('close'));
        expect(scenarioClose).toHaveBeenCalledTimes(2);

        await expect(wallet.features[StandardConnect].connect()).resolves.toEqual({ accounts: wallet.accounts });
        expect(mockStartScenario).toHaveBeenCalledTimes(1);

        removeListener();
        await wallet.features[StandardDisconnect].disconnect();

        expect(authorizationCache.clear).toHaveBeenCalledTimes(1);
        expect(wallet.connected).toBe(false);
        expect(wallet.accounts).toEqual([]);
        expect(changeListener).toHaveBeenCalledTimes(1);
    });

    it('uses cached authorization for silent connects and updates capability features', async () => {
        const cachedAuthorization = createCachedAuthorization(Uint8Array.of(4, 5, 6), {
            ...DEFAULT_CAPABILITIES,
            features: [],
            supports_sign_and_send_transactions: false,
        });
        const { authorizationCache, wallet } = createLocalWallet({
            cachedAuthorization,
        });
        const changeListener = vi.fn();

        wallet.features[StandardEvents].on('change', changeListener);

        const connectResult = await wallet.features[StandardConnect].connect({ silent: true });

        expect(connectResult.accounts).toEqual([cachedAuthorization.accounts[0]]);
        expect(wallet.connected).toBe(true);
        expect(getSignAndSendFeature(wallet)).toBeDefined();
        expect(getOptionalSignTransactionFeature(wallet)).toBeUndefined();
        expect(mockStartScenario).not.toHaveBeenCalled();
        expect(authorizationCache.get).toHaveBeenCalledTimes(1);
        expect(changeListener).toHaveBeenCalledWith({ features: wallet.features });
        expect(changeListener).toHaveBeenCalledWith({ accounts: wallet.accounts });
    });

    it('returns existing accounts without association for silent connects when nothing is cached', async () => {
        const { authorizationCache, wallet } = createLocalWallet();

        await expect(wallet.features[StandardConnect].connect({ silent: true })).resolves.toEqual({ accounts: [] });

        expect(authorizationCache.get).toHaveBeenCalledTimes(1);
        expect(mockStartScenario).not.toHaveBeenCalled();
        expect(wallet.connected).toBe(false);
    });

    it('signs transactions, sends transactions, and signs messages after authorization', async () => {
        const accountPublicKey = Uint8Array.of(7, 8, 9);
        const authorization = createMwaAuthorization(accountPublicKey);
        const mobileWallet = createMobileWallet({ authorization });
        const { wallet } = createLocalWallet();
        const signedMessage = Uint8Array.from([...Uint8Array.of(1, 2), ...new Uint8Array(64).fill(9)]);

        mobileWallet.signAndSendTransactions.mockResolvedValue({
            signatures: [encodeBytes(Uint8Array.of(10, 11, 12))],
        });
        mobileWallet.signMessages.mockResolvedValue({
            signed_payloads: [encodeBytes(signedMessage)],
        });
        mobileWallet.signTransactions.mockResolvedValue({
            signed_payloads: [encodeBytes(Uint8Array.of(13, 14, 15))],
        });
        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await wallet.features[StandardConnect].connect();

        await expect(
            getSignTransactionFeature(wallet).signTransaction({
                account: wallet.accounts[0],
                transaction: Uint8Array.of(1, 2, 3),
            }),
        ).resolves.toEqual([{ signedTransaction: Uint8Array.of(13, 14, 15) }]);
        expect(mobileWallet.signTransactions).toHaveBeenCalledWith({
            payloads: [encodeBytes(Uint8Array.of(1, 2, 3))],
        });

        await expect(
            getSignAndSendFeature(wallet).signAndSendTransaction({
                account: wallet.accounts[0],
                chain: SOLANA_MAINNET_CHAIN,
                options: { minContextSlot: 123 },
                transaction: Uint8Array.of(4, 5, 6),
            }),
        ).resolves.toEqual([{ signature: Uint8Array.of(10, 11, 12) }]);
        expect(mobileWallet.signAndSendTransactions).toHaveBeenCalledWith({
            minContextSlot: 123,
            payloads: [encodeBytes(Uint8Array.of(4, 5, 6))],
        });

        await expect(
            wallet.features[SolanaSignMessage].signMessage({
                account: wallet.accounts[0],
                message: Uint8Array.of(16, 17, 18),
            }),
        ).resolves.toEqual([{ signature: new Uint8Array(64).fill(9), signedMessage }]);
        expect(mobileWallet.signMessages).toHaveBeenCalledWith({
            addresses: [encodeBytes(accountPublicKey)],
            payloads: [encodeBytes(Uint8Array.of(16, 17, 18))],
        });
        expect(mobileWallet.authorize).toHaveBeenCalledWith({
            auth_token: 'auth-token',
            chain: SOLANA_MAINNET_CHAIN,
            identity: {
                icon: 'favicon.ico',
                name: 'Example App',
                uri: 'https://example.test',
            },
        });
    });

    it('signs in with a default domain and returns the signed-in account details', async () => {
        const accountPublicKey = Uint8Array.of(21, 22, 23);
        const signedMessage = Uint8Array.of(1, 2, 3);
        const signature = Uint8Array.of(4, 5, 6);
        const authorization = createMwaAuthorization(accountPublicKey, {
            sign_in_result: {
                address: encodeBytes(accountPublicKey),
                signature: encodeBytes(signature),
                signed_message: encodeBytes(signedMessage),
            },
        });
        const mobileWallet = createMobileWallet({ authorization });
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(wallet.features[SolanaSignIn].signIn({ statement: 'Sign in' })).resolves.toEqual([
            {
                account: expect.objectContaining({
                    address: base58.encode(accountPublicKey),
                    publicKey: accountPublicKey,
                }),
                signature,
                signedMessage,
            },
        ]);
        expect(mobileWallet.authorize).toHaveBeenCalledWith({
            chain: SOLANA_MAINNET_CHAIN,
            identity: {
                icon: 'favicon.ico',
                name: 'Example App',
                uri: 'https://example.test',
            },
            sign_in_payload: {
                domain: window.location.host,
                statement: 'Sign in',
            },
        });
    });

    it('rejects signing methods when the wallet is not connected', async () => {
        const { wallet } = createLocalWallet();

        await expect(
            getSignTransactionFeature(wallet).signTransaction({
                account: createWalletAccount(Uint8Array.of(1, 2, 3)),
                transaction: Uint8Array.of(1, 2, 3),
            }),
        ).rejects.toThrow('Wallet not connected');
        expect(mockStartScenario).not.toHaveBeenCalled();
    });

    it('rejects connect when local association times out', async () => {
        vi.useFakeTimers();
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockReturnValue(new Promise(() => undefined));

        const connectPromise = wallet.features[StandardConnect].connect();
        const expectation = expect(connectPromise).rejects.toThrow('Wallet connection timed out');
        await vi.advanceTimersByTimeAsync(30000);

        await expectation;
        expect(loadingSpinnerInstances[0].close).toHaveBeenCalledTimes(1);
        vi.useRealTimers();
    });

    it('runs the wallet-not-found handler for local association failures', async () => {
        const onWalletNotFound = vi.fn<WalletNotFoundHandler>().mockResolvedValue(undefined);
        const { wallet } = createLocalWallet({ onWalletNotFound });

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockRejectedValue(
            new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
                'Found no installed wallet that supports the mobile wallet protocol.',
            ),
        );

        await expect(wallet.features[StandardConnect].connect()).rejects.toThrow(
            'Found no installed wallet that supports the mobile wallet protocol.',
        );
        expect(onWalletNotFound).toHaveBeenCalledWith(wallet);
    });

    it('wraps non-error local authorization failures with an unknown-error message', async () => {
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockRejectedValue('association failed');

        await expect(wallet.features[StandardConnect].connect()).rejects.toThrow('Unknown error');
    });

    it('rejects local sign-and-send when the connected wallet reports no support', async () => {
        const accountPublicKey = Uint8Array.of(61, 62, 63);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey),
        });
        const { wallet } = createLocalWallet();

        mobileWallet.getCapabilities.mockResolvedValueOnce(DEFAULT_CAPABILITIES).mockResolvedValueOnce({
            ...DEFAULT_CAPABILITIES,
            supports_sign_and_send_transactions: false,
        });
        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await wallet.features[StandardConnect].connect();

        await expect(
            getSignAndSendFeature(wallet).signAndSendTransaction({
                account: wallet.accounts[0],
                chain: SOLANA_MAINNET_CHAIN,
                transaction: Uint8Array.of(1, 2, 3),
            }),
        ).rejects.toThrow('connected wallet does not support signAndSendTransaction');
    });

    it('signs in with multiple inputs', async () => {
        const accountPublicKey = Uint8Array.of(71, 72, 73);
        const signedMessage = Uint8Array.of(74, 75, 76);
        const signature = Uint8Array.of(77, 78, 79);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey, {
                sign_in_result: {
                    address: encodeBytes(accountPublicKey),
                    signature: encodeBytes(signature),
                    signed_message: encodeBytes(signedMessage),
                },
            }),
        });
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(
            wallet.features[SolanaSignIn].signIn({ domain: 'one.example' }, { domain: 'two.example' }),
        ).resolves.toHaveLength(2);
        expect(mobileWallet.authorize).toHaveBeenCalledTimes(2);
    });

    it('rejects sign-in when the wallet omits the sign-in result', async () => {
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(Uint8Array.of(81, 82, 83)),
        });
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(wallet.features[SolanaSignIn].signIn()).rejects.toThrow(
            'Sign in failed, no sign in result returned by wallet',
        );
    });

    it('builds fallback account details when sign-in returns an address outside the authorized accounts', async () => {
        const accountPublicKey = Uint8Array.of(84, 85, 86);
        const signedInPublicKey = Uint8Array.of(87, 88, 89);
        const signedMessage = Uint8Array.of(90, 91, 92);
        const signature = Uint8Array.of(93, 94, 95);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey, {
                sign_in_result: {
                    address: encodeBytes(signedInPublicKey),
                    signature: encodeBytes(signature),
                    signed_message: encodeBytes(signedMessage),
                },
            }),
        });
        const { wallet } = createLocalWallet();

        mockCheckLocalNetworkAccessPermission.mockResolvedValue(undefined);
        mockStartScenario.mockResolvedValue({
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(wallet.features[SolanaSignIn].signIn({ domain: 'app.example' })).resolves.toEqual([
            {
                account: {
                    address: base58.encode(signedInPublicKey),
                    chains: [SOLANA_MAINNET_CHAIN],
                    features: DEFAULT_CAPABILITIES.features,
                    publicKey: signedInPublicKey,
                },
                signature,
                signedMessage,
            },
        ]);
    });
});

describe('RemoteSolanaMobileWalletAdapterWallet', () => {
    it('connects through remote association, reuses the session, and closes it on disconnect', async () => {
        const accountPublicKey = Uint8Array.of(31, 32, 33);
        const capabilities: Authorization['capabilities'] = {
            ...DEFAULT_CAPABILITIES,
            features: ['solana:signAndSendTransaction', 'solana:signTransactions'],
            supports_sign_and_send_transactions: false,
        };
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey),
            capabilities,
        });
        const scenarioClose = vi.fn();
        const { authorizationCache, chainSelector, wallet } = createRemoteWallet();
        const changeListener = vi.fn();

        const removeListener = wallet.features[StandardEvents].on('change', changeListener);

        mobileWallet.signTransactions.mockResolvedValue({
            signed_payloads: [encodeBytes(Uint8Array.of(34, 35, 36))],
        });
        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: scenarioClose,
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(wallet.features[StandardConnect].connect()).resolves.toEqual({
            accounts: [
                expect.objectContaining({
                    address: base58.encode(accountPublicKey),
                    publicKey: accountPublicKey,
                }),
            ],
        });

        expect(wallet.version).toBe('1.0.0');
        expect(wallet.name).toBe('Remote Mobile Wallet Adapter');
        expect(wallet.url).toBe('https://solanamobile.com/wallets');
        expect(wallet.icon).toMatch(/^data:image\/svg\+xml;base64,/);
        expect(wallet.chains).toEqual([SOLANA_MAINNET_CHAIN]);
        expect(wallet.currentAuthorization).toEqual(expect.objectContaining({ auth_token: 'auth-token' }));
        expect(wallet.isAuthorized).toBe(true);
        await expect(wallet.cachedAuthorizationResult).resolves.toBeUndefined();
        expect(mockStartRemoteScenario).toHaveBeenCalledWith({
            remoteHostAuthority: 'remote.example.com',
        });
        expect(chainSelector.select).toHaveBeenCalledWith([SOLANA_MAINNET_CHAIN]);
        expect(remoteModalInstances).toHaveLength(1);
        expect(remoteModalInstances[0].init).toHaveBeenCalledTimes(1);
        expect(remoteModalInstances[0].open).toHaveBeenCalledTimes(1);
        expect(remoteModalInstances[0].populateQRCode).toHaveBeenCalledWith('https://example.test/associate');
        expect(remoteModalInstances[0].close).toHaveBeenCalledTimes(1);
        remoteModalInstances[0].addEventListener.mock.calls[0][1](new Event('close'));
        expect(scenarioClose).toHaveBeenCalledTimes(1);
        expect(wallet.connected).toBe(true);
        expect(getSignAndSendFeature(wallet).supportedTransactionVersions).toEqual(['legacy', 0]);
        expect(getSignTransactionFeature(wallet).supportedTransactionVersions).toEqual(['legacy', 0]);

        await expect(
            getSignTransactionFeature(wallet).signTransaction({
                account: wallet.accounts[0],
                transaction: Uint8Array.of(1, 2, 3),
            }),
        ).resolves.toEqual([{ signedTransaction: Uint8Array.of(34, 35, 36) }]);

        expect(mockStartRemoteScenario).toHaveBeenCalledTimes(1);
        expect(mobileWallet.signTransactions).toHaveBeenCalledWith({
            payloads: [encodeBytes(Uint8Array.of(1, 2, 3))],
        });

        await expect(wallet.features[StandardConnect].connect()).resolves.toEqual({ accounts: wallet.accounts });
        expect(mockStartRemoteScenario).toHaveBeenCalledTimes(1);

        removeListener();
        await wallet.features[StandardDisconnect].disconnect();

        expect(scenarioClose).toHaveBeenCalledTimes(2);
        expect(authorizationCache.clear).toHaveBeenCalledTimes(1);
        expect(wallet.connected).toBe(false);
        expect(changeListener).toHaveBeenCalledWith({ accounts: expect.any(Array) });
    });

    it('uses an existing remote session to sign and send transactions and sign messages', async () => {
        const accountPublicKey = Uint8Array.of(41, 42, 43);
        const signedMessage = Uint8Array.from([...Uint8Array.of(1, 2), ...new Uint8Array(64).fill(8)]);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey),
        });
        const { wallet } = createRemoteWallet();

        mobileWallet.signAndSendTransactions.mockResolvedValue({
            signatures: [encodeBytes(Uint8Array.of(44, 45, 46))],
        });
        mobileWallet.signMessages.mockResolvedValue({
            signed_payloads: [encodeBytes(signedMessage)],
        });
        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await wallet.features[StandardConnect].connect();

        await expect(
            getSignAndSendFeature(wallet).signAndSendTransaction({
                account: wallet.accounts[0],
                chain: SOLANA_MAINNET_CHAIN,
                options: { minContextSlot: 123 },
                transaction: Uint8Array.of(4, 5, 6),
            }),
        ).resolves.toEqual([{ signature: Uint8Array.of(44, 45, 46) }]);
        expect(mobileWallet.signAndSendTransactions).toHaveBeenCalledWith({
            minContextSlot: 123,
            payloads: [encodeBytes(Uint8Array.of(4, 5, 6))],
        });

        await expect(
            wallet.features[SolanaSignMessage].signMessage({
                account: wallet.accounts[0],
                message: Uint8Array.of(47, 48, 49),
            }),
        ).resolves.toEqual([{ signature: new Uint8Array(64).fill(8), signedMessage }]);
        expect(mobileWallet.signMessages).toHaveBeenCalledWith({
            addresses: [encodeBytes(accountPublicKey)],
            payloads: [encodeBytes(Uint8Array.of(47, 48, 49))],
        });
        expect(mockStartRemoteScenario).toHaveBeenCalledTimes(1);
    });

    it('signs in through remote association', async () => {
        const accountPublicKey = Uint8Array.of(51, 52, 53);
        const signedMessage = Uint8Array.of(54, 55, 56);
        const signature = Uint8Array.of(57, 58, 59);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey, {
                sign_in_result: {
                    address: encodeBytes(accountPublicKey),
                    signature: encodeBytes(signature),
                    signed_message: encodeBytes(signedMessage),
                },
            }),
        });
        const { wallet } = createRemoteWallet();

        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(
            wallet.features[SolanaSignIn].signIn({
                domain: 'app.example',
                statement: 'Remote sign in',
            }),
        ).resolves.toEqual([
            {
                account: expect.objectContaining({
                    address: base58.encode(accountPublicKey),
                    publicKey: accountPublicKey,
                }),
                signature,
                signedMessage,
            },
        ]);
        expect(mobileWallet.authorize).toHaveBeenCalledWith({
            chain: SOLANA_MAINNET_CHAIN,
            identity: {
                icon: 'favicon.ico',
                name: 'Example App',
                uri: 'https://example.test',
            },
            sign_in_payload: {
                domain: 'app.example',
                statement: 'Remote sign in',
            },
        });
    });

    it('uses cached remote authorization results without starting a session', async () => {
        const cachedAuthorization = createCachedAuthorization(Uint8Array.of(101, 102, 103), {
            ...DEFAULT_CAPABILITIES,
            features: [],
            supports_sign_and_send_transactions: false,
        });
        const { authorizationCache, wallet } = createRemoteWallet({ cachedAuthorization });
        const changeListener = vi.fn();

        wallet.features[StandardEvents].on('change', changeListener);

        await expect(wallet.features[StandardConnect].connect()).resolves.toEqual({
            accounts: [cachedAuthorization.accounts[0]],
        });

        expect(authorizationCache.get).toHaveBeenCalledTimes(1);
        expect(mockStartRemoteScenario).not.toHaveBeenCalled();
        expect(getSignAndSendFeature(wallet)).toBeDefined();
        expect(getSignTransactionFeature(wallet)).toBeDefined();
        expect(changeListener).toHaveBeenCalledWith({ accounts: wallet.accounts });
    });

    it('rejects remote signing methods when the wallet is not connected', async () => {
        const { wallet } = createRemoteWallet();

        await expect(
            wallet.features[SolanaSignMessage].signMessage({
                account: createWalletAccount(Uint8Array.of(1, 2, 3)),
                message: Uint8Array.of(1, 2, 3),
            }),
        ).rejects.toThrow('Wallet not connected');
        expect(mockStartRemoteScenario).not.toHaveBeenCalled();
    });

    it('runs the wallet-not-found handler for remote association failures', async () => {
        const onWalletNotFound = vi.fn<WalletNotFoundHandler>().mockResolvedValue(undefined);
        const { wallet } = createRemoteWallet({ onWalletNotFound });

        mockStartRemoteScenario.mockRejectedValue(
            new SolanaMobileWalletAdapterError(
                SolanaMobileWalletAdapterErrorCode.ERROR_WALLET_NOT_FOUND,
                'Found no installed wallet that supports the mobile wallet protocol.',
            ),
        );

        await expect(wallet.features[StandardConnect].connect()).rejects.toThrow(
            'Found no installed wallet that supports the mobile wallet protocol.',
        );
        expect(remoteModalInstances[0].close).toHaveBeenCalledTimes(1);
        expect(onWalletNotFound).toHaveBeenCalledWith(wallet);
    });

    it('disconnects remote authorization when reauthorization fails', async () => {
        const accountPublicKey = Uint8Array.of(104, 105, 106);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey),
        });
        const scenarioClose = vi.fn();
        const { authorizationCache, wallet } = createRemoteWallet();

        mobileWallet.authorize
            .mockResolvedValueOnce(createMwaAuthorization(accountPublicKey))
            .mockRejectedValueOnce(new Error('reauthorize failed'));
        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: scenarioClose,
            wallet: Promise.resolve(mobileWallet),
        });

        await wallet.features[StandardConnect].connect();

        await expect(
            wallet.features[SolanaSignMessage].signMessage({
                account: wallet.accounts[0],
                message: Uint8Array.of(1, 2, 3),
            }),
        ).rejects.toThrow('reauthorize failed');

        expect(scenarioClose).toHaveBeenCalledTimes(1);
        expect(authorizationCache.clear).toHaveBeenCalledTimes(1);
        expect(wallet.connected).toBe(false);
    });

    it('rejects remote sign-and-send when the connected wallet reports no support', async () => {
        const accountPublicKey = Uint8Array.of(107, 108, 109);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey),
        });
        const { wallet } = createRemoteWallet();

        mobileWallet.getCapabilities.mockResolvedValueOnce(DEFAULT_CAPABILITIES).mockResolvedValueOnce({
            ...DEFAULT_CAPABILITIES,
            supports_sign_and_send_transactions: false,
        });
        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await wallet.features[StandardConnect].connect();

        await expect(
            getSignAndSendFeature(wallet).signAndSendTransaction({
                account: wallet.accounts[0],
                chain: SOLANA_MAINNET_CHAIN,
                transaction: Uint8Array.of(1, 2, 3),
            }),
        ).rejects.toThrow('connected wallet does not support signAndSendTransaction');
    });

    it('rejects remote sign-in when the wallet omits the sign-in result', async () => {
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(Uint8Array.of(110, 111, 112)),
        });
        const { wallet } = createRemoteWallet();

        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(wallet.features[SolanaSignIn].signIn()).rejects.toThrow(
            'Sign in failed, no sign in result returned by wallet',
        );
    });

    it('signs in remotely with multiple inputs', async () => {
        const accountPublicKey = Uint8Array.of(113, 114, 115);
        const signedMessage = Uint8Array.of(116, 117, 118);
        const signature = Uint8Array.of(119, 120, 121);
        const mobileWallet = createMobileWallet({
            authorization: createMwaAuthorization(accountPublicKey, {
                sign_in_result: {
                    address: encodeBytes(accountPublicKey),
                    signature: encodeBytes(signature),
                    signed_message: encodeBytes(signedMessage),
                },
            }),
        });
        const { wallet } = createRemoteWallet();

        mockStartRemoteScenario.mockResolvedValue({
            associationUrl: new URL('https://example.test/associate'),
            close: vi.fn(),
            wallet: Promise.resolve(mobileWallet),
        });

        await expect(
            wallet.features[SolanaSignIn].signIn({ domain: 'one.example' }, { domain: 'two.example' }),
        ).resolves.toHaveLength(2);
        expect(mobileWallet.authorize).toHaveBeenCalledTimes(2);
    });
});

function createLocalWallet({
    cachedAuthorization,
    onWalletNotFound = vi.fn<WalletNotFoundHandler>().mockResolvedValue(undefined),
}: {
    cachedAuthorization?: Authorization;
    onWalletNotFound?: WalletNotFoundHandler;
} = {}) {
    const authorizationCache = createAuthorizationCache(cachedAuthorization);
    const chainSelector = {
        select: vi.fn().mockResolvedValue(SOLANA_MAINNET_CHAIN),
    };
    const wallet = new LocalSolanaMobileWalletAdapterWallet({
        appIdentity: {
            icon: 'favicon.ico',
            name: 'Example App',
            uri: 'https://example.test',
        },
        authorizationCache,
        chains: [SOLANA_MAINNET_CHAIN],
        chainSelector,
        onWalletNotFound,
    });

    return { authorizationCache, chainSelector, onWalletNotFound, wallet };
}

function createRemoteWallet({
    cachedAuthorization,
    onWalletNotFound = vi.fn<WalletNotFoundHandler>().mockResolvedValue(undefined),
}: {
    cachedAuthorization?: Authorization;
    onWalletNotFound?: WalletNotFoundHandler;
} = {}) {
    const authorizationCache = createAuthorizationCache(cachedAuthorization);
    const chainSelector = {
        select: vi.fn().mockResolvedValue(SOLANA_MAINNET_CHAIN),
    };
    const wallet = new RemoteSolanaMobileWalletAdapterWallet({
        appIdentity: {
            icon: 'favicon.ico',
            name: 'Example App',
            uri: 'https://example.test',
        },
        authorizationCache,
        chains: [SOLANA_MAINNET_CHAIN],
        chainSelector,
        onWalletNotFound,
        remoteHostAuthority: 'remote.example.com',
    });

    return { authorizationCache, chainSelector, onWalletNotFound, wallet };
}

type WalletNotFoundHandler = ConstructorParameters<typeof LocalSolanaMobileWalletAdapterWallet>[0]['onWalletNotFound'];

function createAuthorizationCache(cachedAuthorization?: Authorization) {
    return {
        clear: vi.fn().mockResolvedValue(undefined),
        get: vi.fn().mockResolvedValue(cachedAuthorization),
        set: vi.fn().mockResolvedValue(undefined),
    };
}

function createMobileWallet({
    authorization = createMwaAuthorization(Uint8Array.of(1, 2, 3)),
    capabilities = DEFAULT_CAPABILITIES,
}: {
    authorization?: ReturnType<typeof createMwaAuthorization>;
    capabilities?: Authorization['capabilities'];
} = {}) {
    return {
        authorize: vi.fn().mockResolvedValue(authorization),
        getCapabilities: vi.fn().mockResolvedValue(capabilities),
        signAndSendTransactions: vi.fn(),
        signMessages: vi.fn(),
        signTransactions: vi.fn(),
    };
}

function createMwaAuthorization(
    publicKey: Uint8Array,
    overrides: Partial<{
        auth_token: string;
        sign_in_result: {
            address: string;
            signature: string;
            signed_message: string;
        };
        wallet_uri_base: string;
    }> = {},
) {
    return {
        accounts: [
            {
                address: encodeBytes(publicKey),
                chains: [SOLANA_MAINNET_CHAIN],
                features: [SolanaSignAndSendTransaction, SolanaSignMessage, SolanaSignTransaction],
                icon: 'data:image/svg+xml;base64,icon',
                label: 'Primary',
            },
        ],
        auth_token: 'auth-token',
        wallet_uri_base: 'https://wallet.example',
        ...overrides,
    };
}

function createCachedAuthorization(
    publicKey: Uint8Array,
    capabilities: Authorization['capabilities'] = DEFAULT_CAPABILITIES,
): Authorization {
    return {
        accounts: [createWalletAccount(publicKey)],
        auth_token: 'auth-token',
        capabilities,
        chain: SOLANA_MAINNET_CHAIN,
        wallet_uri_base: 'https://wallet.example',
    };
}

function createWalletAccount(publicKey: Uint8Array): WalletAccount {
    return {
        address: base58.encode(publicKey),
        chains: [SOLANA_MAINNET_CHAIN],
        features: [SolanaSignAndSendTransaction, SolanaSignMessage, SolanaSignTransaction],
        icon: 'data:image/svg+xml;base64,icon',
        label: 'Primary',
        publicKey,
    };
}

function encodeBytes(bytes: Uint8Array): string {
    return window.btoa(String.fromCharCode(...bytes));
}
