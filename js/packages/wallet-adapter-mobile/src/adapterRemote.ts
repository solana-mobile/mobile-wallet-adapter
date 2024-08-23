import {
    BaseSignInMessageSignerWalletAdapter,
    isVersionedTransaction,
    WalletConnectionError,
    WalletDisconnectedError,
    WalletName,
    WalletNotConnectedError,
    WalletNotReadyError,
    WalletPublicKeyError,
    WalletReadyState,
    WalletSendTransactionError,
    WalletSignMessageError,
    WalletSignTransactionError,
} from '@solana/wallet-adapter-base';
import { type SolanaSignInInput, type SolanaSignInOutput } from '@solana/wallet-standard-features';
import { 
    PublicKey, 
    Transaction as LegacyTransaction,
    TransactionVersion, 
    VersionedTransaction,
    Connection,
    SendOptions,
    TransactionSignature,
} from '@solana/web3.js';
import EmbeddedDialogModal from './embedded-modal/modal.js';
import { 
    AppIdentity, 
    AuthorizationResult, 
    AuthToken, 
    Base64EncodedAddress, 
    Finality,
    Chain,
    SignInPayload,
    SolanaMobileWalletAdapterError,
    SolanaMobileWalletAdapterErrorCode,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import { toUint8Array } from 'js-base64';
import { AddressSelector, AuthorizationResultCache } from './adapter.js';
import type { WalletAccount } from '@wallet-standard/core';
import { transactRemote, Web3MobileWallet, Web3RemoteMobileWallet } from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';

export const SolanaMobileWalletAdapterRemoteWalletName = 'MWA (Remote)' as WalletName<'MWA (Remote)'>;

const SIGNATURE_LENGTH_IN_BYTES = 64;

function getPublicKeyFromAddress(address: Base64EncodedAddress): PublicKey {
    const publicKeyByteArray = toUint8Array(address);
    return new PublicKey(publicKeyByteArray);
}

/**
 * Determine the mobile operating system.
 * Returns true if running on a mobile operating system, or false otherwise.
 *
 * @returns {boolean}
 */
function isMobileOperatingSystem() {
    var userAgent = navigator.userAgent;

    // Windows Phone must come first because its UA also contains "Android"
    if (/windows phone/i.test(userAgent)) {
        return true;
    }

    if (/android/i.test(userAgent)) {
        return true;
    }

    // iOS detection from: http://stackoverflow.com/a/9039885/177710
    if (/iPad|iPhone|iPod/.test(userAgent) && !(window as any).MSStream) {
        return true;
    }

    return false;
}

function getIsSupported() {
    return (
        typeof window !== 'undefined' &&
        window.isSecureContext &&
        typeof document !== 'undefined' &&
        !isMobileOperatingSystem()
    );
}

/**
 * This burner wallet adapter is unsafe to use and is only included to provide an easy way for applications to test
 * Wallet Adapter without using a third-party wallet.
 */
export class SolanaMobileWalletAdapterRemote extends BaseSignInMessageSignerWalletAdapter {
    readonly supportedTransactionVersions: Set<TransactionVersion> = new Set(
        // FIXME(#244): We can't actually know what versions are supported until we know which wallet we're talking to.
        ['legacy', 0],
    );
    name = SolanaMobileWalletAdapterRemoteWalletName;
    url = 'https://solanamobile.com/wallets';
    icon =
        'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI4IiB3aWR0aD0iMjgiIHZpZXdCb3g9Ii0zIDAgMjggMjgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGcgZmlsbD0iI0RDQjhGRiI+PHBhdGggZD0iTTE3LjQgMTcuNEgxNXYyLjRoMi40di0yLjRabTEuMi05LjZoLTIuNHYyLjRoMi40VjcuOFoiLz48cGF0aCBkPSJNMjEuNiAzVjBoLTIuNHYzaC0zLjZWMGgtMi40djNoLTIuNHY2LjZINC41YTIuMSAyLjEgMCAxIDEgMC00LjJoMi43VjNINC41QTQuNSA0LjUgMCAwIDAgMCA3LjVWMjRoMjEuNnYtNi42aC0yLjR2NC4ySDIuNFYxMS41Yy41LjMgMS4yLjQgMS44LjVoNy41QTYuNiA2LjYgMCAwIDAgMjQgOVYzaC0yLjRabTAgNS43YTQuMiA0LjIgMCAxIDEtOC40IDBWNS40aDguNHYzLjNaIi8+PC9nPjwvc3ZnPg==';
    
    private _addressSelector: AddressSelector;
    private _appIdentity: AppIdentity;
    private _authorizationResult: AuthorizationResult | undefined;
    private _authorizationResultCache: AuthorizationResultCache;
    private _connecting = false;
    /**
     * Every time the connection is recycled in some way (eg. `disconnect()` is called)
     * increment this and use it to make sure that `transact` calls from the previous
     * 'generation' don't continue to do work and throw exceptions.
     */
    private _connectionGeneration = 0;
    private _chain: Chain;
    private _onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterRemote) => Promise<void>;
    private _publicKey: PublicKey | undefined;
    private _readyState: WalletReadyState = getIsSupported() ? WalletReadyState.Loadable : WalletReadyState.Unsupported;
    private _selectedAddress: Base64EncodedAddress | undefined;
    private _hostAuthority: string;
    private _wallet: Web3RemoteMobileWallet | undefined;

    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        chain: Chain;
        remoteHostAuthority: string;
        onWalletNotFound: (mobileWalletAdapter: SolanaMobileWalletAdapterRemote) => Promise<void>;
    }) {
        super();
        this._authorizationResultCache = config.authorizationResultCache;
        this._addressSelector = config.addressSelector;
        this._appIdentity = config.appIdentity;
        this._chain = config.chain;
        this._hostAuthority = config.remoteHostAuthority;
        this._onWalletNotFound = config.onWalletNotFound;
        if (this._readyState !== WalletReadyState.Unsupported) {
            this._authorizationResultCache.get().then((authorizationResult) => {
                if (authorizationResult) {
                    // Having a prior authorization result is, right now, the best
                    // indication that a mobile wallet is installed. There is no API
                    // we can use to test for whether the association URI is supported.
                    this.declareWalletAsInstalled();
                }
            });
        }
    }

    get publicKey(): PublicKey | null {
        if (this._publicKey == null && this._selectedAddress != null) {
            try {
                this._publicKey = getPublicKeyFromAddress(this._selectedAddress);
            } catch (e) {
                throw new WalletPublicKeyError((e instanceof Error && e?.message) || 'Unknown error', e);
            }
        }
        return this._publicKey ? this._publicKey : null;
    }

    get connected(): boolean {
        return !!this._authorizationResult;
    }

    get connecting(): boolean {
        return this._connecting;
    }

    get readyState(): WalletReadyState {
        return this._readyState;
    }

    private declareWalletAsInstalled(): void {
        if (this._readyState !== WalletReadyState.Installed) {
            this.emit('readyStateChange', (this._readyState = WalletReadyState.Installed));
        }
    }

    private async runWithGuard<TReturn>(callback: () => Promise<TReturn>) {
        try {
            return await callback();
        } catch (e: any) {
            this.emit('error', e);
            throw e;
        }
    }

    async autoConnect(): Promise<void> {
        if (this.connecting || this.connected) {
            return;
        }
        return await this.runWithGuard(async () => {
            if (this._readyState !== WalletReadyState.Installed && this._readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this._connecting = true;
            try {
                const cachedAuthorizationResult = await this._authorizationResultCache.get();
                if (cachedAuthorizationResult) {
                    // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                    this.handleAuthorizationResult(cachedAuthorizationResult);
                }
            } catch (e) {
                throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
            } finally {
                this._connecting = false;
            }
        });
    }

    async connect(): Promise<void> {
        if (this.connecting || this.connected) {
            return;
        }
        return await this.runWithGuard(async () => {
            if (this._readyState !== WalletReadyState.Installed && this._readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this._connecting = true;
            try {
                await this.performAuthorization();
            } catch (e) {
                throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
            } finally {
                this._connecting = false;
            }
        });
    }

    async performAuthorization(signInPayload?: SignInPayload): Promise<AuthorizationResult> {
        try {
            const cachedAuthorizationResult = await this._authorizationResultCache.get();
            if (cachedAuthorizationResult) {
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.handleAuthorizationResult(cachedAuthorizationResult);
                return cachedAuthorizationResult;
            }
            if (this._wallet) delete this._wallet;
            return await this.transact(async (wallet) => {
                this._wallet = wallet;
                const authorizationResult = await wallet.authorize({
                    chain: this._chain,
                    identity: this._appIdentity,
                    sign_in_payload: signInPayload,
                });
                // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                Promise.all([
                    this._authorizationResultCache.set(authorizationResult),
                    this.handleAuthorizationResult(authorizationResult),
                ]);

                return authorizationResult;
            });
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        }
    }

    private async handleAuthorizationResult(authorizationResult: AuthorizationResult): Promise<void> {
        const didPublicKeysChange =
            // Case 1: We started from having no authorization.
            this._authorizationResult == null ||
            // Case 2: The number of authorized accounts changed.
            this._authorizationResult?.accounts.length !== authorizationResult.accounts.length ||
            // Case 3: The new list of addresses isn't exactly the same as the old list, in the same order.
            this._authorizationResult.accounts.some(
                (account, ii) => account.address !== authorizationResult.accounts[ii].address,
            );
        this._authorizationResult = authorizationResult;
        this.declareWalletAsInstalled();
        if (didPublicKeysChange) {
            const nextSelectedAddress = await this._addressSelector.select(
                authorizationResult.accounts.map(({ address }) => address),
            );
            if (nextSelectedAddress !== this._selectedAddress) {
                this._selectedAddress = nextSelectedAddress;
                delete this._publicKey;
                this.emit(
                    'connect',
                    // Having just set `this._selectedAddress`, `this.publicKey` is definitely non-null
                    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                    this.publicKey!,
                );
            }
        }
    }

    private async performReauthorization(wallet: Web3MobileWallet, authToken: AuthToken): Promise<void> {
        try {
            const authorizationResult = await wallet.authorize({
                auth_token: authToken,
                identity: this._appIdentity,
            });
            // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            Promise.all([
                this._authorizationResultCache.set(authorizationResult),
                this.handleAuthorizationResult(authorizationResult),
            ]);
        } catch (e) {
            this.disconnect();
            throw new WalletDisconnectedError((e instanceof Error && e?.message) || 'Unknown error', e);
        }
    }

    async disconnect(): Promise<void> {
        // TODO: figure out why this call throws "TypeError: _a.terminateSession is not a function"
        //  even though the session termination is actually executed (websocket closes). 
        try { this._wallet?.terminateSession(); } catch (e) {}
        this._authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        this._connecting = false;
        this._connectionGeneration++;
        delete this._authorizationResult;
        delete this._publicKey;
        delete this._selectedAddress;
        delete this._wallet;
        this.emit('disconnect');
    }

    private async transact<TReturn>(callback: (wallet: Web3RemoteMobileWallet) => TReturn): Promise<TReturn> {
        const walletUriBase = this._authorizationResult?.wallet_uri_base;
        const baseConfig = walletUriBase ? { baseUri: walletUriBase } : undefined;
        const remoteConfig = { ...baseConfig, remoteHostAuthority: this._hostAuthority };
        const currentConnectionGeneration = this._connectionGeneration;
        const modal = new EmbeddedDialogModal('MWA QR');

        if (this._wallet) {
            return callback(this._wallet);
        }
        
        try {
            const { associationUrl, result: promise } = await transactRemote(async (wallet) => {
                const result = await callback(wallet);
                modal.close();
                return result;
            }, remoteConfig);
            modal.init(associationUrl.toString());
            modal.open();
            return await promise;
        } catch (e) {
            modal.close();
            if (this._connectionGeneration !== currentConnectionGeneration) {
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
                await this._onWalletNotFound(this);
            }
            throw e;
        }
    }

    private assertIsAuthorized() {
        if (!this._authorizationResult || !this._selectedAddress) throw new WalletNotConnectedError();
        return {
            authToken: this._authorizationResult.auth_token,
            selectedAddress: this._selectedAddress,
        };
    }

    private async performSignTransactions<T extends LegacyTransaction | VersionedTransaction>(
        transactions: T[],
    ): Promise<T[]> {
        const { authToken } = this.assertIsAuthorized();
        try {
            return await this.transact(async (wallet) => {
                await this.performReauthorization(wallet, authToken);
                const signedTransactions = await wallet.signTransactions({
                    transactions,
                });
                return signedTransactions;
            });
        } catch (error: any) {
            throw new WalletSignTransactionError(error?.message, error);
        }
    }

    async sendTransaction<T extends LegacyTransaction | VersionedTransaction>(
        transaction: T,
        connection: Connection,
        options?: SendOptions,
    ): Promise<TransactionSignature> {
        return await this.runWithGuard(async () => {
            const { authToken } = this.assertIsAuthorized();
            const minContextSlot = options?.minContextSlot;
            try {
                return await this.transact(async (wallet) => {
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
                    const [capabilities, _1, _2] = await Promise.all([
                        wallet.getCapabilities(),
                        this.performReauthorization(wallet, authToken),
                        isVersionedTransaction(transaction)
                            ? null
                            : /**
                               * Unlike versioned transactions, legacy `Transaction` objects
                               * may not have an associated `feePayer` or `recentBlockhash`.
                               * This code exists to patch them up in case they are missing.
                               */
                              (async () => {
                                  transaction.feePayer ||= this.publicKey ?? undefined;
                                  if (transaction.recentBlockhash == null) {
                                      const { blockhash } = await connection.getLatestBlockhash({
                                          commitment: getTargetCommitment(),
                                      });
                                      transaction.recentBlockhash = blockhash;
                                  }
                              })(),
                    ]);
                    if (capabilities.supports_sign_and_send_transactions) {
                        const signatures = await wallet.signAndSendTransactions({
                            minContextSlot,
                            transactions: [transaction],
                        });
                        return signatures[0];
                    } else {
                        const [signedTransaction] = await wallet.signTransactions({
                            transactions: [transaction],
                        });
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
                });
            } catch (error: any) {
                throw new WalletSendTransactionError(error?.message, error);
            }
        });
    }

    async signTransaction<T extends LegacyTransaction | VersionedTransaction>(transaction: T): Promise<T> {
        return await this.runWithGuard(async () => {
            const [signedTransaction] = await this.performSignTransactions([transaction]);
            return signedTransaction;
        });
    }

    async signAllTransactions<T extends LegacyTransaction | VersionedTransaction>(transactions: T[]): Promise<T[]> {
        return await this.runWithGuard(async () => {
            const signedTransactions = await this.performSignTransactions(transactions);
            return signedTransactions;
        });
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        return await this.runWithGuard(async () => {
            const { authToken, selectedAddress } = this.assertIsAuthorized();
            try {
                return await this.transact(async (wallet) => {
                    await this.performReauthorization(wallet, authToken);
                    const [signedMessage] = await wallet.signMessages({
                        addresses: [selectedAddress],
                        payloads: [message],
                    });
                    const signature = signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES);
                    return signature;
                });
            } catch (error: any) {
                throw new WalletSignMessageError(error?.message, error);
            }
        });
    }

    async signIn(input?: SolanaSignInInput): Promise<SolanaSignInOutput> {
        return await this.runWithGuard(async () => {
            if (this._readyState !== WalletReadyState.Installed && this._readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this._connecting = true;
            try {
                const authorizationResult = await this.performAuthorization({
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
                this._connecting = false;
            }
        });
    }
}
