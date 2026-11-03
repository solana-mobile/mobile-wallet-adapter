import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

/**
 * E2E only (MWA_E2E=1): records each remote association URL on `window.__mwaE2eAssociationUrls`
 * as the SDK hands it to its QR code dialog. The e2e fake wallet has no camera, so this is how it
 * learns the session parameters a real wallet would scan.
 */
function e2eAssociationCapture(): Plugin {
    const callSite = 'modal.populateQRCode(associationUrl.toString())';
    return {
        name: 'mwa-e2e-association-capture',
        apply: 'serve',
        transform(code, id) {
            if (!process.env.MWA_E2E || !id.includes('wallet-standard-mobile')) return;
            if (!code.includes(callSite)) return;
            return code.replace(
                callSite,
                `((window.__mwaE2eAssociationUrls ??= []).push(associationUrl.toString()), ${callSite})`,
            );
        },
    };
}

export default defineConfig(({ command }) => ({
    // Published to GitHub Pages under the repository path.
    base: command === 'build' ? '/mobile-wallet-adapter/example-web-app/' : '/',
    build: { outDir: 'out' },
    // Pre-bundled dependencies skip plugin transforms, and the e2e capture must see the SDK source.
    optimizeDeps: { exclude: ['@solana-mobile/wallet-standard-mobile'] },
    plugins: [react(), e2eAssociationCapture()],
    server: {
        port: 3002,
        // `pnpm tunnel:start` serves the dev server to a phone over https through ngrok.
        allowedHosts: ['.ngrok.io', '.ngrok-free.app', '.ngrok.app', '.ngrok.dev'],
    },
    test: {
        environment: 'happy-dom',
        include: ['src/**/*.test.{ts,tsx}'],
    },
}));
