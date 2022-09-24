import { Web3MobileWallet, transact } from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import {
    AppIdentity,
    AuthorizationResult,
    AuthToken,
    Base64EncodedAddress,
    Finality,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import {
    BaseMessageSignerWalletAdapter,
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
import {
    Connection,
    PublicKey,
    SendOptions,
    Transaction as LegacyTransaction,
    TransactionSignature,
    TransactionVersion,
    VersionedTransaction,
} from '@solana/web3.js';
import { toUint8Array } from './base64Utils';
import getIsSupported from './getIsSupported';
import { Cluster } from '@solana-mobile/mobile-wallet-adapter-protocol';

export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | undefined>;
    set(authorizationResult: AuthorizationResult): Promise<void>;
}

export interface AddressSelector {
    select(addresses: Base64EncodedAddress[]): Promise<Base64EncodedAddress>;
}

export const SolanaMobileWalletAdapterWalletName = 'Default wallet app' as WalletName;

const SIGNATURE_LENGTH_IN_BYTES = 64;

function getPublicKeyFromAddress(address: Base64EncodedAddress): PublicKey {
    const publicKeyByteArray = toUint8Array(address);
    return new PublicKey(publicKeyByteArray);
}

export class SolanaMobileWalletAdapter extends BaseMessageSignerWalletAdapter {
    readonly supportedTransactionVersions: Set<TransactionVersion> = new Set(
        // FIXME(#244): We can't actually know what versions are supported until we know which wallet we're talking to.
        ['legacy', 0],
    );
    name = SolanaMobileWalletAdapterWalletName;
    url = 'https://solanamobile.com';
    icon =
        'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI4IiB3aWR0aD0iMjgiIHZpZXdCb3g9Ii0zIDAgMjggMjgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGcgZmlsbD0iI0RDQjhGRiI+PHBhdGggZD0iTTE3LjQgMTcuNEgxNXYyLjRoMi40di0yLjRabTEuMi05LjZoLTIuNHYyLjRoMi40VjcuOFoiLz48cGF0aCBkPSJNMjEuNiAzVjBoLTIuNHYzaC0zLjZWMGgtMi40djNoLTIuNHY2LjZINC41YTIuMSAyLjEgMCAxIDEgMC00LjJoMi43VjNINC41QTQuNSA0LjUgMCAwIDAgMCA3LjVWMjRoMjEuNnYtNi42aC0yLjR2NC4ySDIuNFYxMS41Yy41LjMgMS4yLjQgMS44LjVoNy41QTYuNiA2LjYgMCAwIDAgMjQgOVYzaC0yLjRabTAgNS43YTQuMiA0LjIgMCAxIDEtOC40IDBWNS40aDguNHYzLjNaIi8+PC9nPjwvc3ZnPg==';

    private _addressSelector: AddressSelector;
    private _appIdentity: AppIdentity;
    private _authorizationResult: AuthorizationResult | undefined;
    private _authorizationResultCache: AuthorizationResultCache;
    private _connecting = false;
    private _cluster: Cluster;
    private _publicKey: PublicKey | undefined;
    private _readyState: WalletReadyState = getIsSupported() ? WalletReadyState.Loadable : WalletReadyState.Unsupported;
    private _selectedAddress: Base64EncodedAddress | undefined;

    constructor(config: {
        addressSelector: AddressSelector;
        appIdentity: AppIdentity;
        authorizationResultCache: AuthorizationResultCache;
        cluster: Cluster;
    }) {
        super();
        this._authorizationResultCache = config.authorizationResultCache;
        this._addressSelector = config.addressSelector;
        this._appIdentity = config.appIdentity;
        this._cluster = config.cluster;
        if (this._readyState !== WalletReadyState.Unsupported) {
            this._authorizationResultCache.get().then((authorizationResult) => {
                if (authorizationResult) {
                    // Having a prior authorization result is, right now, the best
                    // indication that a mobile wallet is installed. There is no API
                    // we can use to test for whether the association URI is supported.
                    this.emit('readyStateChange', (this._readyState = WalletReadyState.Installed));
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

    private async runWithGuard<TReturn>(callback: () => Promise<TReturn>) {
        try {
            return await callback();
        } catch (e: any) {
            this.emit('error', e);
            throw e;
        }
    }

    async connect(): Promise<void> {
        return await this.runWithGuard(async () => {
            if (this._readyState !== WalletReadyState.Installed && this._readyState !== WalletReadyState.Loadable) {
                throw new WalletNotReadyError();
            }
            this._connecting = true;
            const cachedAuthorizationResult = await this._authorizationResultCache.get();
            if (cachedAuthorizationResult) {
                this._authorizationResult = cachedAuthorizationResult;
                this._connecting = false;
                if (this._readyState !== WalletReadyState.Installed) {
                    this.emit('readyStateChange', (this._readyState = WalletReadyState.Installed));
                }
                this._selectedAddress = await this._addressSelector.select(
                    cachedAuthorizationResult.accounts.map(({ address }) => address),
                );
                this.emit(
                    'connect',
                    // Having just set `this._selectedAddress`, `this.publicKey` is definitely non-null
                    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                    this.publicKey!,
                );
                return;
            }
            try {
                await this.transact(async (wallet) => {
                    const authorizationResult = await wallet.authorize({
                        cluster: this._cluster,
                        identity: this._appIdentity,
                    });
                    this.handleAuthorizationResult(authorizationResult); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                });
            } catch (e) {
                throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
            } finally {
                this._connecting = false;
            }
        });
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
        await this._authorizationResultCache.set(authorizationResult);
    }

    private async performReauthorization(wallet: Web3MobileWallet, authToken: AuthToken): Promise<void> {
        try {
            const authorizationResult = await wallet.reauthorize({
                auth_token: authToken,
            });
            this.handleAuthorizationResult(authorizationResult); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        } catch (e) {
            this.disconnect();
            throw new WalletDisconnectedError((e instanceof Error && e?.message) || 'Unknown error', e);
        }
    }

    async disconnect(): Promise<void> {
        this._authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        delete this._authorizationResult;
        delete this._publicKey;
        delete this._selectedAddress;
        this.emit('disconnect');
    }

    private async transact<TReturn>(callback: (wallet: Web3MobileWallet) => TReturn): Promise<TReturn> {
        const walletUriBase = this._authorizationResult?.wallet_uri_base;
        const config = walletUriBase ? { baseUri: walletUriBase } : undefined;
        return await transact(callback, config);
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
                    await Promise.all([
                        this.performReauthorization(wallet, authToken),
                        'version' in transaction
                            ? null
                            : /**
                               * Unlike versioned transactions, legacy `Transaction` objects
                               * may not have an associated `feePayer` or `recentBlockhash`.
                               * This code exists to patch them up in case they are missing.
                               */
                              (async () => {
                                  transaction.feePayer ||= this.publicKey ?? undefined;
                                  if (transaction.recentBlockhash == null) {
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
                                          targetCommitment === 'finalized'
                                              ? 2
                                              : targetCommitment === 'confirmed'
                                              ? 1
                                              : 0;
                                      const { blockhash } = await connection.getLatestBlockhash({
                                          commitment:
                                              preflightCommitmentScore < targetCommitmentScore
                                                  ? targetPreflightCommitment
                                                  : targetCommitment,
                                      });
                                      transaction.recentBlockhash = blockhash;
                                  }
                              })(),
                    ]);
                    const signatures = await wallet.signAndSendTransactions({
                        minContextSlot,
                        transactions: [transaction],
                    });
                    return signatures[0];
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
}
