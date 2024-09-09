import { Base64EncodedAddress } from "@solana-mobile/mobile-wallet-adapter-protocol";
import { 
    SOLANA_DEVNET_CHAIN, 
    SOLANA_MAINNET_CHAIN, 
    SOLANA_TESTNET_CHAIN 
} from "@solana/wallet-standard-chains";
import { PublicKey, Transaction, VersionedTransaction } from "@solana/web3.js";
import { toUint8Array } from "./base64Utils";

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

export function getPublicKeyFromAddress(address: Base64EncodedAddress): PublicKey {
    const publicKeyByteArray = toUint8Array(address);
    return new PublicKey(publicKeyByteArray);
}