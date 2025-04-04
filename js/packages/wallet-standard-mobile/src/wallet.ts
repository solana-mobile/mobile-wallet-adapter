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
    type SolanaSignMessageOutput,
    SolanaSignTransaction,
    type SolanaSignTransactionFeature,
    type SolanaSignTransactionMethod,
} from '@solana/wallet-standard-features';
import { 
    Transaction as LegacyTransaction,
    VersionedTransaction
} from '@solana/web3.js';
import RemoteConnectionModal from './embedded-modal/remoteConnectionModal.js';
import {
    Account,
    AppIdentity,
    AuthorizationResult,
    AuthToken,
    Base64EncodedAddress,
    SignInPayload,
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import type { IdentifierString, Wallet, WalletAccount } from '@wallet-standard/base';
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
import { isVersionedTransaction, MWA_SOLANA_CHAINS } from './solana';
import { transact, startRemoteScenario, Web3MobileWallet } from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import { fromUint8Array, toUint8Array } from './base64Utils';
import { 
    WalletConnectionError,
    WalletDisconnectedError,
    WalletNotConnectedError,
    WalletSendTransactionError,
    WalletSignMessageError,
    WalletSignTransactionError
} from './errors';
import base58 from 'bs58';

export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | undefined>;
    set(authorizationResult: AuthorizationResult): Promise<void>;
}

export interface AddressSelector {
    select(addresses: Base64EncodedAddress[]): Promise<Base64EncodedAddress>;
}

export const SolanaMobileWalletAdapterWalletName = 'Mobile Wallet Adapter';

const SIGNATURE_LENGTH_IN_BYTES = 64;
const DEFAULT_FEATURES = [SolanaSignAndSendTransaction, SolanaSignTransaction, SolanaSignMessage, SolanaSignIn] as const;

export interface SolanaMobileWalletAdapterWallet extends Wallet {
    url: string
}

export class LocalSolanaMobileWalletAdapterWallet implements SolanaMobileWalletAdapterWallet {
    readonly #listeners: { [E in StandardEventsNames]?: StandardEventsListeners[E][] } = {};
    readonly #version = '1.0.0' as const; // wallet-standard version
    readonly #name = SolanaMobileWalletAdapterWalletName;
    readonly #url = 'https://solanamobile.com/wallets';
    readonly #icon = icon;

    // #accounts: [MobileWalletAccount] | [] = [];

    #appIdentity: AppIdentity;
    #authorizationResult: AuthorizationResult | undefined;
    #authorizationResultCache: AuthorizationResultCache;
    #connecting = false;
    /**
     * Every time the connection is recycled in some way (eg. `disconnect()` is called)
     * increment this and use it to make sure that `transact` calls from the previous
     * 'generation' don't continue to do work and throw exceptions.
     */
    #connectionGeneration = 0;
    #chain: IdentifierString;
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
        return MWA_SOLANA_CHAINS.slice();
    }

    get features(): StandardConnectFeature &
        StandardDisconnectFeature &
        StandardEventsFeature &
        SolanaSignAndSendTransactionFeature &
        SolanaSignTransactionFeature &
        SolanaSignMessageFeature &
        SolanaSignInFeature {
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
            // TODO: signAndSendTransaction was an optional feature in MWA 1.x.
            //  this should be omitted here and only added after confirming wallet support via getCapabilities
            //  Note: dont forget to emit the StandardEvent 'change' for the updated feature list 
            [SolanaSignAndSendTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signAndSendTransaction: this.#signAndSendTransaction,
            },
            // TODO: signTransaction is an optional, deprecated feature in MWA 2.0.
            //  this should be omitted here and only added after confirming wallet support via getCapabilities
            //  Note: dont forget to emit the StandardEvent 'change' for the updated feature list 
            [SolanaSignTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signTransaction: this.#signTransaction,
            },
            [SolanaSignMessage]: {
                version: '1.0.0',
                signMessage: this.#signMessage,
            },
            [SolanaSignIn]: {
                version: '1.0.0',
                signIn: this.#signIn,
            },
        };
    }
    
    get accounts() {
        return this.#authorizationResult?.accounts as WalletAccount[] ?? [];
    }

    constructor(config: {
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: IdentifierString;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    }) {
        this.#authorizationResultCache = config.authorizationResultCache;
        this.#appIdentity = config.appIdentity;
        this.#chain = config.chain;
        this.#onWalletNotFound = config.onWalletNotFound;
    }

    get connected(): boolean {
        return !!this.#authorizationResult;
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
                const cachedAuthorizationResult = await this.#authorizationResultCache.get();
                if (cachedAuthorizationResult) {
                    await this.#handleAuthorizationResult(cachedAuthorizationResult);
                }
            } else {
                await this.#performAuthorization();
            }
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    }

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            const cachedAuthorizationResult = await this.#authorizationResultCache.get();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            return await this.#transact(async (wallet) => {
                const mwaAuthorizationResult = await wallet.authorize({
                    chain: this.#chain,
                    identity: this.#appIdentity,
                    sign_in_payload: signInPayload,
                });

                const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
                const authorizationResult = { ...mwaAuthorizationResult, accounts};
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                Promise.all([
                    this.#authorizationResultCache.set(authorizationResult),
                    this.#handleAuthorizationResult(authorizationResult),
                ]);

                return authorizationResult;
            });
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        }
    }

    #handleAuthorizationResult = async (authorizationResult: AuthorizationResult) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorizationResult == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorizationResult?.accounts.length !== authorizationResult.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorizationResult.accounts.some(
                (account, ii) => account.address !== authorizationResult.accounts[ii].address,
            );
        this.#authorizationResult = authorizationResult;
        if (didPublicKeysChange) {
            this.#emit('change',{ accounts: this.accounts });
        }
    }

    #performReauthorization = async (wallet: Web3MobileWallet, authToken: AuthToken) => {
        try {
            const mwaAuthorizationResult = await wallet.authorize({
                auth_token: authToken,
                identity: this.#appIdentity,
            });
            
            const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
            const authorizationResult = { ...mwaAuthorizationResult, 
                accounts: accounts
            };
            // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            Promise.all([
                this.#authorizationResultCache.set(authorizationResult),
                this.#handleAuthorizationResult(authorizationResult),
            ]);
        } catch (e) {
            this.#disconnect();
            throw new WalletDisconnectedError((e instanceof Error && e?.message) || 'Unknown error', e);
        }
    }

    #disconnect: StandardDisconnectMethod = async () => {
        this.#authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        this.#authorizationResult = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(callback: (wallet: Web3MobileWallet) => TReturn) => {
        const walletUriBase = this.#authorizationResult?.wallet_uri_base;
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
        if (!this.#authorizationResult) throw new WalletNotConnectedError();
        return this.#authorizationResult.auth_token;
    }

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = toUint8Array(account.address)
            return {
                address: base58.encode(publicKey), 
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? [this.#chain],
                // TODO: get supported features from getCapabilities API 
                features: account.features ?? DEFAULT_FEATURES
            } as WalletAccount
        });
    }

    #performSignTransactions = async <T extends LegacyTransaction | VersionedTransaction>(
        transactions: T[]
    ) => {
        const authToken = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken);
                const signedTransactions = await wallet.signTransactions({
                    transactions,
                });
                return signedTransactions;
            });
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    #performSignAndSendTransaction = async (
        transaction: VersionedTransaction,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const authToken = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities, _1] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken)
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const signatures = await wallet.signAndSendTransactions({
                        ...options,
                        transactions: [transaction],
                    });
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction')
                }
            });
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message, error);
        }
    }

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {

        const outputs: SolanaSignAndSendTransactionOutput[] = [];

        for (const input of inputs) {
            const transaction = VersionedTransaction.deserialize(input.transaction);
            const signature = (await this.#performSignAndSendTransaction(transaction, input.options))
            outputs.push({ signature: base58.decode(signature) })
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        const transactions = inputs.map(({ transaction }) => VersionedTransaction.deserialize(transaction));
        const signedTransactions = await this.#performSignTransactions(transactions);
        return signedTransactions.map((signedTransaction) => {
            const serializedTransaction = isVersionedTransaction(signedTransaction)
                ? signedTransaction.serialize()
                : new Uint8Array(
                        (signedTransaction as LegacyTransaction).serialize({
                            requireAllSignatures: false,
                            verifySignatures: false,
                        })
                    );

            return { signedTransaction: serializedTransaction };
        });
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const authToken = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => fromUint8Array(account.publicKey))
        const messages = inputs.map(({ message }) => message);
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken);
                const signedMessages = await wallet.signMessages({
                    addresses: addresses,
                    payloads: messages,
                });
                return signedMessages.map((signedMessage) => { 
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) }
                });
            });
        } catch (error: any) {
            throw new WalletSignMessageError(error?.message, error);
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
            const signedInAccount: WalletAccount = {
                ...authorizationResult.accounts.find(acc => acc.address == signedInAddress) ?? {
                    address: signedInAddress
                }, 
                publicKey: toUint8Array(signedInAddress)
            } as WalletAccount;
            return {
                account: signedInAccount,
                signedMessage: toUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: toUint8Array(authorizationResult.sign_in_result.signature)
            };
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        } finally {
            this.#connecting = false;
        }
    }
}

export class RemoteSolanaMobileWalletAdapterWallet implements SolanaMobileWalletAdapterWallet {
    readonly #listeners: { [E in StandardEventsNames]?: StandardEventsListeners[E][] } = {};
    readonly #version = '1.0.0' as const; // wallet-standard version
    readonly #name = SolanaMobileWalletAdapterWalletName;
    readonly #url = 'https://solanamobile.com/wallets';
    readonly #icon = icon;

    // #accounts: [MobileWalletAccount] | [] = [];

    #appIdentity: AppIdentity;
    #authorizationResult: AuthorizationResult | undefined;
    #authorizationResultCache: AuthorizationResultCache;
    #connecting = false;
    /**
     * Every time the connection is recycled in some way (eg. `disconnect()` is called)
     * increment this and use it to make sure that `transact` calls from the previous
     * 'generation' don't continue to do work and throw exceptions.
     */
    #connectionGeneration = 0;
    #chain: IdentifierString;
    #onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    #hostAuthority: string;
    #session: { close: () => void, wallet: Web3MobileWallet } | undefined;

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
        return MWA_SOLANA_CHAINS.slice();
    }

    get features(): StandardConnectFeature &
        StandardDisconnectFeature &
        StandardEventsFeature &
        SolanaSignAndSendTransactionFeature &
        SolanaSignTransactionFeature &
        SolanaSignMessageFeature &
        SolanaSignInFeature {
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
            // TODO: signAndSendTransaction was an optional feature in MWA 1.x.
            //  this should be omitted here and only added after confirming wallet support via getCapabilities
            //  Note: dont forget to emit the StandardEvent 'change' for the updated feature list 
            [SolanaSignAndSendTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signAndSendTransaction: this.#signAndSendTransaction,
            },
            // TODO: signTransaction is an optional, deprecated feature in MWA 2.0.
            //  this should be omitted here and only added after confirming wallet support via getCapabilities
            //  Note: dont forget to emit the StandardEvent 'change' for the updated feature list 
            [SolanaSignTransaction]: {
                version: '1.0.0',
                supportedTransactionVersions: ['legacy', 0],
                signTransaction: this.#signTransaction,
            },
            [SolanaSignMessage]: {
                version: '1.0.0',
                signMessage: this.#signMessage,
            },
            [SolanaSignIn]: {
                version: '1.0.0',
                signIn: this.#signIn,
            },
        };
    }
    
    get accounts() {
        return this.#authorizationResult?.accounts as WalletAccount[] ?? [];
    }

    constructor(config: {
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: IdentifierString;
        remoteHostAuthority: string;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterWallet) => Promise<void>;
    }) {
        this.#authorizationResultCache = config.authorizationResultCache;
        this.#appIdentity = config.appIdentity;
        this.#chain = config.chain;
        this.#hostAuthority = config.remoteHostAuthority;
        this.#onWalletNotFound = config.onWalletNotFound;
    }

    get connected(): boolean {
        return !!this.#session && !!this.#authorizationResult;
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
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        } finally {
            this.#connecting = false;
        }

        return { accounts: this.accounts };
    }

    #performAuthorization = async (signInPayload?: SignInPayload) => {
        try {
            const cachedAuthorizationResult = await this.#authorizationResultCache.get();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.#handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            if (this.#session) this.#session = undefined;
            return await this.#transact(async (wallet) => {
                const mwaAuthorizationResult = await wallet.authorize({
                    chain: this.#chain,
                    identity: this.#appIdentity,
                    sign_in_payload: signInPayload,
                });

                const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
                const authorizationResult = { ...mwaAuthorizationResult, accounts};
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                Promise.all([
                    this.#authorizationResultCache.set(authorizationResult),
                    this.#handleAuthorizationResult(authorizationResult),
                ]);

                return authorizationResult;
            });
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        }
    }

    #handleAuthorizationResult = async (authorizationResult: AuthorizationResult) => {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this.#authorizationResult == null ||
            // Case 2: The number of authorized accounts changed.
            this.#authorizationResult?.accounts.length !== authorizationResult.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this.#authorizationResult.accounts.some(
                (account, ii) => account.address !== authorizationResult.accounts[ii].address,
            );
        this.#authorizationResult = authorizationResult;
        if (didPublicKeysChange) {
            this.#emit('change',{ accounts: this.accounts });
        }
    }

    #performReauthorization = async (wallet: Web3MobileWallet, authToken: AuthToken) => {
        try {
            const mwaAuthorizationResult = await wallet.authorize({
                auth_token: authToken,
                identity: this.#appIdentity,
            });
            
            const accounts = this.#accountsToWalletStandardAccounts(mwaAuthorizationResult.accounts)
            const authorizationResult = { ...mwaAuthorizationResult, 
                accounts: accounts
            };
            // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            Promise.all([
                this.#authorizationResultCache.set(authorizationResult),
                this.#handleAuthorizationResult(authorizationResult),
            ]);
        } catch (e) {
            this.#disconnect();
            throw new WalletDisconnectedError((e instanceof Error && e?.message) || 'Unknown error', e);
        }
    }

    #disconnect: StandardDisconnectMethod = async () => {
        this.#session?.close();
        this.#authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this.#connecting = false;
        this.#connectionGeneration++;
        this.#authorizationResult = undefined;
        this.#session = undefined;
        this.#emit('change', { accounts: this.accounts });
    };

    #transact = async <TReturn>(callback: (wallet: Web3MobileWallet) => TReturn) => {
        const walletUriBase = this.#authorizationResult?.wallet_uri_base;
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
        if (!this.#authorizationResult) throw new WalletNotConnectedError();
        return this.#authorizationResult.auth_token;
    }

    #accountsToWalletStandardAccounts = (accounts: Account[]) => {
        return accounts.map((account) => {
            const publicKey = toUint8Array(account.address)
            return {
                address: base58.encode(publicKey), 
                publicKey,
                label: account.label,
                icon: account.icon,
                chains: account.chains ?? [this.#chain],
                // TODO: get supported features from getCapabilities API 
                features: account.features ?? DEFAULT_FEATURES
            } as WalletAccount
        });
    }

    #performSignTransactions = async <T extends LegacyTransaction | VersionedTransaction>(
        transactions: T[]
    ) => {
        const authToken = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken);
                const signedTransactions = await wallet.signTransactions({
                    transactions,
                });
                return signedTransactions;
            });
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    #performSignAndSendTransaction = async (
        transaction: VersionedTransaction,
        options?: SolanaSignAndSendTransactionOptions | undefined,
    ) => {
        const authToken = this.#assertIsAuthorized();
        try {
            return await this.#transact(async (wallet) => {
                const [capabilities, _1] = await Promise.all([
                    wallet.getCapabilities(),
                    this.#performReauthorization(wallet, authToken)
                ]);
                if (capabilities.supports_sign_and_send_transactions) {
                    const signatures = await wallet.signAndSendTransactions({
                        ...options,
                        transactions: [transaction],
                    });
                    return signatures[0];
                } else {
                    throw new Error('connected wallet does not support signAndSendTransaction')
                }
            });
        } catch (error: any) {
            throw new WalletSendTransactionError(error?.message, error);
        }
    }

    #signAndSendTransaction: SolanaSignAndSendTransactionMethod = async (...inputs) => {
        const outputs: SolanaSignAndSendTransactionOutput[] = [];
        for (const input of inputs) {
            const transaction = VersionedTransaction.deserialize(input.transaction);
            const signature = (await this.#performSignAndSendTransaction(transaction, input.options))
            outputs.push({ signature: base58.decode(signature) })
        }

        return outputs;
    };

    #signTransaction: SolanaSignTransactionMethod = async (...inputs) => {
        const transactions = inputs.map(({ transaction }) => VersionedTransaction.deserialize(transaction));
        const signedTransactions = await this.#performSignTransactions(transactions);
        return signedTransactions.map((signedTransaction) => {
            const serializedTransaction = isVersionedTransaction(signedTransaction)
                ? signedTransaction.serialize()
                : new Uint8Array(
                        (signedTransaction as LegacyTransaction).serialize({
                            requireAllSignatures: false,
                            verifySignatures: false,
                        })
                    );

            return { signedTransaction: serializedTransaction };
        });
    };

    #signMessage: SolanaSignMessageMethod = async (...inputs) => {
        const authToken = this.#assertIsAuthorized();
        const addresses = inputs.map(({ account }) => fromUint8Array(account.publicKey))
        const messages = inputs.map(({ message }) => message);
        try {
            return await this.#transact(async (wallet) => {
                await this.#performReauthorization(wallet, authToken);
                const signedMessages = await wallet.signMessages({
                    addresses: addresses,
                    payloads: messages,
                });
                return signedMessages.map((signedMessage) => { 
                    return { signedMessage: signedMessage, signature: signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES) }
                });
            });
        } catch (error: any) {
            throw new WalletSignMessageError(error?.message, error);
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
            const signedInAccount: WalletAccount = {
                ...authorizationResult.accounts.find(acc => acc.address == signedInAddress) ?? {
                    address: signedInAddress
                }, 
                publicKey: toUint8Array(signedInAddress)
            } as WalletAccount;
            return {
                account: signedInAccount,
                signedMessage: toUint8Array(authorizationResult.sign_in_result.signed_message),
                signature: toUint8Array(authorizationResult.sign_in_result.signature)
            };
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        } finally {
            this.#connecting = false;
        }
    }
}

