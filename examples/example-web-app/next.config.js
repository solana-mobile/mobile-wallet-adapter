/* eslint-disable @typescript-eslint/no-var-requires */
const { PHASE_PRODUCTION_BUILD } = require('next/constants');

module.exports = (phase, { defaultConfig }) => {
  /**
   * @type {import('next').NextConfig}
   */
  const nextConfig = {
    reactStrictMode: true,
    output: "export",
    basePath: phase === PHASE_PRODUCTION_BUILD ? '/mobile-wallet-adapter/example-web-app' : '',
  }
  return nextConfig
}
