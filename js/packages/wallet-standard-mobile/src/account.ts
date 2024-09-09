// import {
//     SolanaSignAndSendTransaction,
//     SolanaSignIn,
//     SolanaSignMessage,
//     SolanaSignTransaction,
// } from '@solana/wallet-standard-features';
// import type { WalletAccount } from '@wallet-standard/base';
// import { MWA_SOLANA_CHAINS } from './solana.js';

// export const DEFAULT_CHAINS = MWA_SOLANA_CHAINS;
// export const DEFAULT_FEATURES = [SolanaSignAndSendTransaction, SolanaSignTransaction, SolanaSignMessage, SolanaSignIn] as const;

// export class MobileWalletAccount implements WalletAccount {
//     readonly #address: WalletAccount['address'];
//     readonly #publicKey: WalletAccount['publicKey'];
//     readonly #chains: WalletAccount['chains'];
//     readonly #features: WalletAccount['features'];
//     readonly #label: WalletAccount['label'];
//     readonly #icon: WalletAccount['icon'];

//     get address() {
//         return this.#address;
//     }

//     get publicKey() {
//         return this.#publicKey.slice();
//     }

//     get chains() {
//         return this.#chains.slice();
//     }

//     get features() {
//         return this.#features.slice();
//     }

//     get label() {
//         return this.#label;
//     }

//     get icon() {
//         return this.#icon;
//     }

//     constructor({ address, publicKey, label, icon, chains, features }: WalletAccount) {
//         if (new.target === MobileWalletAccount) {
//             Object.freeze(this);
//         }

//         this.#address = address;
//         this.#publicKey = publicKey;
//         this.#chains = chains;
//         this.#features = features;
//         this.#label = label;
//         this.#icon = icon;
//     }
// }