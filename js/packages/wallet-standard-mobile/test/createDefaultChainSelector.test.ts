import { SOLANA_DEVNET_CHAIN, SOLANA_MAINNET_CHAIN, SOLANA_TESTNET_CHAIN } from '@solana/wallet-standard-chains';
import { describe, expect, it } from 'vitest';

import createDefaultChainSelector from '../src/createDefaultChainSelector.js';

describe('createDefaultChainSelector', () => {
    it('falls back to the first chain when mainnet is absent', async () => {
        const selector = createDefaultChainSelector();

        await expect(selector.select([SOLANA_DEVNET_CHAIN, SOLANA_TESTNET_CHAIN])).resolves.toBe(SOLANA_DEVNET_CHAIN);
    });

    it('prefers mainnet when it is available', async () => {
        const selector = createDefaultChainSelector();

        await expect(selector.select([SOLANA_DEVNET_CHAIN, SOLANA_MAINNET_CHAIN])).resolves.toBe(SOLANA_MAINNET_CHAIN);
    });

    it('returns the only available chain', async () => {
        const selector = createDefaultChainSelector();

        await expect(selector.select([SOLANA_DEVNET_CHAIN])).resolves.toBe(SOLANA_DEVNET_CHAIN);
    });
});
