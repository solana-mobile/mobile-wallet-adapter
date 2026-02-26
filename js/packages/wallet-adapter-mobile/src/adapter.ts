import {
    BaseSignInMessageSignerWalletAdapter,
    WalletConnectionError,
    WalletName,
    WalletNotConnectedError,
    WalletNotReadyError,
    WalletPublicKeyError,
    WalletReadyState,
    WalletSendTransactionError,
    WalletSignMessageError,
    WalletSignTransactionError,
} from '@solana/wallet-adapter-base';
import {
    Connection,
    PublicKey,
    SendOptions,
    Transaction as LegacyTransaction,
    TransactionSignature,
    TransactionVersion,
    VersionedTransaction,
    VersionedMessage,
    Transaction,
} from '@solana/web3.js';
import {
    AppIdentity,
    AuthorizationResult,
    Base64EncodedAddress,
    Finality,
    Chain,
    Cluster,
    SignInPayload,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {
    SolanaSignAndSendTransaction,
    SolanaSignIn,
    SolanaSignInInput,
    SolanaSignInOutput,
    SolanaSignMessage,
    SolanaSignTransaction,
    SolanaSignTransactionOutput,
} from '@solana/wallet-standard-features';
import {
    IdentifierString,
    StandardConnect,
    StandardDisconnect,
    StandardEvents,
    StandardEventsChangeProperties,
    WalletAccount,
} from '@wallet-standard/core';
import {
    Authorization,
    createDefaultChainSelector,
    LocalSolanaMobileWalletAdapterWallet,
    RemoteSolanaMobileWalletAdapterWallet,
    SolanaMobileWalletAdapterWalletName as WalletStandardWalletName,
    SolanaMobileWalletAdapterRemoteWalletName as WalletStandardRemoteWalletName,
} from '@solana-mobile/wallet-standard-mobile';
import { fromUint8Array } from './base64Utils.js';
import getIsSupported from './getIsSupported.js';

export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | Authorization | undefined>;
    set(authorizationResult: AuthorizationResult | Authorization): Promise<void>;
}

export interface AddressSelector {
    select(addresses: Base64EncodedAddress[]): Promise<Base64EncodedAddress>;
}

export const SolanaMobileWalletAdapterWalletName = WalletStandardWalletName as WalletName;
export const SolanaMobileWalletAdapterRemoteWalletName = WalletStandardRemoteWalletName as WalletName;

const SIGNATURE_LENGTH_IN_BYTES = 64;

function isVersionedTransaction(
    transaction: LegacyTransaction | VersionedTransaction,
): transaction is VersionedTransaction {
    return 'version' in transaction;
}

function chainOrClusterToChainId(chain: Cluster | Chain): IdentifierString {
    switch (chain) {
        case 'mainnet-beta':
            return 'solana:mainnet';
        case 'testnet':
            return 'solana:testnet';
        case 'devnet':
            return 'solana:devnet';
        default:
            return chain;
    }
}

abstract class BaseSolanaMobileWalletAdapter extends BaseSignInMessageSignerWalletAdapter {
    readonly supportedTransactionVersions: Set<TransactionVersion> = new Set(
        // FIXME(#244): We can't actually know what versions are supported until we know which wallet we're talking to.
        ['legacy', 0],
    );
    name; icon; url;
    #wallet: LocalSolanaMobileWalletAdapterWallet | RemoteSolanaMobileWalletAdapterWallet;
    #connecting = false;
    #readyState: WalletReadyState = getIsSupported() ? WalletReadyState.Loadable : WalletReadyState.Unsupported;
    #accountSelector: (accounts: readonly WalletAccount[]) => Promise<WalletAccount>;
    #selectedAccount: WalletAccount | undefined;
    #publicKey: PublicKey | undefined;

    #handleChangeEvent = async (properties: StandardEventsChangeProperties) => {
        if (properties.accounts && properties.accounts.length > 0) {
            this.#declareWalletAsInstalled();
            const nextSelectedAccount = await this.#accountSelector(properties.accounts);
            if (nextSelectedAccount !== this.#selectedAccount) {
                this.#selectedAccount = nextSelectedAccount;
                this.#publicKey = undefined;
                this.emit(
                    'connect',
                    // Having just set `this.#selectedAccount`, `this.publicKey` is definitely non-null
                    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                    this.publicKey!,
                );
            }
        }
    }

    protected constructor(wallet: LocalSolanaMobileWalletAdapterWallet | RemoteSolanaMobileWalletAdapterWallet, config: {
        addressSelector: AddressSelector;
        chain: Chain;
    }) {
        super();
        // this.#chain = chainOrClusterToChainId(config.chain);
        this.#accountSelector = async (accounts: readonly WalletAccount[]) => {
            const selectedBase64EncodedAddress = await config.addressSelector.select(accounts.map(({ publicKey }) => fromUint8Array(new Uint8Array(publicKey))));
            return accounts.find(({ publicKey }) => fromUint8Array(new Uint8Array(publicKey)) === selectedBase64EncodedAddress) ?? accounts[0];
        };
        this.#wallet = wallet
        this.#wallet.features[StandardEvents].on('change', this.#handleChangeEvent);
        this.name = this.#wallet.name as WalletName;
        this.icon = this.#wallet.icon;
        this.url = this.#wallet.url;
        // TODO: evaluate if this logic should be kept - it seems to create a nasty bug where 
        //  the wallet tries to auto connect on page load and gets blocked by the popup blocker
        // if (this.#readyState !== WalletReadyState.Unsupported) {
        //     config.authorizationResultCache.get().then((authorizationResult) => {
        //         if (authorizationResult) {
        //             // Having a prior authorization result is, right now, the best
        //             // indication that a mobile wallet is installed. There is no API
        //             // we can use to test for whether the association URI is supported.
        //             this.#declareWalletAsInstalled();
        //         }
        //     });
        // }
    }

    get publicKey(): PublicKey | null {
        if (!this.#publicKey && this.#selectedAccount) {
            try {
                this.#publicKey = new PublicKey(this.#selectedAccount.publicKey);
            } catch (e) {
                throw new WalletPublicKeyError((e instanceof Error && e?.message) || 'Unknown error', e);
            }
        }
        return this.#publicKey ?? null;
    }

    get connected(): boolean {
        return this.#wallet.connected;
    }

    get connecting(): boolean {
        return this.#connecting;
    }

    get readyState(): WalletReadyState {
        return this.#readyState;
    }

    /** @deprecated Use `autoConnect()` instead. */
    async autoConnect_DO_NOT_USE_OR_YOU_WILL_BE_FIRED(): Promise<void> {
        return await this.autoConnect();
    }
    
    async autoConnect(): Promise<void> {
        this.#connect(true);
    }

    async connect(): Promise<void> {
        this.#connect();
    }

    async #connect(autoConnect: boolean = false): Promise<void> {
        if (this.connecting || this.connected) {
            return;
        }
        return await this.#runWithGuard(async () => {
            if (this.#readyState !== WalletReadyState.Installed && this.#readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this.#connecting = true;
            try {
                await this.#wallet.features[StandardConnect].connect({ silent: autoConnect });
            } catch (e) {
                throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
            } finally {
                this.#connecting = false;
            }
        });
    }
    
    /** @deprecated Use `connect()` or `autoConnect()` instead. */
    async performAuthorization(signInPayload?: SignInPayload): Promise<AuthorizationResult> {
        try {
            const cachedAuthorizationResult = await this.#wallet.cachedAuthorizationResult;
            if (cachedAuthorizationResult) {
                await this.#wallet.features[StandardConnect].connect({ silent: true });
                return cachedAuthorizationResult;
            }

            if (signInPayload) {
                await this.#wallet.features[SolanaSignIn].signIn(signInPayload);
            } else await this.#wallet.features[StandardConnect].connect();
            const authorizationResult = await await this.#wallet.cachedAuthorizationResult;
            return authorizationResult!;
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        }
    }

    async disconnect(): Promise<void> {
        // return await this.#runWithGuard(this.#wallet.features[StandardDisconnect].disconnect);
        return await this.#runWithGuard(async () => {
            this.#connecting = false;
            this.#publicKey = undefined;
            this.#selectedAccount = undefined;
            await this.#wallet.features[StandardDisconnect].disconnect();
            this.emit('disconnect');
        })
    }

    async signIn(input?: SolanaSignInInput): Promise<SolanaSignInOutput> {
        return this.#runWithGuard(async () => {
            if (this.#readyState !== WalletReadyState.Installed && this.#readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this.#connecting = true;
            try {
                const outputs = await this.#wallet.features[SolanaSignIn].signIn({
                    ...input,
                    domain: input?.domain ?? window.location.host
                })
                if (outputs.length > 0) {
                    return outputs[0];
                } else {
                    throw new Error("Sign in failed, no sign in result returned by wallet");
                }
            } catch (e) {
                throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
            } finally {
                this.#connecting = false;
            }
        });
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        return await this.#runWithGuard(async () => {
            const account = this.#assertIsAuthorized();
            try {
                const outputs = await this.#wallet.features[SolanaSignMessage].signMessage({
                    account, message: message
                })
                return outputs[0].signature
            } catch (error: any) {
                throw new WalletSignMessageError(error?.message, error);
            }
        });
    }

    async sendTransaction<T extends LegacyTransaction | VersionedTransaction>(
        transaction: T,
        connection: Connection,
        options?: SendOptions,
    ): Promise<TransactionSignature> {
        return await this.#runWithGuard(async ()  => {
            const account = this.#assertIsAuthorized();
            try {
                function getTargetCommitment() {
                    let targetCommitment: Finality;
                    switch (connection.commitment) {
                        case 'confirmed':
                        case 'finalized':
                        case 'processed':
                            targetCommitment = connection.commitment;
                            break;
                        default:
                            targetCommitment = 'finalized';
                    }
                    let targetPreflightCommitment: Finality;
                    switch (options?.preflightCommitment) {
                        case 'confirmed':
                        case 'finalized':
                        case 'processed':
                            targetPreflightCommitment = options.preflightCommitment;
                            break;
                        case undefined:
                            targetPreflightCommitment = targetCommitment;
                            break;
                        default:
                            targetPreflightCommitment = 'finalized';
                    }
                    const preflightCommitmentScore =
                        targetPreflightCommitment === 'finalized'
                            ? 2
                            : targetPreflightCommitment === 'confirmed'
                            ? 1
                            : 0;
                    const targetCommitmentScore =
                        targetCommitment === 'finalized' ? 2 : targetCommitment === 'confirmed' ? 1 : 0;
                    return preflightCommitmentScore < targetCommitmentScore
                        ? targetPreflightCommitment
                        : targetCommitment;
                }
                if (SolanaSignAndSendTransaction in this.#wallet.features) {
                    const chain = chainOrClusterToChainId(this.#wallet.currentAuthorization!.chain);
                    const [signature] = (await this.#wallet.features[SolanaSignAndSendTransaction].signAndSendTransaction({
                        account,
                        transaction: transaction.serialize(),
                        chain: chain,
                        options: options ? {
                            skipPreflight: options.skipPreflight,
                            maxRetries: options.maxRetries
                        } : undefined
                    })).map(((output) => {
                        return fromUint8Array(output.signature)
                    }));
        
                    return signature;
                } else {
                    const [signedTransaction] = await this.#performSignTransactions([transaction])
                    if (isVersionedTransaction(signedTransaction)) {
                        return await connection.sendTransaction(signedTransaction);
                    } else {
                        const serializedTransaction = signedTransaction.serialize();
                        return await connection.sendRawTransaction(serializedTransaction, {
                            ...options,
                            preflightCommitment: getTargetCommitment(),
                        });
                    }
                }
            } catch (error: any) {
                throw new WalletSendTransactionError(error?.message, error);
            }
        });
    }

    async signTransaction<T extends LegacyTransaction | VersionedTransaction>(transaction: T): Promise<T> {
        return await this.#runWithGuard(async () => {
            const [signedTransaction] = await this.#performSignTransactions([transaction]);
            return signedTransaction;
        });
    }

    async signAllTransactions<T extends LegacyTransaction | VersionedTransaction>(transactions: T[]): Promise<T[]> {
        return await this.#runWithGuard(async () => {
            const signedTransactions = await this.#performSignTransactions(transactions);
            return signedTransactions;
        });
    }

    #declareWalletAsInstalled(): void {
        if (this.#readyState !== WalletReadyState.Installed) {
            this.emit('readyStateChange', (this.#readyState = WalletReadyState.Installed));
        }
    }

    #assertIsAuthorized() {
        if (!this.#wallet.isAuthorized || !this.#selectedAccount) throw new WalletNotConnectedError()
        return this.#selectedAccount;
    }
    
    async #performSignTransactions<T extends LegacyTransaction | VersionedTransaction>(
        transactions: T[],
    ): Promise<T[]> {
        const account = this.#assertIsAuthorized();
        try {
            if (SolanaSignTransaction in this.#wallet.features) {
                return this.#wallet.features[SolanaSignTransaction].signTransaction(
                    ...transactions.map((value) => {
                        return { account, transaction: value.serialize() }
                    }
                )).then((outputs) => {
                    return outputs.map((output: SolanaSignTransactionOutput) => {
                        const byteArray = output.signedTransaction;
                        const numSignatures = byteArray[0];
                        const messageOffset = numSignatures * SIGNATURE_LENGTH_IN_BYTES + 1;
                        const version = VersionedMessage.deserializeMessageVersion(byteArray.slice(messageOffset, byteArray.length));
                        if (version === 'legacy') {
                            return Transaction.from(byteArray) as T;
                        } else {
                            return VersionedTransaction.deserialize(byteArray) as T;
                        }
                    })
                })
            } else {
                throw new Error('Connected wallet does not support signing transactions');
            }
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    async #runWithGuard<TReturn>(callback: () => Promise<TReturn>) {
        try {
            return await callback();
        } catch (e: any) {
            this.emit('error', e);
            throw e;
        }
    }
}

export class LocalSolanaMobileWalletAdapter extends BaseSolanaMobileWalletAdapter {
    /**
     * @deprecated @param cluster config paramter is deprecated, use @param chain instead
     */
    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        cluster: Cluster;
        onWalletNotFound: (mobileWalletAdapter: LocalSolanaMobileWalletAdapter) => Promise<void>;
    });

    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: Chain;
        onWalletNotFound: (mobileWalletAdapter: LocalSolanaMobileWalletAdapter) => Promise<void>;
    });

    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: Chain;
        cluster: Cluster;
        onWalletNotFound: (mobileWalletAdapter: LocalSolanaMobileWalletAdapter) => Promise<void>;
    }) {
        const chain = chainOrClusterToChainId(config.chain ?? config.cluster);
        super(new LocalSolanaMobileWalletAdapterWallet({
            appIdentity: config.appIdentity,
            authorizationCache: {
                set: config.authorizationResultCache.set,
                get: async () => {
                    return await config.authorizationResultCache.get() as Authorization | undefined;
                },
                clear: config.authorizationResultCache.clear,
            },
            chains: [chain],
            chainSelector: createDefaultChainSelector(),
            onWalletNotFound: async () => {
                config.onWalletNotFound(this)
            },
        }), {
            addressSelector: config.addressSelector,
            chain: chain,
        });
    }
}

export class RemoteSolanaMobileWalletAdapter extends BaseSolanaMobileWalletAdapter {
    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: Chain;
        remoteHostAuthority: string;
        onWalletNotFound: (mobileWalletAdapter: RemoteSolanaMobileWalletAdapter) => Promise<void>;
    }) {
        const chain = chainOrClusterToChainId(config.chain);
        super(new RemoteSolanaMobileWalletAdapterWallet({
            appIdentity: config.appIdentity,
            authorizationCache: {
                set: config.authorizationResultCache.set,
                get: async () => {
                    return await config.authorizationResultCache.get() as Authorization | undefined;
                },
                clear: config.authorizationResultCache.clear,
            },
            chains: [chain],
            chainSelector: createDefaultChainSelector(),
            remoteHostAuthority: config.remoteHostAuthority,
            onWalletNotFound: async () => {
                config.onWalletNotFound(this)
            },
        }), {
            addressSelector: config.addressSelector,
            chain: chain,
        });
    }
}

export class SolanaMobileWalletAdapter extends LocalSolanaMobileWalletAdapter {}