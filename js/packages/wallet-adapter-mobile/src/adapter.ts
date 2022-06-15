import {
    AppIdentity,
    AuthorizationResult,
    AuthToken,
    MobileWallet,
    withLocalWallet,
} from '@solana/mobile-wallet-protocol';
import {
    BaseMessageSignerWalletAdapter,
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
import { Connection, PublicKey, SendOptions, Transaction, TransactionSignature } from '@solana/web3.js';

export interface AuthorizationResultCache {
    clear(): Promise<void>;
    get(): Promise<AuthorizationResult | undefined>;
    set(authorizationResult: AuthorizationResult): Promise<void>;
}

export const NativeWalletName = 'Native' as WalletName;

function getBase64StringFromByteArray(byteArray: Uint8Array): string {
    return btoa(String.fromCharCode.call(null, ...byteArray));
}

function getByteArrayFromBase64String(base64EncodedByteArray: string): Uint8Array {
    return new Uint8Array(
        atob(base64EncodedByteArray)
            .split('')
            .map((c) => c.charCodeAt(0)),
    );
}

export class NativeWalletAdapter extends BaseMessageSignerWalletAdapter {
    name = NativeWalletName;
    url = 'https://solana.com';
    icon =
        'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHdpZHRoPSIzNCIgaGVpZ2h0PSIzNCIgdmlld0JveD0iMCAwIDEwMSA4OCIgZmlsbD0ibm9uZSI+CjxwYXRoIGQ9Ik0xMDAuNDggNjkuMzgxN0w4My44MDY4IDg2LjgwMTVDODMuNDQ0NCA4Ny4xNzk5IDgzLjAwNTggODcuNDgxNiA4Mi41MTg1IDg3LjY4NzhDODIuMDMxMiA4Ny44OTQgODEuNTA1NSA4OC4wMDAzIDgwLjk3NDMgODhIMS45MzU2M0MxLjU1ODQ5IDg4IDEuMTg5NTcgODcuODkyNiAwLjg3NDIwMiA4Ny42OTEyQzAuNTU4ODI5IDg3LjQ4OTcgMC4zMTA3NCA4Ny4yMDI5IDAuMTYwNDE2IDg2Ljg2NTlDMC4wMTAwOTIzIDg2LjUyOSAtMC4wMzU5MTgxIDg2LjE1NjYgMC4wMjgwMzgyIDg1Ljc5NDVDMC4wOTE5OTQ0IDg1LjQzMjQgMC4yNjMxMzEgODUuMDk2NCAwLjUyMDQyMiA4NC44Mjc4TDE3LjIwNjEgNjcuNDA4QzE3LjU2NzYgNjcuMDMwNiAxOC4wMDQ3IDY2LjcyOTUgMTguNDkwNCA2Ni41MjM0QzE4Ljk3NjIgNjYuMzE3MiAxOS41MDAyIDY2LjIxMDQgMjAuMDMwMSA2Ni4yMDk1SDk5LjA2NDRDOTkuNDQxNSA2Ni4yMDk1IDk5LjgxMDQgNjYuMzE2OSAxMDAuMTI2IDY2LjUxODNDMTAwLjQ0MSA2Ni43MTk4IDEwMC42ODkgNjcuMDA2NyAxMDAuODQgNjcuMzQzNkMxMDAuOTkgNjcuNjgwNiAxMDEuMDM2IDY4LjA1MjkgMTAwLjk3MiA2OC40MTVDMTAwLjkwOCA2OC43NzcxIDEwMC43MzcgNjkuMTEzMSAxMDAuNDggNjkuMzgxN1pNODMuODA2OCAzNC4zMDMyQzgzLjQ0NDQgMzMuOTI0OCA4My4wMDU4IDMzLjYyMzEgODIuNTE4NSAzMy40MTY5QzgyLjAzMTIgMzMuMjEwOCA4MS41MDU1IDMzLjEwNDUgODAuOTc0MyAzMy4xMDQ4SDEuOTM1NjNDMS41NTg0OSAzMy4xMDQ4IDEuMTg5NTcgMzMuMjEyMSAwLjg3NDIwMiAzMy40MTM2QzAuNTU4ODI5IDMzLjYxNTEgMC4zMTA3NCAzMy45MDE5IDAuMTYwNDE2IDM0LjIzODhDMC4wMTAwOTIzIDM0LjU3NTggLTAuMDM1OTE4MSAzNC45NDgyIDAuMDI4MDM4MiAzNS4zMTAzQzAuMDkxOTk0NCAzNS42NzIzIDAuMjYzMTMxIDM2LjAwODMgMC41MjA0MjIgMzYuMjc3TDE3LjIwNjEgNTMuNjk2OEMxNy41Njc2IDU0LjA3NDIgMTguMDA0NyA1NC4zNzUyIDE4LjQ5MDQgNTQuNTgxNEMxOC45NzYyIDU0Ljc4NzUgMTkuNTAwMiA1NC44OTQ0IDIwLjAzMDEgNTQuODk1Mkg5OS4wNjQ0Qzk5LjQ0MTUgNTQuODk1MiA5OS44MTA0IDU0Ljc4NzkgMTAwLjEyNiA1NC41ODY0QzEwMC40NDEgNTQuMzg0OSAxMDAuNjg5IDU0LjA5ODEgMTAwLjg0IDUzLjc2MTJDMTAwLjk5IDUzLjQyNDIgMTAxLjAzNiA1My4wNTE4IDEwMC45NzIgNTIuNjg5N0MxMDAuOTA4IDUyLjMyNzcgMTAwLjczNyA1MS45OTE3IDEwMC40OCA1MS43MjNMODMuODA2OCAzNC4zMDMyWk0xLjkzNTYzIDIxLjc5MDVIODAuOTc0M0M4MS41MDU1IDIxLjc5MDcgODIuMDMxMiAyMS42ODQ1IDgyLjUxODUgMjEuNDc4M0M4My4wMDU4IDIxLjI3MjEgODMuNDQ0NCAyMC45NzA0IDgzLjgwNjggMjAuNTkyTDEwMC40OCAzLjE3MjE5QzEwMC43MzcgMi45MDM1NyAxMDAuOTA4IDIuNTY3NTggMTAwLjk3MiAyLjIwNTVDMTAxLjAzNiAxLjg0MzQyIDEwMC45OSAxLjQ3MTAzIDEwMC44NCAxLjEzNDA4QzEwMC42ODkgMC43OTcxMyAxMDAuNDQxIDAuNTEwMjk2IDEwMC4xMjYgMC4zMDg4MjNDOTkuODEwNCAwLjEwNzM0OSA5OS40NDE1IDEuMjQwNzRlLTA1IDk5LjA2NDQgMEwyMC4wMzAxIDBDMTkuNTAwMiAwLjAwMDg3ODM5NyAxOC45NzYyIDAuMTA3Njk5IDE4LjQ5MDQgMC4zMTM4NDhDMTguMDA0NyAwLjUxOTk5OCAxNy41Njc2IDAuODIxMDg3IDE3LjIwNjEgMS4xOTg0OEwwLjUyNDcyMyAxOC42MTgzQzAuMjY3NjgxIDE4Ljg4NjYgMC4wOTY2MTk4IDE5LjIyMjMgMC4wMzI1MTg1IDE5LjU4MzlDLTAuMDMxNTgyOSAxOS45NDU2IDAuMDE0MDYyNCAyMC4zMTc3IDAuMTYzODU2IDIwLjY1NDVDMC4zMTM2NSAyMC45OTEzIDAuNTYxMDgxIDIxLjI3ODEgMC44NzU4MDQgMjEuNDc5OUMxLjE5MDUzIDIxLjY4MTcgMS41NTg4NiAyMS43ODk2IDEuOTM1NjMgMjEuNzkwNVoiIGZpbGw9InVybCgjcGFpbnQwX2xpbmVhcl8xNzRfNDQwMykiPjwvcGF0aD4KPGRlZnM+CjxsaW5lYXJHcmFkaWVudCBpZD0icGFpbnQwX2xpbmVhcl8xNzRfNDQwMyIgeDE9IjguNTI1NTgiIHkxPSI5MC4wOTczIiB4Mj0iODguOTkzMyIgeTI9Ii0zLjAxNjIyIiBncmFkaWVudFVuaXRzPSJ1c2VyU3BhY2VPblVzZSI+CjxzdG9wIG9mZnNldD0iMC4wOCIgc3RvcC1jb2xvcj0iIzk5NDVGRiI+PC9zdG9wPgo8c3RvcCBvZmZzZXQ9IjAuMyIgc3RvcC1jb2xvcj0iIzg3NTJGMyI+PC9zdG9wPgo8c3RvcCBvZmZzZXQ9IjAuNSIgc3RvcC1jb2xvcj0iIzU0OTdENSI+PC9zdG9wPgo8c3RvcCBvZmZzZXQ9IjAuNiIgc3RvcC1jb2xvcj0iIzQzQjRDQSI+PC9zdG9wPgo8c3RvcCBvZmZzZXQ9IjAuNzIiIHN0b3AtY29sb3I9IiMyOEUwQjkiPjwvc3RvcD4KPHN0b3Agb2Zmc2V0PSIwLjk3IiBzdG9wLWNvbG9yPSIjMTlGQjlCIj48L3N0b3A+CjwvbGluZWFyR3JhZGllbnQ+CjwvZGVmcz4KPC9zdmc+';

    private _appIdentity: AppIdentity;
    private _authorizationResult: AuthorizationResult | undefined;
    private _authorizationResultCache: AuthorizationResultCache | undefined;
    private _connecting = false;
    private _publicKey: PublicKey | undefined;
    private _readyState: WalletReadyState =
        typeof window === 'undefined' || typeof document === 'undefined'
            ? WalletReadyState.Unsupported
            : WalletReadyState.NotDetected;

    constructor(config: { appIdentity: AppIdentity; authorizationResultCache?: AuthorizationResultCache }) {
        super();
        this._authorizationResultCache = config.authorizationResultCache;
        this._appIdentity = config.appIdentity;
        if (this._readyState !== WalletReadyState.Unsupported) {
            // TODO: Implement actual detection strategy
            this._readyState = WalletReadyState.Installed;
        }
    }

    get publicKey(): PublicKey | null {
        if (this._publicKey == null && this._authorizationResult != null) {
            this._publicKey = new PublicKey(this._authorizationResult.publicKey);
        }
        return this._publicKey ? this._publicKey : null;
    }

    get connecting(): boolean {
        return this._connecting;
    }

    get readyState(): WalletReadyState {
        return this._readyState;
    }

    async connect(): Promise<void> {
        if (this._readyState !== WalletReadyState.Installed) {
            const err = new WalletNotReadyError();
            this.emit('error', err);
            throw err;
        }
        this._connecting = true;
        const cachedAuthorizationResult = await this._authorizationResultCache?.get();
        if (cachedAuthorizationResult) {
            this._authorizationResult = cachedAuthorizationResult;
            this._connecting = false;
            this.emit(
                'connect',
                // Having just set an `authorizationResult`, `this.publicKey` is definitely non-null
                // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
                this.publicKey!,
            );
            return;
        }
        try {
            await withLocalWallet(async (mobileWallet) => {
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
        if (this._authorizationResultCache) {
            await this._authorizationResultCache.set(authorizationResult);
        }
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
            // TODO: throw a first-class error
            throw e;
        }
    }

    async disconnect(): Promise<void> {
        if (this._authorizationResultCache) {
            this._authorizationResultCache.clear(); // TODO: Evaluate whether there's any threat to not `awaiting` this expression
        }
        delete this._authorizationResult;
        delete this._publicKey;
        this.emit('disconnect');
    }

    private assertIsAuthorized(): [AuthorizationResult, PublicKey] {
        const authorizationResult = this._authorizationResult;
        if (!authorizationResult) throw new WalletNotConnectedError();
        return [
            authorizationResult,
            // Having an `authorizationResult` implies that `this.publicKey` is non-null
            // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
            this.publicKey!,
        ];
    }

    private async performSignTransactions(transactions: Transaction[]): Promise<Transaction[]> {
        try {
            const [authorizationResult, publicKey] = this.assertIsAuthorized();
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
                return await withLocalWallet(async (mobileWallet) => {
                    const freshAuthToken = await this.performReauthorization(mobileWallet, authorizationResult);
                    const { signatures } = await mobileWallet('sign_transaction', {
                        auth_token: freshAuthToken,
                        payloads,
                        return_signed_payloads: false,
                    });
                    const decodedSignatures = signatures.map(getByteArrayFromBase64String);
                    return transactions.map((transaction, ii) => {
                        const signature = decodedSignatures[ii];
                        transaction.addSignature(publicKey, signature as Buffer);
                        return transaction;
                    });
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
            const [authorizationResult] = this.assertIsAuthorized();
            try {
                const serializedTransaction = transaction.serialize({
                    requireAllSignatures: false,
                    verifySignatures: false,
                });
                const payloads = [serializedTransaction.toString('base64')];
                return await withLocalWallet(async (mobileWallet) => {
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
            const [authorizationResult] = this.assertIsAuthorized();
            try {
                return await withLocalWallet(async (mobileWallet) => {
                    const freshAuthToken = await this.performReauthorization(mobileWallet, authorizationResult);
                    const {
                        signed_payloads: [signedPayloadBase64Encoded],
                    } = await mobileWallet('sign_message', {
                        auth_token: freshAuthToken,
                        payloads: [getBase64StringFromByteArray(message)],
                    });
                    const signedPayload = getByteArrayFromBase64String(signedPayloadBase64Encoded);
                    return signedPayload;
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
