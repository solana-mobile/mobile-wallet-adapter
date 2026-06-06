import { defineConfig } from 'vitest/config';

export default defineConfig({
    test: {
        environment: 'node',
        hookTimeout: 120000,
        include: ['packages/mobile-wallet-adapter-protocol/test/nostrRelay.integration.ts'],
        testTimeout: 30000,
    },
});
