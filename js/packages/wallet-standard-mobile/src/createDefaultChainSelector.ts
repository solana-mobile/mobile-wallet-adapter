import { SOLANA_MAINNET_CHAIN } from "@solana/wallet-standard-chains";
import { ChainSelector } from "./wallet";

export default function createDefaultChainSelector(): ChainSelector {
    return {
        async select(chains) {
            if (chains.length === 1) { return chains[0]; }
            else if (chains.includes(SOLANA_MAINNET_CHAIN)) { return SOLANA_MAINNET_CHAIN; }
            else return chains[0];
        },
    };
}