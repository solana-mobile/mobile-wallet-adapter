import { getWallets } from '@wallet-standard/app';
import type { Wallet } from '@wallet-standard/base';
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { describe, expect, it } from 'vitest';

import { useWallets } from './useWallets.js';

(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

const wallet = {
    version: '1.0.0',
    name: 'Test Wallet',
    icon: 'data:image/svg+xml;base64,',
    chains: [],
    accounts: [],
    features: {},
} as unknown as Wallet;

describe('useWallets', () => {
    it('sees a wallet registered before the first render, and its removal while mounted', async () => {
        const unregister = getWallets().register(wallet);
        const renders: string[][] = [];
        function Probe() {
            renders.push(useWallets().map((w) => w.name));
            return null;
        }
        const root = createRoot(document.createElement('div'));

        await act(async () => root.render(<Probe />));
        expect(renders[0]).toContain('Test Wallet');

        await act(async () => unregister());
        expect(renders.at(-1)).not.toContain('Test Wallet');

        act(() => root.unmount());
    });
});
