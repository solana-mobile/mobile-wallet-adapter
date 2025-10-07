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
} from '@solana/wallet-standard-features';
import RemoteConnectionModal from './embedded-modal/remoteConnectionModal.js';
import {
    type Account,
    type AppIdentity,
    type AuthorizationResult,
    AuthToken,
    GetCapabilitiesAPI,
    MobileWallet,
    SignInPayload,
    type SolanaMobileWalletAdapterError,
    type SolanaMobileWalletAdapterErrorCode,
    startRemoteScenario,
    transact,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
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
import { icon } from './icon';
import { fromUint8Array, toUint8Array } from './base64Utils';
import base58 from 'bs58';

type WalletCapabilities = Awaited<ReturnType<GetCapabilitiesAPI["getCapabilities"]>>;

export type Authorization = AuthorizationResult & Readonly<{
    chain: IdentifierString;
    capabilities: WalletCapabilities;
}>

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
const DEFAULT_FEATURES = [SolanaSignAndSendTransaction, SolanaSignTransaction, SolanaSignMessage, SolanaSignIn] as const;

export interface SolanaMobileWalletAdapterWallet extends Wallet {
    url: string
}

interface SolanaMobileWalletAdapterAuthorization {
    get isAuthorized(): boolean;
    get currentAuthorization(): Authorization | undefined;
    get cachedAuthorizationResult(): Promise<Authorization | undefined>;
}

export class LocalSolanaMobileWalletAdapterWallet implements SolanaMobileWalletAdapterWallet, SolanaMobileWalletAdapterAuthorization {
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

    get version() {
        return this.#version;
    }

    get name() {
        return this.#name;
    }

    get url() {
        return this.#url
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
        return this.#authorization?.accounts as WalletAccount[] ?? [];
    }

    constructor(config: {
        appIdentity: AppIdentity;
        authorizationCache: AuthorizationCache;
        chains: IdentifierArray;
        chainSelector: ChainSelector;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    }) {
        this.#authorizationCache = config.authorizationCache;
        this.#appIdentity = config.appIdentity;
        this.#chains = config.chains;
        this.#chainSelector = config.chainSelector;
        this.#onWalletNotFound = config.onWalletNotFound;
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
                const cachedAuthorization = await this.#authorizationCache.get();
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
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    }

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            const cachedAuthorizationResult = await this.#authorizationCache.get();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            const selectedChain = await this.#chainSelector.select(this.#chains);
            return await this.#transact(async (wallet) => {
                const [capabilities, mwaAuthorizationResult] = await Promise.all([
                    wallet.getCapabilities(),
                    wallet.authorize({
                        chain: selectedChain,
                        identity: this.#appIdentity,
                        sign_in_payload: signInPayload,
                    })
                ]);

                const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
                const authorization = { ...mwaAuthorizationResult, 
                    accounts, chain: selectedChain, capabilities: capabilities};
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                Promise.all([
                    this.#handleWalletCapabilitiesResult(capabilities),
                    this.#authorizationCache.set(authorization),
                    this.#handleAuthorizationResult(authorization),
                ]);

                return authorization;
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #handleAuthorizationResult = async (authorization: Authorization) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorization == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorization?.accounts.length !== authorization.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorization.accounts.some(
                (account, ii) => account.address !== authorization.accounts[ii].address,
            );
        this.#authorization = authorization;
        if (didPublicKeysChange) {
            this.#emit('change',{ accounts: this.accounts });
        }
    }

    #handleWalletCapabilitiesResult = async (
        capabilities: Awaited<ReturnType<GetCapabilitiesAPI['getCapabilities']>>
    ) => {
        // TODO: investigate why using SolanaSignTransactions constant breaks treeshaking
        const supportsSignTransaction = capabilities.features.includes('solana:signTransactions');//SolanaSignTransactions);
        const supportsSignAndSendTransaction = capabilities.supports_sign_and_send_transactions;
        const didCapabilitiesChange = 
            SolanaSignAndSendTransaction in this.features !== supportsSignAndSendTransaction ||
            SolanaSignTransaction in this.features !== supportsSignTransaction;
        this.#optionalFeatures = {
            ...((supportsSignAndSendTransaction || (!supportsSignAndSendTransaction && !supportsSignTransaction)) && {
                [SolanaSignAndSendTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: ['legacy', 0],
                    signAndSendTransaction: this.#signAndSendTransaction,
                },
            }),
            ...(supportsSignTransaction && {
                [SolanaSignTransaction]: {
                    version: '1.0.0',
                    supportedTransactionVersions: ['legacy', 0],
                    signTransaction: this.#signTransaction,
                },
            }),
        } as SolanaSignAndSendTransactionFeature | SolanaSignTransactionFeature;
        if (didCapabilitiesChange) {
            this.#emit('change', { features: this.features });
        }
    }

    #performReauthorization = async (wallet: MobileWallet, authToken: AuthToken, chain: IdentifierString) => {
        try {
            const [capabilities, mwaAuthorizationResult] = await Promise.all([
                this.#authorization?.capabilities ?? await wallet.getCapabilities(),
                wallet.authorize({
                    auth_token: authToken,
                    identity: this.#appIdentity,
                    chain: chain
                })
            ]);
            
            const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
            const authorization = { ...mwaAuthorizationResult, 
                accounts: accounts, chain: chain, capabilities: capabilities
            };
            // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            Promise.all([
                this.#authorizationCache.set(authorization),
                this.#handleAuthorizationResult(authorization),
            ]);
        } catch (e) {
            this.#disconnect();
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #disconnect: StandardDisconnectMethod = async () => {
        this.#authorizationCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        this.#authorization = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(callback: (wallet: MobileWallet) => TReturn) => {
        const walletUriBase = this.#authorization?.wallet_uri_base;
        const config = walletUriBase ? { baseUri: walletUriBase } : undefined;
        const currentConnectionGeneration = this.#connectionGeneration;
        try {
            return await transact(callback, config);
        } catch (e) {
            if (this.#connectionGeneration !== currentConnectionGeneration) {
                await new Promise(() => {}); // Never resolve.
            }
            if (
                e instanceof Error &&
                e.name === 'SolanaMobileWalletAdapterError' &&
                (
                    e as SolanaMobileWalletAdapterError<
                        typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >
                ).code === 'ERROR_WALLET_NOT_FOUND'
            ) {
                await this.#onWalletNotFound(this);
            }
            throw e;
        }
    }

    #assertIsAuthorized = () => {
        if (!this.#authorization) throw new Error('Wallet not connected');
        return { authToken: this.#authorization.auth_token, chain: this.#authorization.chain };
    }

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = toUint8Array(account.address)
            return {
                address: base58.encode(publicKey), 
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? this.#chains,
                // TODO: get supported features from getCapabilities API 
                features: account.features ?? DEFAULT_FEATURES
            } as WalletAccount
        });
    }

    #performSignTransactions = async(
        transactions: Uint8Array[]
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            const base64Transactions = transactions.map((tx) => { return fromUint8Array(tx) });
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedTransactions = (await wallet.signTransactions({
                    payloads: base64Transactions,
                })).signed_payloads.map(toUint8Array);
                return signedTransactions;
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #performSignAndSendTransaction = async (
        transaction: Uint8Array,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities, _1] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken, chain)
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const base64Transaction = fromUint8Array(transaction);
                    const signatures = (await wallet.signAndSendTransactions({
                        ...options,
                        payloads: [base64Transaction],
                    })).signatures.map(toUint8Array);
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction')
                }
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {

        const outputs: SolanaSignAndSendTransactionOutput[] = [];

        for (const input of inputs) {
            const signature = await this.#performSignAndSendTransaction(input.transaction, input.options)
            outputs.push({ signature })
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        return (await this.#performSignTransactions(inputs.map(({ transaction }) => transaction)))
            .map((signedTransaction) => {
                return { signedTransaction }
            });
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => fromUint8Array(account.publicKey))
        const messages = inputs.map(({ message }) => fromUint8Array(message));
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedMessages = (await wallet.signMessages({
                    addresses: addresses,
                    payloads: messages,
                })).signed_payloads.map(toUint8Array);
                return signedMessages.map((signedMessage) => { 
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) }
                });
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
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
                domain: input?.domain ?? window.location.host
            });
            if (!authorizationResult.sign_in_result) {
                throw new Error("Sign in failed, no sign in result returned by wallet");
            }
            const signedInAddress = authorizationResult.sign_in_result.address;
            const signedInAccount = authorizationResult.accounts.find(acc => acc.address == signedInAddress);
            return {
                account: {
                    ...signedInAccount ?? {
                        address: base58.encode(toUint8Array(signedInAddress))
                    },
                    publicKey: toUint8Array(signedInAddress),
                    chains: signedInAccount?.chains ?? this.#chains,
                    features: signedInAccount?.features ?? authorizationResult.capabilities.features
                },
                signedMessage: toUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: toUint8Array(authorizationResult.sign_in_result.signature)
            };
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        } finally {
            this.#connecting = false;
        }
    }
}

export class RemoteSolanaMobileWalletAdapterWallet implements SolanaMobileWalletAdapterWallet, SolanaMobileWalletAdapterAuthorization {
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
    #hostAuthority: string;
    #session: { close: () => void, wallet: MobileWallet } | undefined;

    get version() {
        return this.#version;
    }

    get name() {
        return this.#name;
    }

    get url() {
        return this.#url
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
        return this.#authorization?.accounts as WalletAccount[] ?? [];
    }

    constructor(config: {
        appIdentity: AppIdentity;
        authorizationCache: AuthorizationCache;
        chains: IdentifierArray;
        chainSelector: ChainSelector;
        remoteHostAuthority: string;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    }) {
        this.#authorizationCache = config.authorizationCache;
        this.#appIdentity = config.appIdentity;
        this.#chains = config.chains;
        this.#chainSelector = config.chainSelector;
        this.#hostAuthority = config.remoteHostAuthority;
        this.#onWalletNotFound = config.onWalletNotFound;
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
        }
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

    #connect: StandardConnectMethod = async ({ silent } = {}) => {
        if (this.#connecting || this.connected) {
            return { accounts: this.accounts };
        }
        this.#connecting = true;
        try {
            await this.#performAuthorization();
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    }

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            const cachedAuthorizationResult = await this.#authorizationCache.get();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            if (this.#session) this.#session = undefined;
            const selectedChain = await this.#chainSelector.select(this.#chains);
            return await this.#transact(async (wallet) => {
                const [capabilities, mwaAuthorizationResult] = await Promise.all([
                    wallet.getCapabilities(),
                    wallet.authorize({
                        chain: selectedChain,
                        identity: this.#appIdentity,
                        sign_in_payload: signInPayload,
                    })
                ]);

                const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
                const authorizationResult = { ...mwaAuthorizationResult, 
                    accounts, chain: selectedChain, capabilities: capabilities };
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                Promise.all([
                    this.#handleWalletCapabilitiesResult(capabilities),
                    this.#authorizationCache.set(authorizationResult),
                    this.#handleAuthorizationResult(authorizationResult),
                ]);

                return authorizationResult;
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #handleAuthorizationResult = async (authorization: Authorization) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorization == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorization?.accounts.length !== authorization.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorization.accounts.some(
                (account, ii) => account.address !== authorization.accounts[ii].address,
            );
        this.#authorization = authorization;
        if (didPublicKeysChange) {
            this.#emit('change',{ accounts: this.accounts });
        }
    }

    #handleWalletCapabilitiesResult = async (
        capabilities: Awaited<ReturnType<GetCapabilitiesAPI['getCapabilities']>>
    ) => {
        // TODO: investigate why using SolanaSignTransactions constant breaks treeshaking
        const supportsSignTransaction = capabilities.features.includes('solana:signTransactions');//SolanaSignTransactions);
        const supportsSignAndSendTransaction = capabilities.supports_sign_and_send_transactions || 
            capabilities.features.includes('solana:signAndSendTransaction');
        const didCapabilitiesChange = 
            SolanaSignAndSendTransaction in this.features !== supportsSignAndSendTransaction ||
            SolanaSignTransaction in this.features !== supportsSignTransaction;
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
    }

    #performReauthorization = async (wallet: MobileWallet, authToken: AuthToken, chain: IdentifierString) => {
        try {
            const [capabilities, mwaAuthorizationResult] = await Promise.all([
                this.#authorization?.capabilities ?? await wallet.getCapabilities(),
                wallet.authorize({
                    auth_token: authToken,
                    identity: this.#appIdentity,
                    chain: chain
                })
            ]);
            
            const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
            const authorization = { ...mwaAuthorizationResult, 
                accounts: accounts, chain: chain, capabilities: capabilities };
            // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            Promise.all([
                this.#authorizationCache.set(authorization),
                this.#handleAuthorizationResult(authorization),
            ]);
        } catch (e) {
            this.#disconnect();
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #disconnect: StandardDisconnectMethod = async () => {
        this.#session?.close();
        this.#authorizationCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        this.#authorization = undefined;
        this.#session = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(callback: (wallet: MobileWallet) => TReturn) => {
        const walletUriBase = this.#authorization?.wallet_uri_base;
        const baseConfig = walletUriBase ? { baseUri: walletUriBase } : undefined;
        const remoteConfig = { ...baseConfig, remoteHostAuthority: this.#hostAuthority };
        const currentConnectionGeneration = this.#connectionGeneration;
        const modal = new RemoteConnectionModal();

        if (this.#session) {
            return callback(this.#session.wallet);
        }
        
        try {
            const { associationUrl, close, wallet } = await startRemoteScenario(remoteConfig);
            const removeCloseListener = modal.addEventListener('close', (event: any) => {
                if (event) close();
            });
            modal.initWithQR(associationUrl.toString());
            modal.open();
            this.#session = { close, wallet: await wallet };
            removeCloseListener();
            modal.close();
            return await callback(this.#session.wallet);
        } catch (e) {
            modal.close();
            if (this.#connectionGeneration !== currentConnectionGeneration) {
                await new Promise(() => {}); // Never resolve.
            }
            if (
                e instanceof Error &&
                e.name === 'SolanaMobileWalletAdapterError' &&
                (
                    e as SolanaMobileWalletAdapterError<
                        typeof SolanaMobileWalletAdapterErrorCode[keyof typeof SolanaMobileWalletAdapterErrorCode]
                    >
                ).code === 'ERROR_WALLET_NOT_FOUND'
            ) {
                await this.#onWalletNotFound(this);
            }
            throw e;
        }
    }

    #assertIsAuthorized = () => {
        if (!this.#authorization) throw new Error('Wallet not connected');
        return { authToken: this.#authorization.auth_token, chain: this.#authorization.chain };
    }

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = toUint8Array(account.address)
            return {
                address: base58.encode(publicKey), 
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? this.#chains,
                // TODO: get supported features from getCapabilities API 
                features: account.features ?? DEFAULT_FEATURES
            } as WalletAccount
        });
    }

    #performSignTransactions = async(
        transactions: Uint8Array[]
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedTransactions = (await wallet.signTransactions({
                    payloads: transactions.map(fromUint8Array),
                })).signed_payloads.map(toUint8Array);
                return signedTransactions;
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #performSignAndSendTransaction = async (
        transaction: Uint8Array,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities, _1] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken, chain)
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const signatures = (await wallet.signAndSendTransactions({
                        ...options,
                        payloads: [fromUint8Array(transaction)],
                    })).signatures.map(toUint8Array);
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction')
                }
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        }
    }

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {
        const outputs: SolanaSignAndSendTransactionOutput[] = [];
        for (const input of inputs) {
            const signature = (await this.#performSignAndSendTransaction(input.transaction, input.options))
            outputs.push({ signature })
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        return (await this.#performSignTransactions(inputs.map(({ transaction }) => transaction)))
            .map((signedTransaction) => {
                return { signedTransaction }
            });
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const { authToken, chain } = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => fromUint8Array(account.publicKey))
        const messages = inputs.map(({ message }) => fromUint8Array(message));
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken, chain);
                const signedMessages = (await wallet.signMessages({
                    addresses: addresses,
                    payloads: messages,
                })).signed_payloads.map(toUint8Array);
                return signedMessages.map((signedMessage) => { 
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) }
                });
            });
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
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
                domain: input?.domain ?? window.location.host
            });
            if (!authorizationResult.sign_in_result) {
                throw new Error("Sign in failed, no sign in result returned by wallet");
            }
            const signedInAddress = authorizationResult.sign_in_result.address;
            const signedInAccount = authorizationResult.accounts.find(acc => acc.address == signedInAddress);
            return {
                account: {
                    ...signedInAccount ?? {
                        address: base58.encode(toUint8Array(signedInAddress))
                    },
                    publicKey: toUint8Array(signedInAddress),
                    chains: signedInAccount?.chains ?? this.#chains,
                    features: signedInAccount?.features ?? authorizationResult.capabilities.features
                },
                signedMessage: toUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: toUint8Array(authorizationResult.sign_in_result.signature)
            };
        } catch (e) {
            throw new Error((e instanceof Error && e.message) || 'Unknown error');
        } finally {
            this.#connecting = false;
        }
    }
}
