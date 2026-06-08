import { clusterApiUrl, Connection, SendOptions, TransactionSignature, VersionedTransaction } from '@solana/web3.js';
import { base58ToUint8Array } from '@solana-mobile/mobile-wallet-adapter-protocol/encoding';

type SolanaCluster = Parameters<typeof clusterApiUrl>[0];

const MWA_CHAIN_CLUSTER: Record<string, SolanaCluster> = {
    'solana:devnet': 'devnet',
    'solana:mainnet': 'mainnet-beta',
    'solana:mainnet-beta': 'mainnet-beta',
    'solana:testnet': 'testnet',
};

export class SendTransactionsError extends Error {
    valid: boolean[];
    constructor(message: string, valid: boolean[]) {
        super(message);
        this.name = 'SendTransactionErrors';
        this.valid = valid;
    }
}

export class SendTransactionsUseCase {
    static readonly SIGNATURE_LEN = 64;
    static readonly PUBLIC_KEY_LEN = 32;

    static async sendSignedTransactions(
        signedTransactions: Array<Uint8Array>,
        minContextSlot: number | undefined,
        chain: string,
    ): Promise<Uint8Array[]> {
        const cluster = MWA_CHAIN_CLUSTER[chain] ?? 'testnet';
        const connection = new Connection(clusterApiUrl(cluster), 'finalized');
        const signatures: (Uint8Array | null)[] = await Promise.all(
            signedTransactions.map(async (byteArray) => {
                // Try sending a transaction.
                try {
                    const transaction: VersionedTransaction = VersionedTransaction.deserialize(byteArray);

                    const sendOptions: SendOptions = {
                        minContextSlot: minContextSlot,
                        preflightCommitment: 'finalized',
                    };
                    const signature: TransactionSignature = await connection.sendTransaction(transaction, sendOptions);
                    const decoded = base58ToUint8Array(signature);
                    return decoded;
                } catch (error) {
                    console.log('Failed sending transaction ' + error);
                    return null;
                }
            }),
        );

        if (signatures.includes(null)) {
            const valid = signatures.map((signature) => {
                return signature !== null;
            });
            throw new SendTransactionsError('Failed sending transactions', valid);
        }

        return signatures as Uint8Array[];
    }
}
