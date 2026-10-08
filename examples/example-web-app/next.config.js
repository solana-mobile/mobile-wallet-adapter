const path = require('path');

const { PHASE_PRODUCTION_BUILD } = require('next/constants');

module.exports = (phase) => {
    /**
     * @type {import('next').NextConfig}
     */
    const nextConfig = {
        basePath: phase === PHASE_PRODUCTION_BUILD ? '/mobile-wallet-adapter/example-web-app' : '',
        output: 'export',
        reactStrictMode: true,
        turbopack: {
            // The @solana-mobile/* packages are `link:` dependencies that live in `js/`, outside this
            // app directory. Turbopack only resolves modules under its root, so point it at the repo root.
            root: path.join(__dirname, '..', '..'),
        },
    };
    return nextConfig;
};
