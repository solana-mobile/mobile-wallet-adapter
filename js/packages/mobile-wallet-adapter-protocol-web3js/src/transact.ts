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

interface Web3SignAndSendTransactionAPI {
    (
        method: 'sign_and_send_transaction',
        params: {
            auth_token: AuthToken;
            connection: Connection;
            fee_payer?: PublicKey;
            transactions: Transaction[];
        },
    ): Promise<TransactionSignature[]>;
}

interface Web3SignTransactionAPI {
    (method: 'sign_transaction', params: { auth_token: AuthToken; transactions: Transaction[] }): Promise<
        Transaction[]
    >;
}

interface Web3SignMessageAPI {
    (method: 'sign_message', params: { auth_token: AuthToken; payloads: Uint8Array[] }): Promise<Uint8Array[]>;
}

export interface Web3MobileWalletAPI
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
    callback: (walletAPI: Web3MobileWalletAPI) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    const augmentedCallback: (walletAPI: MobileWalletAPI) => TReturn = (walletAPI) => {
        const augmentedAPI = (async (...args) => {
            let latestBlockhashPromise: ReturnType<InstanceType<typeof Connection>['getLatestBlockhash']>;
            async function getLatestBlockhash(connection: Connection) {
                if (latestBlockhashPromise == null) {
                    latestBlockhashPromise = connection.getLatestBlockhash({
                        commitment: connection.commitment,
                    });
                }
                return await latestBlockhashPromise;
            }
            const [method] = args;
            switch (method) {
                case 'sign_and_send_transaction': {
                    const params = args[1] as Parameters<Web3SignAndSendTransactionAPI>[1];
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
                    const { signatures } = await walletAPI('sign_and_send_transaction', {
                        auth_token: params.auth_token,
                        commitment: targetCommitment,
                        payloads,
                    });
                    return signatures as TransactionSignature[];
                }
                case 'sign_message': {
                    const params = args[1] as Parameters<Web3SignMessageAPI>[1];
                    const payloads = params.payloads.map(getBase64StringFromByteArray);
                    const { signed_payloads: base64EncodedSignedMessages } = await walletAPI('sign_message', {
                        auth_token: params.auth_token,
                        payloads,
                    });
                    const signedMessages = base64EncodedSignedMessages.map(getByteArrayFromBase64String);
                    return signedMessages;
                }
                case 'sign_transaction': {
                    const params = args[1] as Parameters<Web3SignTransactionAPI>[1];
                    const serializedTransactions = params.transactions.map((transaction) =>
                        transaction.serialize({
                            requireAllSignatures: false,
                            verifySignatures: false,
                        }),
                    );
                    const payloads = serializedTransactions.map((serializedTransaction) =>
                        serializedTransaction.toString('base64'),
                    );
                    const { signed_payloads: base64EncodedCompiledTransactions } = await walletAPI('sign_transaction', {
                        auth_token: params.auth_token,
                        payloads,
                    });
                    const compiledTransactions = base64EncodedCompiledTransactions.map(getByteArrayFromBase64String);
                    const transactions = compiledTransactions.map(Transaction.from);
                    return transactions;
                }
                default:
                    return await walletAPI(...(args as Parameters<MobileWalletAPI>));
            }
        }) as Web3MobileWalletAPI;
        return callback(augmentedAPI);
    };
    return await baseTransact(augmentedCallback, config);
}
