import { SOLANA_DEVNET_CHAIN, SOLANA_MAINNET_CHAIN, SOLANA_TESTNET_CHAIN } from '@solana/wallet-standard-chains';
import { describe, expect, it } from 'vitest';

import { isVersionedTransaction, MWA_SOLANA_CHAINS } from '../src/solana.js';

describe('solana helpers', () => {
    it('exports the supported Solana chains in the expected order', () => {
        expect(MWA_SOLANA_CHAINS).toEqual([SOLANA_MAINNET_CHAIN, SOLANA_DEVNET_CHAIN, SOLANA_TESTNET_CHAIN]);
    });

    it('detects versioned transactions by the presence of a version field', () => {
        expect(isVersionedTransaction({ signatures: [] })).toBe(false);
        expect(isVersionedTransaction({ signatures: [], version: 0 })).toBe(true);
    });
});
