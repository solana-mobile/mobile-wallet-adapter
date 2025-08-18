import { 
    type Transaction,
    compileTransaction,
    getBase64EncodedWireTransaction,
    getTransactionDecoder
} from '@solana/transactions';
import { 
    CompilableTransactionMessage, 
} from '@solana/transaction-messages';
import type { SignatureBytes } from '@solana/keys';
import {
    AuthorizeAPI,
    Base64EncodedAddress,
    Base64EncodedTransaction,
    CloneAuthorizationAPI,
    DeauthorizeAPI,
    GetCapabilitiesAPI,
    MobileWallet,
    ReauthorizeAPI,
    TerminateSessionAPI,
    transact as baseTransact,
    startRemoteScenario as baseStartRemoteScenario,
    WalletAssociationConfig,
    RemoteWalletAssociationConfig,
} from '@solana-mobile/mobile-wallet-adapter-protocol';

import { fromUint8Array, toUint8Array } from './base64Utils.js';

interface KitSignAndSendTransactionsAPI {
    signAndSendTransactions<T extends Transaction | CompilableTransactionMessage >(params: {
        minContextSlot?: number;
        commitment?: string;
        skipPreflight?: boolean;
        maxRetries?: number;
        waitForCommitmentToSendNextTransaction?: boolean;
        transactions: T[];
    }): Promise<SignatureBytes[]>;
}

interface KitSignTransactionsAPI {
    signTransactions<T extends Transaction>(params: { transactions: T[] }): Promise<T[]>;
}

interface KitSignMessagesAPI {
    signMessages(params: { addresses: Base64EncodedAddress[]; payloads: Uint8Array[] }): Promise<Uint8Array[]>;
}

export interface KitMobileWallet
    extends AuthorizeAPI,
        CloneAuthorizationAPI,
        DeauthorizeAPI,
        GetCapabilitiesAPI,
        ReauthorizeAPI,
        KitSignAndSendTransactionsAPI,
        KitSignTransactionsAPI,
        KitSignMessagesAPI {}

export interface KitRemoteMobileWallet
    extends KitMobileWallet, TerminateSessionAPI {}

export type KitScenario = Readonly<{
    wallet: Promise<KitMobileWallet>;
    close: () => void;
}>;

export type KitRemoteScenario = KitScenario & Readonly<{
    associationUrl: URL;
}>;

function getPayloadFromTransaction(
    transaction: Transaction | CompilableTransactionMessage
): Base64EncodedTransaction {
    if ('messageBytes' in transaction) {
        return getBase64EncodedWireTransaction(transaction);
    } else if ('instructions' in transaction) {
        const compiledTransaction = compileTransaction(transaction);
        return getBase64EncodedWireTransaction(compiledTransaction);
    } else {
        throw new Error('Invalid transaction type');
    }
}

function getTransactionFromWireMessage(byteArray: Uint8Array): Transaction {
    const transactionDecoder = getTransactionDecoder();
    return transactionDecoder.decode(byteArray);
}

export async function transact<TReturn>(
    callback: (wallet: KitMobileWallet) => TReturn,
    config?: WalletAssociationConfig,
): Promise<TReturn> {
    const augmentedCallback: (wallet: MobileWallet) => TReturn = (wallet) => {
        return callback(augmentWalletAPI(wallet));
    };
    return await baseTransact(augmentedCallback, config);
}

export async function startRemoteScenario(
    config: RemoteWalletAssociationConfig,
): Promise<KitRemoteScenario> {
    const { wallet, close, associationUrl } = await baseStartRemoteScenario(config);
    const augmentedPromise = wallet.then((wallet) => {
        return augmentWalletAPI(wallet); 
    });
    return { wallet: augmentedPromise, close, associationUrl };
}

function augmentWalletAPI(wallet: MobileWallet): KitMobileWallet {
    return new Proxy<KitMobileWallet>({} as KitMobileWallet, {
        get<TMethodName extends keyof KitMobileWallet>(target: KitMobileWallet, p: TMethodName) {
            if (target[p] == null) {
                switch (p) {
                    case 'signAndSendTransactions':
                        target[p] = async function ({
                            minContextSlot,
                            commitment,
                            skipPreflight,
                            maxRetries,
                            waitForCommitmentToSendNextTransaction,
                            transactions,
                            ...rest
                        }: Parameters<KitMobileWallet['signAndSendTransactions']>[0]) {
                            const payloads = transactions.map(getPayloadFromTransaction);
                            const options = {
                                min_context_slot: minContextSlot,
                                commitment: commitment,
                                skip_preflight: skipPreflight,
                                max_retries: maxRetries,
                                wait_for_commitment_to_send_next_transaction: waitForCommitmentToSendNextTransaction
                            };
                            const { signatures: base64EncodedSignatures } = await wallet.signAndSendTransactions({
                                ...rest,
                                ...(Object.values(options).some(element => element != null) 
                                    ? { options: options } 
                                    : null),
                                payloads,
                            });
                            const signatures = base64EncodedSignatures.map(toUint8Array);
                            return signatures as SignatureBytes[];
                        } as KitMobileWallet[TMethodName];
                        break;
                    case 'signMessages':
                        target[p] = async function ({
                            payloads,
                            ...rest
                        }: Parameters<KitMobileWallet['signMessages']>[0]) {
                            const base64EncodedPayloads = payloads.map(fromUint8Array);
                            const { signed_payloads: base64EncodedSignedMessages } = await wallet.signMessages({
                                ...rest,
                                payloads: base64EncodedPayloads,
                            });
                            const signedMessages = base64EncodedSignedMessages.map(toUint8Array);
                            return signedMessages;
                        } as KitMobileWallet[TMethodName];
                        break;
                    case 'signTransactions':
                        target[p] = async function ({
                            transactions,
                            ...rest
                        }: Parameters<KitMobileWallet['signTransactions']>[0]) {
                            const payloads = transactions.map(getPayloadFromTransaction);
                            const { signed_payloads: base64EncodedCompiledTransactions } =
                                await wallet.signTransactions({
                                    ...rest,
                                    payloads,
                                });
                            const compiledTransactions = base64EncodedCompiledTransactions.map(toUint8Array);
                            const signedTransactions = compiledTransactions.map(getTransactionFromWireMessage);
                            return signedTransactions;
                        } as KitMobileWallet[TMethodName];
                        break;
                    default: {
                        target[p] = wallet[p] as unknown as KitMobileWallet[TMethodName];
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
}