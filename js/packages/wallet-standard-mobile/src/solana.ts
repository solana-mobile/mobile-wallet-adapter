import { 
    SOLANA_DEVNET_CHAIN, 
    SOLANA_MAINNET_CHAIN, 
    SOLANA_TESTNET_CHAIN 
} from "@solana/wallet-standard-chains";
import { Transaction, VersionedTransaction } from "@solana/web3.js";

/** Array of all supported Solana clusters */
export const MWA_SOLANA_CHAINS = [
    SOLANA_MAINNET_CHAIN,
    SOLANA_DEVNET_CHAIN,
    SOLANA_TESTNET_CHAIN,
] as const;

export function isVersionedTransaction(
    transaction: Transaction | VersionedTransaction
): transaction is VersionedTransaction {
    return 'version' in transaction;
}