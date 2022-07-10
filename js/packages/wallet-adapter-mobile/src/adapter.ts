import {
    AppIdentity,
    AuthorizationResult,
    AuthToken,
    MobileWallet,
    withLocalWallet,
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
import { Connection, PublicKey, SendOptions, Transaction, TransactionSignature } from '@solana/web3.js';
import getIsSupported from './getIsSupported';

export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | undefined>;
    set(authorizationResult: AuthorizationResult): Promise<void>;
}

export const SolanaMobileWalletAdapterWalletName = 'Default wallet app' as WalletName;

const SIGNATURE_LENGTH_IN_BYTES = 64;

function getBase64StringFromByteArray(byteArray: Uint8Array): string {
    return window.btoa(String.fromCharCode.call(null, ...byteArray));
}

function getByteArrayFromBase64String(base64EncodedByteArray: string): Uint8Array {
    return new Uint8Array(
        window
            .atob(base64EncodedByteArray)
            .split('')
            .map((c) => c.charCodeAt(0)),
    );
}

export class SolanaMobileWalletAdapter extends BaseMessageSignerWalletAdapter {
    name = SolanaMobileWalletAdapterWalletName;
    url = 'https://solanamobile.com';
    icon =
        'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI4IiB3aWR0aD0iMjgiIHZpZXdCb3g9Ii0zIDAgMjggMjgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGcgZmlsbD0iI0RDQjhGRiI+PHBhdGggZD0iTTE3LjQgMTcuNEgxNXYyLjRoMi40di0yLjRabTEuMi05LjZoLTIuNHYyLjRoMi40VjcuOFoiLz48cGF0aCBkPSJNMjEuNiAzVjBoLTIuNHYzaC0zLjZWMGgtMi40djNoLTIuNHY2LjZINC41YTIuMSAyLjEgMCAxIDEgMC00LjJoMi43VjNINC41QTQuNSA0LjUgMCAwIDAgMCA3LjVWMjRoMjEuNnYtNi42aC0yLjR2NC4ySDIuNFYxMS41Yy41LjMgMS4yLjQgMS44LjVoNy41QTYuNiA2LjYgMCAwIDAgMjQgOVYzaC0yLjRabTAgNS43YTQuMiA0LjIgMCAxIDEtOC40IDBWNS40aDguNHYzLjNaIi8+PC9nPjwvc3ZnPg==';

    private _appIdentity: AppIdentity;
    private _authorizationResult: AuthorizationResult | undefined;
    private _authorizationResultCache: AuthorizationResultCache;
    private _connecting = false;
    private _publicKey: PublicKey | undefined;
    private _readyState: WalletReadyState = getIsSupported() ? WalletReadyState.Loadable : WalletReadyState.Unsupported;

    constructor(config: { appIdentity: AppIdentity; authorizationResultCache: AuthorizationResultCache }) {
        super();
        this._authorizationResultCache = config.authorizationResultCache;
        this._appIdentity = config.appIdentity;
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
        if (this._publicKey == null && this._authorizationResult != null) {
            this._publicKey = new PublicKey(this._authorizationResult.publicKey);
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

    async connect(): Promise<void> {
        if (this._readyState !== WalletReadyState.Installed && this._readyState !== WalletReadyState.Loadable) {
            const err = new WalletNotReadyError();
            this.emit('error', err);
            throw err;
        }
        this._connecting = true;
        const cachedAuthorizationResult = await this._authorizationResultCache.get();
        if (cachedAuthorizationResult) {
            this._authorizationResult = cachedAuthorizationResult;
            this._connecting = false;
            if (this._readyState !== WalletReadyState.Installed) {
                this.emit('readyStateChange', (this._readyState = WalletReadyState.Installed));
            }
            this.emit(
                'connect',
                // Having just set an `authorizationResult`, `this.publicKey` is definitely non-null
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                this.publicKey!,
            );
            return;
        }
        try {
            await this.withWallet(async (mobileWallet) => {
                const {
                    auth_token,
                    pub_key: base58PublicKey,
                    wallet_uri_base,
                } = await mobileWallet('authorize', { identity: this._appIdentity });
                try {
                    this._publicKey = new PublicKey(base58PublicKey);
                } catch (e) {
                    throw new WalletPublicKeyError((e instanceof Error && e?.message) || 'Unknown error', e);
                }
                this.handleAuthorizationResult({
                    authToken: auth_token,
                    publicKey: base58PublicKey,
                    walletUriBase: wallet_uri_base,
                }); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
                this.emit(
                    'connect',
                    // Having just set an `authorizationResult`, `this.publicKey` is definitely non-null
                    // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                    this.publicKey!,
                );
            });
        } catch (e) {
            throw new WalletConnectionError((e instanceof Error && e.message) || 'Unknown error', e);
        } finally {
            this._connecting = false;
        }
    }

    private async handleAuthorizationResult(authorizationResult: AuthorizationResult): Promise<void> {
        this._authorizationResult = authorizationResult;
        await this._authorizationResultCache.set(authorizationResult);
    }

    private async performReauthorization(
        mobileWallet: MobileWallet,
        currentAuthorizationResult: AuthorizationResult,
    ): Promise<AuthToken> {
        try {
            const { auth_token } = await mobileWallet('reauthorize', {
                auth_token: currentAuthorizationResult.authToken,
            });
            if (currentAuthorizationResult.authToken !== auth_token) {
                this.handleAuthorizationResult({
                    ...currentAuthorizationResult,
                    authToken: auth_token,
                }); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
            }
            return auth_token;
        } catch (e) {
            this.disconnect();
            throw new WalletDisconnectedError((e instanceof Error && e?.message) || 'Unknown error', e);
        }
    }

    async disconnect(): Promise<void> {
        this._authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        delete this._authorizationResult;
        delete this._publicKey;
        this.emit('disconnect');
    }

    private async withWallet<TReturn>(callback: (wallet: MobileWallet) => TReturn): Promise<TReturn> {
        const walletUriBase = this._authorizationResult?.walletUriBase;
        const config = walletUriBase ? { baseUri: walletUriBase } : undefined;
        return await withLocalWallet(callback, config);
    }

    private assertIsAuthorized(): AuthorizationResult {
        const authorizationResult = this._authorizationResult;
        if (!authorizationResult) throw new WalletNotConnectedError();
        return authorizationResult;
    }

    private async performSignTransactions(transactions: Transaction[]): Promise<Transaction[]> {
        try {
            const authorizationResult = this.assertIsAuthorized();
            try {
                const serializedTransactions = transactions.map((transaction) =>
                    transaction.serialize({
                        requireAllSignatures: false,
                        verifySignatures: false,
                    }),
                );
                const payloads = serializedTransactions.map((serializedTransaction) =>
                    serializedTransaction.toString('base64'),
                );
                return await this.withWallet(async (mobileWallet) => {
                    const freshAuthToken = await this.performReauthorization(mobileWallet, authorizationResult);
                    const { signed_payloads: base64EncodedCompiledTransactions } = await mobileWallet(
                        'sign_transaction',
                        { auth_token: freshAuthToken, payloads },
                    );
                    const compiledTransactions = base64EncodedCompiledTransactions.map(getByteArrayFromBase64String);
                    const transactions = compiledTransactions.map(Transaction.from);
                    return transactions;
                });
            } catch (error: any) {
                throw new WalletSignTransactionError(error?.message, error);
            }
        } catch (error: any) {
            this.emit('error', error);
            throw error;
        }
    }

    async sendTransaction(
        transaction: Transaction,
        connection: Connection,
        _options?: SendOptions,
    ): Promise<TransactionSignature> {
        try {
            const authorizationResult = this.assertIsAuthorized();
            try {
                if (transaction.feePayer == null) {
                    transaction.feePayer = this.publicKey || undefined;
                }
                if (transaction.recentBlockhash == null) {
                    const { blockhash } = await connection.getRecentBlockhash(connection.commitment);
                    transaction.recentBlockhash = blockhash;
                }
                const serializedTransaction = transaction.serialize({
                    requireAllSignatures: false,
                    verifySignatures: false,
                });
                const payloads = [serializedTransaction.toString('base64')];
                return await this.withWallet(async (mobileWallet) => {
                    const freshAuthToken = await this.performReauthorization(mobileWallet, authorizationResult);
                    let targetCommitment: 'confirmed' | 'finalized' | 'processed';
                    switch (connection.commitment) {
                        case 'confirmed':
                        case 'finalized':
                        case 'processed':
                            targetCommitment = connection.commitment;
                            break;
                        default:
                            targetCommitment = 'finalized';
                    }
                    const { signatures } = await mobileWallet('sign_and_send_transaction', {
                        auth_token: freshAuthToken,
                        commitment: targetCommitment,
                        payloads,
                    });
                    return signatures[0];
                });
            } catch (error: any) {
                throw new WalletSendTransactionError(error?.message, error);
            }
        } catch (error: any) {
            this.emit('error', error);
            throw error;
        }
    }

    async signTransaction(transaction: Transaction): Promise<Transaction> {
        const [signedTransaction] = await this.performSignTransactions([transaction]);
        return signedTransaction;
    }

    async signAllTransactions(transactions: Transaction[]): Promise<Transaction[]> {
        const signedTransactions = await this.performSignTransactions(transactions);
        return signedTransactions;
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        try {
            const authorizationResult = this.assertIsAuthorized();
            try {
                return await this.withWallet(async (mobileWallet) => {
                    const freshAuthToken = await this.performReauthorization(mobileWallet, authorizationResult);
                    const {
                        signed_payloads: [base64EncodedSignedMessage],
                    } = await mobileWallet('sign_message', {
                        auth_token: freshAuthToken,
                        payloads: [getBase64StringFromByteArray(message)],
                    });
                    const signedMessage = getByteArrayFromBase64String(base64EncodedSignedMessage);
                    const signature = signedMessage.slice(-SIGNATURE_LENGTH_IN_BYTES);
                    return signature;
                });
            } catch (error: any) {
                throw new WalletSignMessageError(error?.message, error);
            }
        } catch (error: any) {
            this.emit('error', error);
            throw error;
        }
    }
}
