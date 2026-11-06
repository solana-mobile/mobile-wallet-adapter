import {
    SolanaSignAndSendTransaction,
    type SolanaSignAndSendTransactionFeature,
    type SolanaSignAndSendTransactionMethod,
    SolanaSignAndSendTransactionOptions,
    type SolanaSignAndSendTransactionOutput,
    SolanaSignIn,
    type SolanaSignInFeature,
    SolanaSignInInput,
    type SolanaSignInMethod,
    type SolanaSignInOutput,
    SolanaSignMessage,
    type SolanaSignMessageFeature,
    type SolanaSignMessageMethod,
    SolanaSignTransaction,
    type SolanaSignTransactionFeature,
    type SolanaSignTransactionMethod,
    type SolanaTransactionVersion,
} from '@solana/wallet-standard-features';
import {
    type Account,
    type AppIdentity,
    type AuthorizationResult,
    AuthToken,
    GetCapabilitiesAPI,
    MobileWallet,
    RemoteScenario,
    SignInPayload,
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
    startNostrScenario,
    startRemoteScenario,
    startScenario,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {
    base58FromUint8Array,
    base64FromUint8Array,
    base64ToUint8Array,
} from '@solana-mobile/mobile-wallet-adapter-protocol/encoding';
import type { IdentifierArray, IdentifierString, Wallet, WalletAccount } from '@wallet-standard/base';
import {
    StandardConnect,
    type StandardConnectFeature,
    type StandardConnectMethod,
    StandardDisconnect,
    type StandardDisconnectFeature,
    type StandardDisconnectMethod,
    StandardEvents,
    type StandardEventsFeature,
    type StandardEventsListeners,
    type StandardEventsNames,
    type StandardEventsOnMethod,
} from '@wallet-standard/features';

import { createDefaultAuthorizationCache } from './createDefaultAuthorizationCache.js';
import { createDefaultChainSelector } from './createDefaultChainSelector.js';
import { createDefaultWalletNotFoundHandler } from './createDefaultWalletNotFoundHandler.js';
import EmbeddedLoadingSpinner from './embedded-modal/loadingSpinner.js';
import RemoteConnectionModal from './embedded-modal/remoteConnectionModal.js';
import { checkLocalNetworkAccessPermission, isLocalWebSocketAvailable } from './getIsSupported.js';
import { icon } from './icon.js';

type WalletCapabilities = Awaited<ReturnType<GetCapabilitiesAPI['getCapabilities']>>;

export type Authorization = AuthorizationResult &
    Readonly<{
        chain: IdentifierString;
        capabilities: WalletCapabilities;
    }>;

export interface AuthorizationCache {
    clear(): Promise<void>;
    get(): Promise<Authorization | undefined>;
    set(authorization: Authorization): Promise<void>;
}

export interface ChainSelector {
    select(chains: IdentifierArray): Promise<IdentifierString>;
}

export const SolanaMobileWalletAdapterWalletName = 'Mobile Wallet Adapter';
export const SolanaMobileWalletAdapterRemoteWalletName = 'Remote Mobile Wallet Adapter';

const SIGNATURE_LENGTH_IN_BYTES = 64;
const DEFAULT_FEATURES = [
    SolanaSignAndSendTransaction,
    SolanaSignTransaction,
    SolanaSignMessage,
    SolanaSignIn,
] as const;
const LOCAL_NOSTR_WALLET_CONNECTION_TIMEOUT = 30_000;

/**
 * Waits for the wallet to join a local association made over a Nostr relay. Over localhost, the protocol
 * stops retrying after 30 s when the wallet's server never appears; over Nostr the relay connects whether
 * or not the wallet ever joins, so that timeout never fires. Fails the same way after the same time.
 */
function waitForLocalNostrWallet<T>(wallet: Promise<T>): Promise<T> {
    let timer: ReturnType<typeof setTimeout> | undefined;
    return Promise.race([
        wallet,
        new Promise<never>((_, reject) => {
            timer = setTimeout(
                () =>
                    reject(
                        new SolanaMobileWalletAdapterError(
                            SolanaMobileWalletAdapterErrorCode.ERROR_SESSION_TIMEOUT,
                            'Timed out waiting for the wallet to connect',
                        ),
                    ),
                LOCAL_NOSTR_WALLET_CONNECTION_TIMEOUT,
            );
        }),
    ]).finally(() => clearTimeout(timer));
}

function areTransactionVersionsEqual(
    a: readonly SolanaTransactionVersion[] | undefined,
    b: readonly SolanaTransactionVersion[] | undefined,
): boolean {
    return a?.length === b?.length && (a ?? []).every((version, ii) => version === b?.[ii]);
}

function getSupportedTransactionVersions(
    features: SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature,
): readonly SolanaTransactionVersion[] | undefined {
    if (SolanaSignAndSendTransaction in features) {
        return features[SolanaSignAndSendTransaction].supportedTransactionVersions;
    } else if (SolanaSignTransaction in features) {
        return features[SolanaSignTransaction].supportedTransactionVersions;
    }
    return undefined;
}

function getErrorMessage(error: unknown): string {
    return error instanceof Error ? error.message : 'Unknown error';
}

/**
 * A connection attempt abandoned because `disconnect()` was called. Rejecting, rather than leaving the
 * attempt pending, lets callers clean up: `StandardWalletAdapter` only resets `connecting` when `connect()`
 * settles.
 */
function connectionCancelledError() {
    return new SolanaMobileWalletAdapterError(
        SolanaMobileWalletAdapterErrorCode.ERROR_ASSOCIATION_CANCELLED,
        'Connection cancelled because disconnect() was called',
        { event: undefined },
    );
}

/**
 * Maps Wallet Standard sign-and-send options onto the protocol's `options` object. The protocol has
 * no preflight commitment (its `commitment` is what to confirm after sending), so
 * `preflightCommitment` is not sent.
 */
function getProtocolSignAndSendOptions(options: SolanaSignAndSendTransactionOptions | undefined) {
    const { commitment, maxRetries, minContextSlot, skipPreflight } = options ?? {};
    const protocolOptions = {
        commitment,
        max_retries: maxRetries,
        min_context_slot: minContextSlot,
        skip_preflight: skipPreflight,
    };
    return Object.values(protocolOptions).some((value) => value != null) ? { options: protocolOptions } : null;
}

export interface SolanaMobileWalletAdapterWallet extends Wallet {
    url: string;
}

interface SolanaMobileWalletAdapterAuthorization {
    get isAuthorized(): boolean;
    get currentAuthorization(): Authorization | undefined;
    get cachedAuthorizationResult(): Promise<Authorization | undefined>;
}

export interface SolanaMobileWalletAdapterWalletConfig {
    appIdentity: AppIdentity;
    authorizationCache?: AuthorizationCache;
    chains: IdentifierArray;
    chainSelector?: ChainSelector;
    onWalletNotFound?: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
}

export interface LocalSolanaMobileWalletAdapterWalletConfig extends SolanaMobileWalletAdapterWalletConfig {
    nostrRelay?: string;
}

export interface NostrSolanaMobileWalletAdapterWalletConfig extends SolanaMobileWalletAdapterWalletConfig {
    nostrRelay: string;
}

export interface RemoteSolanaMobileWalletAdapterWalletConfig extends SolanaMobileWalletAdapterWalletConfig {
    remoteHostAuthority: string;
}

export class LocalSolanaMobileWalletAdapterWallet
    implements SolanaMobileWalletAdapterWallet, SolanaMobileWalletAdapterAuthorization
{
    readonly #listeners: { [E in StandardEventsNames]?: StandardEventsListeners[E][] } = {};
    readonly #version = '1.0.0' as const; // wallet-standard version
    readonly #name = SolanaMobileWalletAdapterWalletName;
    readonly #url = 'https://solanamobile.com/wallets';
    readonly #icon = icon;

    #appIdentity: AppIdentity;
    #authorization: Authorization | undefined;
    #authorizationCache: AuthorizationCache;
    #connecting = false;
    /**
     * Every time the connection is recycled in some way (eg. `disconnect()` is called)
     * increment this and use it to make sure that `transact` calls from the previous
     * 'generation' don't continue to do work and throw exceptions.
     */
    #connectionGeneration = 0;
    #chains: IdentifierArray = [];
    #chainSelector: ChainSelector;
    #optionalFeatures: SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature;
    #onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    #nostrRelay: string | undefined;
    /** Aborted by `disconnect()` to end a Local Network Access permission flow still on screen. */
    #connectionAbortController = new AbortController();

    get version() {
        return this.#version;
    }

    get name() {
        return this.#name;
    }

    get url() {
        return this.#url;
    }

    get icon() {
        return this.#icon;
    }

    get chains() {
        return this.#chains;
    }

    get features(): StandardConnectFeature &
        StandardDisconnectFeature &
        StandardEventsFeature &
        SolanaSignMessageFeature &
        SolanaSignInFeature &
        (SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature) {
        return {
            [StandardConnect]: {
                version: '1.0.0',
                connect: this.#connect,
            },
            [StandardDisconnect]: {
                version: '1.0.0',
                disconnect: this.#disconnect,
            },
            [StandardEvents]: {
                version: '1.0.0',
                on: this.#on,
            },
            [SolanaSignMessage]: {
                version: '1.0.0',
                signMessage: this.#signMessage,
            },
            [SolanaSignIn]: {
                version: '1.0.0',
                signIn: this.#signIn,
            },
            ...this.#optionalFeatures,
        };
    }

    get accounts() {
        return (this.#authorization?.accounts as WalletAccount[]) ?? [];
    }

    constructor({
        appIdentity,
        authorizationCache = createDefaultAuthorizationCache(),
        chains,
        chainSelector = createDefaultChainSelector(),
        onWalletNotFound = createDefaultWalletNotFoundHandler(),
        nostrRelay,
    }: LocalSolanaMobileWalletAdapterWalletConfig) {
        this.#authorizationCache = authorizationCache;
        this.#appIdentity = appIdentity;
        this.#chains = chains;
        this.#chainSelector = chainSelector;
        this.#onWalletNotFound = onWalletNotFound;
        this.#nostrRelay = nostrRelay;
        this.#optionalFeatures = {
            // In MWA 1.0, signAndSend is optional and signTransaction is mandatory. Whereas in MWA 2.0+,
            // signAndSend is mandatory and signTransaction is optional (and soft deprecated). As of mid
            // 2025, all MWA wallets support both signAndSendTransaction and signTransaction so its safe
            // assume both are supported here. The features will be updated based on the actual connected
            // wallets capabilities during connection regardless, so this is safe.
            [SolanaSignAndSendTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signAndSendTransaction: this.#signAndSendTransaction,
            },
            [SolanaSignTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signTransaction: this.#signTransaction,
            },
        };
    }

    get connected(): boolean {
        return !!this.#authorization;
    }

    get isAuthorized(): boolean {
        return !!this.#authorization;
    }

    get currentAuthorization(): Authorization | undefined {
        return this.#authorization;
    }

    get cachedAuthorizationResult(): Promise<Authorization | undefined> {
        return this.#authorizationCache.get();
    }

    #on: StandardEventsOnMethod = (event, listener) => {
        this.#listeners[event]?.push(listener) || (this.#listeners[event] = [listener]);
        return (): void => this.#off(event, listener);
    };

    #emit<E extends StandardEventsNames>(event: E, ...args: Parameters<StandardEventsListeners[E]>): void {
        // eslint-disable-next-line prefer-spread
        this.#listeners[event]?.forEach((listener) => listener.apply(null, args));
    }

    #off<E extends StandardEventsNames>(event: E, listener: StandardEventsListeners[E]): void {
        this.#listeners[event] = this.#listeners[event]?.filter((existingListener) => listener !== existingListener);
    }

    #connect: StandardConnectMethod = async ({ silent } = {}) => {
        if (this.#connecting || this.connected) {
            return { accounts: this.accounts };
        }
        this.#connecting = true;
        try {
            if (silent) {
                const generation = this.#connectionGeneration;
                const cachedAuthorization = await this.#authorizationCache.get();
                if (this.#connectionGeneration !== generation) throw connectionCancelledError();
                if (cachedAuthorization) {
                    await this.#handleWalletCapabilitiesResult(cachedAuthorization.capabilities);
                    await this.#handleAuthorizationResult(cachedAuthorization);
                } else {
                    return { accounts: this.accounts };
                }
            } else {
                await this.#performAuthorization();
            }
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    };

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            // Each await below can span a disconnect(); after one, nothing from this attempt may be applied.
            const generation = this.#connectionGeneration;
            const throwIfDisconnected = () => {
                if (this.#connectionGeneration !== generation) throw connectionCancelledError();
            };
            const cachedAuthorizationResult = await this.#authorizationCache.get();
            throwIfDisconnected();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            const selectedChain = await this.#chainSelector.select(this.#chains);
            throwIfDisconnected();
            const authorization = await this.#transact(
                async (wallet) => {
                    const [capabilities, mwaAuthorizationResult] = await Promise.all([
                        wallet.getCapabilities(),
                        wallet.authorize({
                            chain: selectedChain,
                            identity: this.#appIdentity,
                            sign_in_payload: signInPayload,
                        }),
                    ]);
                    return {
                        ...mwaAuthorizationResult,
                        accounts: this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts),
                        chain: selectedChain,
                        capabilities,
                    };
                },
                { cancelOnDisconnect: true },
            );
            // The user may approve after the app disconnected; that approval must not reconnect the app.
            throwIfDisconnected();
            await Promise.all([
                this.#handleWalletCapabilitiesResult(authorization.capabilities),
                // A failed cache write must not fail the connection; the authorization is still applied.
                this.#authorizationCache.set(authorization).catch(() => {}),
                this.#handleAuthorizationResult(authorization),
            ]);
            return authorization;
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #handleAuthorizationResult = async (authorization: Authorization) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorization == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorization?.accounts.length !== authorization.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorization.accounts.some((account, ii) => account.address !== authorization.accounts[ii].address);
        this.#authorization = authorization;
        if (didPublicKeysChange) {
            this.#emit('change', { accounts: this.accounts });
        }
    };

    #handleWalletCapabilitiesResult = async (
        capabilities: Awaited<ReturnType<GetCapabilitiesAPI['getCapabilities']>>,
    ) => {
        // TODO: investigate why using SolanaSignTransactions constant breaks treeshaking
        const supportsSignTransaction = capabilities.features.includes('solana:signTransactions'); //SolanaSignTransactions);
        const supportsSignAndSendTransaction = capabilities.supports_sign_and_send_transactions;
        const didCapabilitiesChange =
            SolanaSignAndSendTransaction in this.features !== supportsSignAndSendTransaction ||
            SolanaSignTransaction in this.features !== supportsSignTransaction ||
            !areTransactionVersionsEqual(
                getSupportedTransactionVersions(this.#optionalFeatures),
                capabilities.supported_transaction_versions,
            );
        this.#optionalFeatures = {
            ...((supportsSignAndSendTransaction || (!supportsSignAndSendTransaction && !supportsSignTransaction)) && {
                [SolanaSignAndSendTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: capabilities.supported_transaction_versions,
                    signAndSendTransaction: this.#signAndSendTransaction,
                },
            }),
            ...(supportsSignTransaction && {
                [SolanaSignTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: capabilities.supported_transaction_versions,
                    signTransaction: this.#signTransaction,
                },
            }),
        } as SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature;
        if (didCapabilitiesChange) {
            this.#emit('change', { features: this.features });
        }
    };

    #performReauthorization = async (wallet: MobileWallet, authToken: AuthToken, chain: IdentifierString) => {
        const generation = this.#connectionGeneration;
        let authorization: Authorization;
        try {
            const [capabilities, mwaAuthorizationResult] = await Promise.all([
                this.#authorization?.capabilities ?? (await wallet.getCapabilities()),
                wallet.authorize({
                    auth_token: authToken,
                    identity: this.#appIdentity,
                    chain: chain,
                }),
            ]);
            authorization = {
                ...mwaAuthorizationResult,
                accounts: this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts),
                chain,
                capabilities,
            };
        } catch (e) {
            this.#disconnect();
            throw new Error(getErrorMessage(e), { cause: e });
        }
        // A reauthorization that completes after disconnect() must not reconnect the app or re-cache it.
        if (this.#connectionGeneration !== generation) return;
        await Promise.all([
            // A failed cache write must not stop the signing request that triggered the reauthorization.
            this.#authorizationCache.set(authorization).catch(() => {}),
            this.#handleAuthorizationResult(authorization),
        ]);
    };

    #disconnect: StandardDisconnectMethod = async () => {
        this.#authorizationCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        // Ends any Local Network Access permission flow still on screen.
        this.#connectionAbortController.abort();
        this.#connectionAbortController = new AbortController();
        this.#authorization = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(
        callback: (wallet: MobileWallet) => TReturn,
        // Connection attempts reject when disconnect() interrupts them; other requests keep their existing behavior.
        { cancelOnDisconnect = false }: { cancelOnDisconnect?: boolean } = {},
    ) => {
        const walletUriBase = this.#authorization?.wallet_uri_base;
        const config = walletUriBase ? { baseUri: walletUriBase } : undefined;
        const currentConnectionGeneration = this.#connectionGeneration;
        const { signal } = this.#connectionAbortController;
        const loadingSpinner = new EmbeddedLoadingSpinner();
        try {
            // No overall timeout: the permission flow lasts as long as the user takes to answer or cancel it,
            // `startScenario` settles once the wallet launches (or ERROR_WALLET_NOT_FOUND), and the socket
            // phase has the protocol's own connection timeout.
            const useWebSocket = await isLocalWebSocketAvailable();
            if (!useWebSocket && !this.#nostrRelay) {
                // Rejects with `signal.reason` if disconnect() aborts it.
                await checkLocalNetworkAccessPermission(signal);
                if (this.#connectionGeneration !== currentConnectionGeneration) throw connectionCancelledError();
            }
            // Begin local connection, show loading spinner while we connect
            loadingSpinner.init();
            const overNostr = !useWebSocket && !!this.#nostrRelay;
            const { wallet, close } =
                overNostr && this.#nostrRelay
                    ? await startNostrScenario({
                          ...config,
                          connectionType: 'local' as const,
                          relayDomain: this.#nostrRelay,
                      })
                    : await startScenario(config);
            loadingSpinner.addEventListener('close', (event) => {
                if (event) close();
            });
            loadingSpinner.open();
            try {
                return await callback(await (overNostr ? waitForLocalNostrWallet(wallet) : wallet));
            } finally {
                // Close even when a request fails or is declined, so the wallet isn't left waiting.
                loadingSpinner.close();
                close();
            }
        } catch (e) {
            loadingSpinner.close();
            if (this.#connectionGeneration !== currentConnectionGeneration) {
                if (cancelOnDisconnect) throw connectionCancelledError();
                await new Promise(() => {}); // Never resolve.
            }
            if (
                e instanceof Error &&
                e.name === 'SolanaMobileWalletAdapterError' &&
                (
                    e as SolanaMobileWalletAdapterError<
                        (typeof SolanaMobileWalletAdapterErrorCode)[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >
                ).code === 'ERROR_WALLET_NOT_FOUND'
            ) {
                await this.#onWalletNotFound(this);
            }
            throw e;
        }
    };

    #assertIsAuthorized = () => {
        if (!this.#authorization) throw new Error('Wallet not connected');
        return { authToken: this.#authorization.auth_token, chain: this.#authorization.chain };
    };

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = base64ToUint8Array(account.address);
            return {
                address: base58FromUint8Array(publicKey),
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? this.#chains,
                // TODO: get supported features from getCapabilities API
                features: account.features ?? DEFAULT_FEATURES,
            } as WalletAccount;
        });
    };

    #performSignTransactions = async (transactions: Uint8Array[]) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            const base64Transactions = transactions.map((tx) => {
                return base64FromUint8Array(tx);
            });
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedTransactions = (
                    await wallet.signTransactions({
                        payloads: base64Transactions,
                    })
                ).signed_payloads.map(base64ToUint8Array);
                return signedTransactions;
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #performSignAndSendTransaction = async (
        transaction: Uint8Array,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken, chain),
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const base64Transaction = base64FromUint8Array(transaction);
                    const signatures = (
                        await wallet.signAndSendTransactions({
                            ...getProtocolSignAndSendOptions(options),
                            payloads: [base64Transaction],
                        })
                    ).signatures.map(base64ToUint8Array);
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction');
                }
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {
        const outputs: SolanaSignAndSendTransactionOutput[] = [];

        for (const input of inputs) {
            const signature = await this.#performSignAndSendTransaction(input.transaction, input.options);
            outputs.push({ signature });
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        return (await this.#performSignTransactions(inputs.map(({ transaction }) => transaction))).map(
            (signedTransaction) => {
                return { signedTransaction };
            },
        );
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => base64FromUint8Array(new Uint8Array(account.publicKey)));
        const messages = inputs.map(({ message }) => base64FromUint8Array(message));
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedMessages = (
                    await wallet.signMessages({
                        addresses: addresses,
                        payloads: messages,
                    })
                ).signed_payloads.map(base64ToUint8Array);
                return signedMessages.map((signedMessage) => {
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) };
                });
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #signIn: SolanaSignInMethod = async (...inputs) => {
        const outputs: SolanaSignInOutput[] = [];

        if (inputs.length > 1) {
            for (const input of inputs) {
                outputs.push(await this.#performSignIn(input));
            }
        } else {
            return [await this.#performSignIn(inputs[0])];
        }

        return outputs;
    };

    #performSignIn = async (input?: SolanaSignInInput) => {
        this.#connecting = true;
        try {
            const authorizationResult = await this.#performAuthorization({
                ...input,
                domain: input?.domain ?? window.location.host,
            });
            if (!authorizationResult.sign_in_result) {
                throw new Error('Sign in failed, no sign in result returned by wallet');
            }
            const signedInAddress = authorizationResult.sign_in_result.address;
            const signedInAccount = authorizationResult.accounts.find((acc) => acc.address == signedInAddress);
            return {
                account: {
                    ...(signedInAccount ?? {
                        address: base58FromUint8Array(base64ToUint8Array(signedInAddress)),
                    }),
                    publicKey: base64ToUint8Array(signedInAddress),
                    chains: signedInAccount?.chains ?? this.#chains,
                    features: signedInAccount?.features ?? authorizationResult.capabilities.features,
                },
                signedMessage: base64ToUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: base64ToUint8Array(authorizationResult.sign_in_result.signature),
            };
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        } finally {
            this.#connecting = false;
        }
    };
}

export class RemoteSolanaMobileWalletAdapterWallet
    implements SolanaMobileWalletAdapterWallet, SolanaMobileWalletAdapterAuthorization
{
    readonly #listeners: { [E in StandardEventsNames]?: StandardEventsListeners[E][] } = {};
    readonly #version = '1.0.0' as const; // wallet-standard version
    readonly #name = SolanaMobileWalletAdapterRemoteWalletName;
    readonly #url = 'https://solanamobile.com/wallets';
    readonly #icon = icon;

    #appIdentity: AppIdentity;
    #authorization: Authorization | undefined;
    #authorizationCache: AuthorizationCache;
    #connecting = false;
    /**
     * Every time the connection is recycled in some way (eg. `disconnect()` is called)
     * increment this and use it to make sure that `transact` calls from the previous
     * 'generation' don't continue to do work and throw exceptions.
     */
    #connectionGeneration = 0;
    #chains: IdentifierArray = [];
    #chainSelector: ChainSelector;
    #optionalFeatures: SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature;
    #onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    #relay: { kind: 'reflector' | 'nostr'; domain: string };
    #session: { close: () => void; wallet: MobileWallet } | undefined;
    // Settles when the last cache clear after a lost session does; cache lookups wait for it.
    #authorizationCacheCleared: Promise<void> = Promise.resolve();
    // Auth tokens the cache must never restore: every authorization dropped with a lost session, and every
    // token a reauthorization replaced.
    #droppedAuthTokens = new Set<string>();
    // Cache writes still in flight; cache lookups wait for them so a late write can't land after the read.
    #pendingCacheWrites = new Set<Promise<void>>();

    #writeAuthorizationCache = (authorization: Authorization) => {
        const write = this.#authorizationCache.set(authorization).finally(() => this.#pendingCacheWrites.delete(write));
        this.#pendingCacheWrites.add(write);
        // A failed cache write must not fail the request that made it; callers still wait for it to settle.
        return write.catch(() => {});
    };

    get version() {
        return this.#version;
    }

    get name() {
        return this.#name;
    }

    get url() {
        return this.#url;
    }

    get icon() {
        return this.#icon;
    }

    get chains() {
        return this.#chains;
    }

    get features(): StandardConnectFeature &
        StandardDisconnectFeature &
        StandardEventsFeature &
        SolanaSignMessageFeature &
        SolanaSignInFeature &
        (SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature) {
        return {
            [StandardConnect]: {
                version: '1.0.0',
                connect: this.#connect,
            },
            [StandardDisconnect]: {
                version: '1.0.0',
                disconnect: this.#disconnect,
            },
            [StandardEvents]: {
                version: '1.0.0',
                on: this.#on,
            },
            [SolanaSignMessage]: {
                version: '1.0.0',
                signMessage: this.#signMessage,
            },
            [SolanaSignIn]: {
                version: '1.0.0',
                signIn: this.#signIn,
            },
            ...this.#optionalFeatures,
        };
    }

    get accounts() {
        return (this.#authorization?.accounts as WalletAccount[]) ?? [];
    }

    constructor(config: RemoteSolanaMobileWalletAdapterWalletConfig);
    constructor(config: NostrSolanaMobileWalletAdapterWalletConfig);
    constructor(config: RemoteSolanaMobileWalletAdapterWalletConfig | NostrSolanaMobileWalletAdapterWalletConfig) {
        const {
            appIdentity,
            authorizationCache = createDefaultAuthorizationCache(),
            chains,
            chainSelector = createDefaultChainSelector(),
            onWalletNotFound = createDefaultWalletNotFoundHandler(),
        } = config;
        this.#authorizationCache = authorizationCache;
        this.#appIdentity = appIdentity;
        this.#chains = chains;
        this.#chainSelector = chainSelector;
        this.#relay =
            'remoteHostAuthority' in config
                ? { kind: 'reflector', domain: config.remoteHostAuthority }
                : { kind: 'nostr', domain: config.nostrRelay };
        this.#onWalletNotFound = onWalletNotFound;
        this.#optionalFeatures = {
            // In MWA 1.0, signAndSend is optional and signTransaction is mandatory. Whereas in MWA 2.0+,
            // signAndSend is mandatory and signTransaction is optional (and soft deprecated). As of mid
            // 2025, all MWA wallets support both signAndSendTransaction and signTransaction so its safe
            // assume both are supported here. The features will be updated based on the actual connected
            // wallets capabilities during connection regardless, so this is safe.
            [SolanaSignAndSendTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signAndSendTransaction: this.#signAndSendTransaction,
            },
            [SolanaSignTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signTransaction: this.#signTransaction,
            },
        };
    }

    get connected(): boolean {
        return !!this.#session && !!this.#authorization;
    }

    get isAuthorized(): boolean {
        return !!this.#authorization;
    }

    get currentAuthorization(): Authorization | undefined {
        return this.#authorization;
    }

    get cachedAuthorizationResult(): Promise<Authorization | undefined> {
        return this.#authorizationCache.get();
    }

    #on: StandardEventsOnMethod = (event, listener) => {
        this.#listeners[event]?.push(listener) || (this.#listeners[event] = [listener]);
        return (): void => this.#off(event, listener);
    };

    #emit<E extends StandardEventsNames>(event: E, ...args: Parameters<StandardEventsListeners[E]>): void {
        // eslint-disable-next-line prefer-spread
        this.#listeners[event]?.forEach((listener) => listener.apply(null, args));
    }

    #off<E extends StandardEventsNames>(event: E, listener: StandardEventsListeners[E]): void {
        this.#listeners[event] = this.#listeners[event]?.filter((existingListener) => listener !== existingListener);
    }

    #connect: StandardConnectMethod = async (_input = {}) => {
        if (this.#connecting || this.connected) {
            return { accounts: this.accounts };
        }
        this.#connecting = true;
        try {
            await this.#performAuthorization();
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    };

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            // Each await below can span a disconnect(); after one, nothing from this attempt may be applied.
            const generation = this.#connectionGeneration;
            const throwIfDisconnected = () => {
                if (this.#connectionGeneration !== generation) throw connectionCancelledError();
            };
            await this.#authorizationCacheCleared;
            await Promise.allSettled([...this.#pendingCacheWrites]);
            let cachedAuthorizationResult = await this.#authorizationCache.get();
            throwIfDisconnected();
            if (cachedAuthorizationResult && this.#droppedAuthTokens.has(cachedAuthorizationResult.auth_token)) {
                // An authorization dropped with a lost session, back because its clear failed or a pending write
                // landed after it. Never restore it; try to clear it again.
                cachedAuthorizationResult = undefined;
                await this.#authorizationCache.clear().catch(() => {});
            }
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            this.#session?.close();
            this.#session = undefined;
            const selectedChain = await this.#chainSelector.select(this.#chains);
            throwIfDisconnected();
            const authorizationResult = await this.#transact(
                async (wallet) => {
                    const [capabilities, mwaAuthorizationResult] = await Promise.all([
                        wallet.getCapabilities(),
                        wallet.authorize({
                            chain: selectedChain,
                            identity: this.#appIdentity,
                            sign_in_payload: signInPayload,
                        }),
                    ]);
                    return {
                        ...mwaAuthorizationResult,
                        accounts: this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts),
                        chain: selectedChain,
                        capabilities,
                    };
                },
                { cancelOnDisconnect: true },
            );
            // The user may approve after the app disconnected (disconnect() closed the session); that approval
            // must not reconnect the app.
            throwIfDisconnected();
            await Promise.all([
                this.#handleWalletCapabilitiesResult(authorizationResult.capabilities),
                this.#writeAuthorizationCache(authorizationResult),
                this.#handleAuthorizationResult(authorizationResult),
            ]);
            return authorizationResult;
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #handleAuthorizationResult = async (authorization: Authorization) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorization == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorization?.accounts.length !== authorization.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorization.accounts.some((account, ii) => account.address !== authorization.accounts[ii].address);
        this.#authorization = authorization;
        if (didPublicKeysChange) {
            this.#emit('change', { accounts: this.accounts });
        }
    };

    #handleWalletCapabilitiesResult = async (
        capabilities: Awaited<ReturnType<GetCapabilitiesAPI['getCapabilities']>>,
    ) => {
        // TODO: investigate why using SolanaSignTransactions constant breaks treeshaking
        const supportsSignTransaction = capabilities.features.includes('solana:signTransactions'); //SolanaSignTransactions);
        const supportsSignAndSendTransaction =
            capabilities.supports_sign_and_send_transactions ||
            capabilities.features.includes('solana:signAndSendTransaction');
        const didCapabilitiesChange =
            SolanaSignAndSendTransaction in this.features !== supportsSignAndSendTransaction ||
            SolanaSignTransaction in this.features !== supportsSignTransaction ||
            !areTransactionVersionsEqual(
                getSupportedTransactionVersions(this.#optionalFeatures),
                capabilities.supported_transaction_versions,
            );
        this.#optionalFeatures = {
            ...(supportsSignAndSendTransaction && {
                [SolanaSignAndSendTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: capabilities.supported_transaction_versions,
                    signAndSendTransaction: this.#signAndSendTransaction,
                },
            }),
            ...(supportsSignTransaction && {
                [SolanaSignTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: capabilities.supported_transaction_versions,
                    signTransaction: this.#signTransaction,
                },
            }),
        } as SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature;
        if (didCapabilitiesChange) {
            this.#emit('change', { features: this.features });
        }
    };

    #performReauthorization = async (wallet: MobileWallet, authToken: AuthToken, chain: IdentifierString) => {
        const generation = this.#connectionGeneration;
        let authorization: Authorization;
        try {
            const [capabilities, mwaAuthorizationResult] = await Promise.all([
                this.#authorization?.capabilities ?? (await wallet.getCapabilities()),
                wallet.authorize({
                    auth_token: authToken,
                    identity: this.#appIdentity,
                    chain: chain,
                }),
            ]);
            authorization = {
                ...mwaAuthorizationResult,
                accounts: this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts),
                chain,
                capabilities,
            };
        } catch (e) {
            this.#disconnect();
            throw new Error(getErrorMessage(e), { cause: e });
        }
        // A reauthorization that completes after disconnect() must not reconnect the app or re-cache it.
        if (this.#connectionGeneration !== generation) return;
        // A wallet may issue a new token; the token it replaced must never come back from the cache.
        if (authorization.auth_token !== authToken) this.#droppedAuthTokens.add(authToken);
        await Promise.all([
            this.#writeAuthorizationCache(authorization),
            this.#handleAuthorizationResult(authorization),
        ]);
    };

    /**
     * Runs `callback` on the open session. A session that can't move forward is closed, so the wallet isn't
     * left waiting on it: a wallet call failed in transport, or the dapp still isn't authorized (e.g.
     * authorization was declined). The session stays open after the wallet's own error reply while authorized
     * (e.g. a declined signing request), and after an error in the callback's own code (e.g. a malformed
     * response), since neither means the connection is gone.
     */
    #runInSession = async <TReturn>(
        session: { close: () => void; wallet: MobileWallet },
        callback: (wallet: MobileWallet) => TReturn,
    ) => {
        // Set when a wallet call fails without the wallet's error reply, i.e. the transport failed.
        let transportFailed = false;
        const trackedWallet = new Proxy(session.wallet, {
            get: (target, property, receiver) => {
                const member = Reflect.get(target, property, receiver);
                if (typeof member !== 'function') return member;
                return async (...args: unknown[]) => {
                    try {
                        return await member.apply(target, args);
                    } catch (e) {
                        if (!(e instanceof Error && e.name === 'SolanaMobileWalletAdapterProtocolError')) {
                            transportFailed = true;
                        }
                        throw e;
                    }
                };
            },
        });
        try {
            return await callback(trackedWallet);
        } catch (e) {
            // `#disconnect` may already have closed and cleared it.
            if (this.#session === session && (!this.#authorization || transportFailed)) {
                session.close();
                this.#session = undefined;
                if (this.#authorization) {
                    // The connection was lost. A remote authorization is only usable with its session, so drop
                    // it too: otherwise the next connect() would return the cached accounts with no session.
                    // (Not `#disconnect()`: that also bumps the connection generation, which makes the caller hang.)
                    this.#droppedAuthTokens.add(this.#authorization.auth_token);
                    this.#authorization = undefined;
                    // A failed clear still leaves the in-memory authorization dropped and the change emitted.
                    this.#authorizationCacheCleared = this.#authorizationCache.clear().catch(() => {});
                    this.#emit('change', { accounts: this.accounts });
                    // Settle the clear before the request rejects, so a reconnect can't read the stale entry.
                    await this.#authorizationCacheCleared;
                }
            }
            throw e;
        }
    };

    #disconnect: StandardDisconnectMethod = async () => {
        this.#session?.close();
        this.#authorizationCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        this.#authorization = undefined;
        this.#session = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(
        callback: (wallet: MobileWallet) => TReturn,
        // Connection attempts reject when disconnect() interrupts them; other requests keep their existing behavior.
        { cancelOnDisconnect = false }: { cancelOnDisconnect?: boolean } = {},
    ) => {
        const walletUriBase = this.#authorization?.wallet_uri_base;
        const baseConfig = walletUriBase ? { baseUri: walletUriBase } : undefined;
        const currentConnectionGeneration = this.#connectionGeneration;
        const modal = new RemoteConnectionModal();

        if (this.#session) {
            return this.#runInSession(this.#session, callback);
        }

        try {
            // Begin remote connection, show modal with loading anim while we connect
            modal.init();
            modal.open();
            const { associationUrl, close, wallet } =
                this.#relay.kind == 'reflector'
                    ? await startRemoteScenario({
                          ...baseConfig,
                          remoteHostAuthority: this.#relay.domain,
                      })
                    : ((await startNostrScenario({
                          ...baseConfig,
                          connectionType: 'remote',
                          relayDomain: this.#relay.domain,
                      })) as RemoteScenario);

            // Reflector is now connected, update the connection modal with qr code
            const removeCloseListener = modal.addEventListener('close', (event?: Event) => {
                if (event) close();
            });
            modal.populateQRCode(associationUrl.toString());

            // Wait for the wallet to be connected, then close the connection modal and proceed
            // Close the scenario if the wallet never connects; closing the modal from code doesn't.
            const connectedWallet = await wallet.catch((e: unknown) => {
                close();
                throw e;
            });
            if (this.#connectionGeneration !== currentConnectionGeneration) {
                // disconnect() ran while the QR code waited, before there was a session for it to close.
                close();
                throw connectionCancelledError();
            }
            this.#session = { close, wallet: connectedWallet };
            removeCloseListener();
            modal.close();
            return await this.#runInSession(this.#session, callback);
        } catch (e) {
            modal.close();
            if (this.#connectionGeneration !== currentConnectionGeneration) {
                if (cancelOnDisconnect) throw connectionCancelledError();
                await new Promise(() => {}); // Never resolve.
            }
            if (
                e instanceof Error &&
                e.name === 'SolanaMobileWalletAdapterError' &&
                (
                    e as SolanaMobileWalletAdapterError<
                        (typeof SolanaMobileWalletAdapterErrorCode)[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >
                ).code === 'ERROR_WALLET_NOT_FOUND'
            ) {
                await this.#onWalletNotFound(this);
            }
            throw e;
        }
    };

    #assertIsAuthorized = () => {
        if (!this.#authorization) throw new Error('Wallet not connected');
        return { authToken: this.#authorization.auth_token, chain: this.#authorization.chain };
    };

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = base64ToUint8Array(account.address);
            return {
                address: base58FromUint8Array(publicKey),
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? this.#chains,
                // TODO: get supported features from getCapabilities API
                features: account.features ?? DEFAULT_FEATURES,
            } as WalletAccount;
        });
    };

    #performSignTransactions = async (transactions: Uint8Array[]) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedTransactions = (
                    await wallet.signTransactions({
                        payloads: transactions.map(base64FromUint8Array),
                    })
                ).signed_payloads.map(base64ToUint8Array);
                return signedTransactions;
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #performSignAndSendTransaction = async (
        transaction: Uint8Array,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken, chain),
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const signatures = (
                        await wallet.signAndSendTransactions({
                            ...getProtocolSignAndSendOptions(options),
                            payloads: [base64FromUint8Array(transaction)],
                        })
                    ).signatures.map(base64ToUint8Array);
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction');
                }
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {
        const outputs: SolanaSignAndSendTransactionOutput[] = [];
        for (const input of inputs) {
            const signature = await this.#performSignAndSendTransaction(input.transaction, input.options);
            outputs.push({ signature });
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        return (await this.#performSignTransactions(inputs.map(({ transaction }) => transaction))).map(
            (signedTransaction) => {
                return { signedTransaction };
            },
        );
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => base64FromUint8Array(new Uint8Array(account.publicKey)));
        const messages = inputs.map(({ message }) => base64FromUint8Array(message));
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedMessages = (
                    await wallet.signMessages({
                        addresses: addresses,
                        payloads: messages,
                    })
                ).signed_payloads.map(base64ToUint8Array);
                return signedMessages.map((signedMessage) => {
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) };
                });
            });
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        }
    };

    #signIn: SolanaSignInMethod = async (...inputs) => {
        const outputs: SolanaSignInOutput[] = [];

        if (inputs.length > 1) {
            for (const input of inputs) {
                outputs.push(await this.#performSignIn(input));
            }
        } else {
            return [await this.#performSignIn(inputs[0])];
        }

        return outputs;
    };

    #performSignIn = async (input?: SolanaSignInInput) => {
        this.#connecting = true;
        try {
            const authorizationResult = await this.#performAuthorization({
                ...input,
                domain: input?.domain ?? window.location.host,
            });
            if (!authorizationResult.sign_in_result) {
                throw new Error('Sign in failed, no sign in result returned by wallet');
            }
            const signedInAddress = authorizationResult.sign_in_result.address;
            const signedInAccount = authorizationResult.accounts.find((acc) => acc.address == signedInAddress);
            return {
                account: {
                    ...(signedInAccount ?? {
                        address: base58FromUint8Array(base64ToUint8Array(signedInAddress)),
                    }),
                    publicKey: base64ToUint8Array(signedInAddress),
                    chains: signedInAccount?.chains ?? this.#chains,
                    features: signedInAccount?.features ?? authorizationResult.capabilities.features,
                },
                signedMessage: base64ToUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: base64ToUint8Array(authorizationResult.sign_in_result.signature),
            };
        } catch (e) {
            throw new Error(getErrorMessage(e), { cause: e });
        } finally {
            this.#connecting = false;
        }
    };
}
