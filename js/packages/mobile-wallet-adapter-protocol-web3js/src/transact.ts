import { Connection, PublicKey, Transaction, TransactionSignature } from '@solana/web3.js';
import {
    AuthorizeAPI,
    AuthToken,
    CloneAuthorizationAPI,
    DeauthorizeAPI,
    MobileWallet,
    ReauthorizeAPI,
    transact as baseTransact,
    WalletAssociationConfig,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

interface Web3SignAndSendTransactionAPI {
    signAndSendTransaction(params: {
        auth_token: AuthToken;
        connection: Connection;
        fee_payer?: PublicKey;
        transactions: Transaction[];
    }): Promise<TransactionSignature[]>;
}

interface Web3SignTransactionAPI {
    signTransaction(params: { auth_token: AuthToken; transactions: Transaction[] }): Promise<Transaction[]>;
}

interface Web3SignMessageAPI {
    signMessage(params: { auth_token: AuthToken; payloads: Uint8Array[] }): Promise<Uint8Array[]>;
}

export interface Web3MobileWallet
    extends AuthorizeAPI,
        CloneAuthorizationAPI,
        DeauthorizeAPI,
        ReauthorizeAPI,
        Web3SignAndSendTransactionAPI,
        Web3SignTransactionAPI,
        Web3SignMessageAPI {}

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

export async function transact<TReturn>(
    callback: (wallet: Web3MobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    const augmentedCallback: (wallet: MobileWallet) => TReturn = (wallet) => {
        const augmentedAPI = new Proxy<Web3MobileWallet>({} as Web3MobileWallet, {
            get<TMethodName extends keyof Web3MobileWallet>(target: Web3MobileWallet, p: TMethodName) {
                if (target[p] == null) {
                    switch (p) {
                        case 'signAndSendTransaction':
                            target[p] = async function (
                                params: Parameters<Web3MobileWallet['signAndSendTransaction']>[0],
                            ) {
                                let latestBlockhashPromise: ReturnType<
                                    InstanceType<typeof Connection>['getLatestBlockhash']
                                >;
                                async function getLatestBlockhash(connection: Connection) {
                                    if (latestBlockhashPromise == null) {
                                        latestBlockhashPromise = connection.getLatestBlockhash({
                                            commitment: connection.commitment,
                                        });
                                    }
                                    return await latestBlockhashPromise;
                                }
                                const payloads = await Promise.all(
                                    params.transactions.map(async (transaction) => {
                                        if (transaction.feePayer == null) {
                                            transaction.feePayer = params.fee_payer;
                                        }
                                        if (transaction.recentBlockhash == null) {
                                            const { blockhash } = await getLatestBlockhash(params.connection);
                                            transaction.recentBlockhash = blockhash;
                                        }
                                        const serializedTransaction = transaction.serialize({
                                            requireAllSignatures: false,
                                            verifySignatures: false,
                                        });
                                        return serializedTransaction.toString('base64');
                                    }),
                                );
                                let targetCommitment: 'confirmed' | 'finalized' | 'processed';
                                switch (params.connection.commitment) {
                                    case 'confirmed':
                                    case 'finalized':
                                    case 'processed':
                                        targetCommitment = params.connection.commitment;
                                        break;
                                    default:
                                        targetCommitment = 'finalized';
                                }
                                const { signatures } = await wallet.signAndSendTransaction({
                                    auth_token: params.auth_token,
                                    commitment: targetCommitment,
                                    payloads,
                                });
                                return signatures as TransactionSignature[];
                            } as Web3MobileWallet[TMethodName];
                            break;
                        case 'signMessage':
                            target[p] = async function (params: Parameters<Web3MobileWallet['signMessage']>[0]) {
                                const payloads = params.payloads.map(getBase64StringFromByteArray);
                                const { signed_payloads: base64EncodedSignedMessages } = await wallet.signMessage({
                                    auth_token: params.auth_token,
                                    payloads,
                                });
                                const signedMessages = base64EncodedSignedMessages.map(getByteArrayFromBase64String);
                                return signedMessages;
                            } as Web3MobileWallet[TMethodName];
                            break;
                        case 'signTransaction':
                            target[p] = async function (params: Parameters<Web3MobileWallet['signTransaction']>[0]) {
                                const serializedTransactions = params.transactions.map((transaction) =>
                                    transaction.serialize({
                                        requireAllSignatures: false,
                                        verifySignatures: false,
                                    }),
                                );
                                const payloads = serializedTransactions.map((serializedTransaction) =>
                                    serializedTransaction.toString('base64'),
                                );
                                const { signed_payloads: base64EncodedCompiledTransactions } =
                                    await wallet.signTransaction({
                                        auth_token: params.auth_token,
                                        payloads,
                                    });
                                const compiledTransactions =
                                    base64EncodedCompiledTransactions.map(getByteArrayFromBase64String);
                                const transactions = compiledTransactions.map(Transaction.from);
                                return transactions;
                            } as Web3MobileWallet[TMethodName];
                            break;
                        default: {
                            target[p] = wallet[p] as unknown as Web3MobileWallet[TMethodName];
                            break;
                        }
                    }
                }
                return target[p];
            },
            defineProperty() {
                return false;
            },
            deleteProperty() {
                return false;
            },
        });
        return callback(augmentedAPI);
    };
    return await baseTransact(augmentedCallback, config);
}
