import { Connection, PublicKey, Transaction, TransactionSignature } from '@solana/web3.js';
import {
    AuthorizeAPI,
    AuthToken,
    CloneAuthorizationAPI,
    DeauthorizeAPI,
    MobileWalletAPI,
    ReauthorizeAPI,
    transact as baseTransact,
    WalletAssociationConfig,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

export interface Web3MobileWalletAPI extends AuthorizeAPI, CloneAuthorizationAPI, DeauthorizeAPI, ReauthorizeAPI {
    (apiCall: {
        method: 'sign_and_send_transaction';
        auth_token: AuthToken;
        connection: Connection;
        fee_payer?: PublicKey;
        transactions: Transaction[];
    }): Promise<TransactionSignature[]>;
    (apiCall: { method: 'sign_transaction'; auth_token: AuthToken; transactions: Transaction[] }): Promise<
        Transaction[]
    >;
    (apiCall: { method: 'sign_message'; auth_token: AuthToken; byteArrays: Uint8Array[] }): Promise<Uint8Array[]>;
}

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
    callback: (walletAPI: Web3MobileWalletAPI) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    const augmentedCallback: (walletAPI: MobileWalletAPI) => TReturn = (walletAPI) => {
        const augmentedAPI = (async (apiCall) => {
            let latestBlockhashPromise: ReturnType<InstanceType<typeof Connection>['getLatestBlockhash']>;
            async function getLatestBlockhash(connection: Connection) {
                if (latestBlockhashPromise == null) {
                    latestBlockhashPromise = connection.getLatestBlockhash({
                        commitment: connection.commitment,
                    });
                }
                return await latestBlockhashPromise;
            }
            switch (apiCall.method) {
                case 'authorize':
                    return await walletAPI(apiCall);
                case 'clone_authorization':
                    return await walletAPI(apiCall);
                case 'deauthorize':
                    return await walletAPI(apiCall);
                case 'reauthorize':
                    return await walletAPI(apiCall);
                case 'sign_and_send_transaction': {
                    const payloads = await Promise.all(
                        apiCall.transactions.map(async (transaction) => {
                            if (transaction.feePayer == null) {
                                transaction.feePayer = apiCall.fee_payer;
                            }
                            if (transaction.recentBlockhash == null) {
                                const { blockhash } = await getLatestBlockhash(apiCall.connection);
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
                    switch (apiCall.connection.commitment) {
                        case 'confirmed':
                        case 'finalized':
                        case 'processed':
                            targetCommitment = apiCall.connection.commitment;
                            break;
                        default:
                            targetCommitment = 'finalized';
                    }
                    const { signatures } = await walletAPI({
                        method: 'sign_and_send_transaction',
                        auth_token: apiCall.auth_token,
                        commitment: targetCommitment,
                        payloads,
                    });
                    return signatures as TransactionSignature[];
                }
                case 'sign_message': {
                    const payloads = apiCall.byteArrays.map(getBase64StringFromByteArray);
                    const { signed_payloads: base64EncodedSignedMessages } = await walletAPI({
                        method: 'sign_message',
                        auth_token: apiCall.auth_token,
                        payloads,
                    });
                    const signedMessages = base64EncodedSignedMessages.map(getByteArrayFromBase64String);
                    return signedMessages;
                }
                case 'sign_transaction': {
                    const serializedTransactions = apiCall.transactions.map((transaction) =>
                        transaction.serialize({
                            requireAllSignatures: false,
                            verifySignatures: false,
                        }),
                    );
                    const payloads = serializedTransactions.map((serializedTransaction) =>
                        serializedTransaction.toString('base64'),
                    );
                    const { signed_payloads: base64EncodedCompiledTransactions } = await walletAPI({
                        method: 'sign_transaction',
                        auth_token: apiCall.auth_token,
                        payloads,
                    });
                    const compiledTransactions = base64EncodedCompiledTransactions.map(getByteArrayFromBase64String);
                    const transactions = compiledTransactions.map(Transaction.from);
                    return transactions;
                }
                default:
                    // If this switch is exhausive, this should be unreachable.
                    return ((_: never) => undefined)(apiCall);
            }
        }) as Web3MobileWalletAPI;
        return callback(augmentedAPI);
    };
    return await baseTransact(augmentedCallback, config);
}
