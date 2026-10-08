// The remote scenario pairs a web dapp with a wallet on another device over a reflector. It has no
// React Native implementation: `@solana-mobile/mobile-wallet-adapter-protocol` only ships `transact`
// in its React Native build, so this build exports nothing here rather than importing a symbol that
// does not exist on that platform.
export {};
