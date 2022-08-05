import { Transaction, TransactionSignature } from '@solana/web3.js';
import {
    AuthorizeAPI,
    Base64EncodedAddress,
    CloneAuthorizationAPI,
    DeauthorizeAPI,
    MobileWallet,
    ReauthorizeAPI,
    transact as baseTransact,
    WalletAssociationConfig,
} from '@solana-mobile/mobile-wallet-adapter-protocol';
import bs58 from 'bs58';

import { fromUint8Array, toUint8Array } from './base64Utils';

interface Web3SignAndSendTransactionsAPI {
    signAndSendTransactions(params: {
        minContextSlot?: number;
        transactions: Transaction[];
    }): Promise<TransactionSignature[]>;
}

interface Web3SignTransactionsAPI {
    signTransactions(params: { transactions: Transaction[] }): Promise<Transaction[]>;
}

interface Web3SignMessagesAPI {
    signMessages(params: { addresses: Base64EncodedAddress[]; payloads: Uint8Array[] }): Promise<Uint8Array[]>;
}

export interface Web3MobileWallet
    extends AuthorizeAPI,
        CloneAuthorizationAPI,
        DeauthorizeAPI,
        ReauthorizeAPI,
        Web3SignAndSendTransactionsAPI,
        Web3SignTransactionsAPI,
        Web3SignMessagesAPI {}

export async function transact<TReturn>(
    callback: (wallet: Web3MobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    const augmentedCallback: (wallet: MobileWallet) => TReturn = (wallet) => {
        const augmentedAPI = new Proxy<Web3MobileWallet>({} as Web3MobileWallet, {
            get<TMethodName extends keyof Web3MobileWallet>(target: Web3MobileWallet, p: TMethodName) {
                if (target[p] == null) {
                    switch (p) {
                        case 'signAndSendTransactions':
                            target[p] = async function ({
                                minContextSlot,
                                transactions,
                                ...rest
                            }: Parameters<Web3MobileWallet['signAndSendTransactions']>[0]) {
                                const payloads = await Promise.all(
                                    transactions.map(async (transaction) => {
                                        const serializedTransaction = await transaction.serialize({
                                            requireAllSignatures: false,
                                            verifySignatures: false,
                                        });
                                        return serializedTransaction.toString('base64');
                                    }),
                                );
                                const { signatures: base64EncodedSignatures } = await wallet.signAndSendTransactions({
                                    ...rest,
                                    ...(minContextSlot != null
                                        ? { options: { min_context_slot: minContextSlot } }
                                        : null),
                                    payloads,
                                });
                                const signatures = base64EncodedSignatures.map(toUint8Array).map(bs58.encode);
                                return signatures as TransactionSignature[];
                            } as Web3MobileWallet[TMethodName];
                            break;
                        case 'signMessages':
                            target[p] = async function ({
                                payloads,
                                ...rest
                            }: Parameters<Web3MobileWallet['signMessages']>[0]) {
                                const base64EncodedPayloads = payloads.map(fromUint8Array);
                                const { signed_payloads: base64EncodedSignedMessages } = await wallet.signMessages({
                                    ...rest,
                                    payloads: base64EncodedPayloads,
                                });
                                const signedMessages = base64EncodedSignedMessages.map(toUint8Array);
                                return signedMessages;
                            } as Web3MobileWallet[TMethodName];
                            break;
                        case 'signTransactions':
                            target[p] = async function ({
                                transactions,
                                ...rest
                            }: Parameters<Web3MobileWallet['signTransactions']>[0]) {
                                const serializedTransactions = transactions.map((transaction) =>
                                    transaction.serialize({
                                        requireAllSignatures: false,
                                        verifySignatures: false,
                                    }),
                                );
                                const payloads = serializedTransactions.map((serializedTransaction) =>
                                    serializedTransaction.toString('base64'),
                                );
                                const { signed_payloads: base64EncodedCompiledTransactions } =
                                    await wallet.signTransactions({
                                        ...rest,
                                        payloads,
                                    });
                                const compiledTransactions = base64EncodedCompiledTransactions.map(toUint8Array);
                                const signedTransactions = compiledTransactions.map(Transaction.from);
                                return signedTransactions;
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
