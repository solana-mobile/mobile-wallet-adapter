import { 
    SOLANA_DEVNET_CHAIN, 
    SOLANA_MAINNET_CHAIN, 
    SOLANA_TESTNET_CHAIN 
} from "@solana/wallet-standard-chains";

/** Array of all supported Solana clusters */
export const MWA_SOLANA_CHAINS = [
    SOLANA_MAINNET_CHAIN,
    SOLANA_DEVNET_CHAIN,
    SOLANA_TESTNET_CHAIN,
] as const;

// Placeholder types to remove dependency on web3.js
type LegacyTransaction = { [key: string]: any }; // Placeholder type
type LegacyVersionedTransaction = LegacyTransaction & { version: number };

/**
 * @deprecated unused method, will be removed before 1.0 release. Do not use!
 */
export function isVersionedTransaction(
    transaction: LegacyTransaction | LegacyVersionedTransaction
): transaction is LegacyVersionedTransaction {
    return 'version' in transaction;
}