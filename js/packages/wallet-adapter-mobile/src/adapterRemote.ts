import { ed25519 } from '@noble/curves/ed25519';
import type { WalletName } from '@solana/wallet-adapter-base';
import {
    BaseSignInMessageSignerWalletAdapter,
    isVersionedTransaction,
    WalletNotConnectedError,
    WalletReadyState,
} from '@solana/wallet-adapter-base';
import { type SolanaSignInInput, type SolanaSignInOutput } from '@solana/wallet-standard-features';
import { createSignInMessage } from '@solana/wallet-standard-util';
import type { Transaction, TransactionVersion, VersionedTransaction } from '@solana/web3.js';
import { Keypair } from '@solana/web3.js';

import { EmbeddedDialogModal } from './embedded-dialog/embeddedModalDialog.js';

export const SolanaMobileWalletAdapterRemoteWalletName = 'MWA (Remote)' as WalletName<'MWA (Remote)'>;

/**
 * This burner wallet adapter is unsafe to use and is only included to provide an easy way for applications to test
 * Wallet Adapter without using a third-party wallet.
 */
export class SolanaMobileWalletAdapterRemote extends BaseSignInMessageSignerWalletAdapter {
    name = SolanaMobileWalletAdapterRemoteWalletName;
    url = 'https://solanamobile.com/wallets';
    icon =
        'data:image/svg+xml;base64,PHN2ZyBmaWxsPSJub25lIiBoZWlnaHQ9IjI4IiB3aWR0aD0iMjgiIHZpZXdCb3g9Ii0zIDAgMjggMjgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PGcgZmlsbD0iI0RDQjhGRiI+PHBhdGggZD0iTTE3LjQgMTcuNEgxNXYyLjRoMi40di0yLjRabTEuMi05LjZoLTIuNHYyLjRoMi40VjcuOFoiLz48cGF0aCBkPSJNMjEuNiAzVjBoLTIuNHYzaC0zLjZWMGgtMi40djNoLTIuNHY2LjZINC41YTIuMSAyLjEgMCAxIDEgMC00LjJoMi43VjNINC41QTQuNSA0LjUgMCAwIDAgMCA3LjVWMjRoMjEuNnYtNi42aC0yLjR2NC4ySDIuNFYxMS41Yy41LjMgMS4yLjQgMS44LjVoNy41QTYuNiA2LjYgMCAwIDAgMjQgOVYzaC0yLjRabTAgNS43YTQuMiA0LjIgMCAxIDEtOC40IDBWNS40aDguNHYzLjNaIi8+PC9nPjwvc3ZnPg==';
    supportedTransactionVersions: ReadonlySet<TransactionVersion> = new Set(['legacy', 0]);

    /**
     * Storing a keypair locally like this is not safe because any application using this adapter could retrieve the
     * secret key, and because the keypair will be lost any time the wallet is disconnected or the window is refreshed.
     */
    private _keypair: Keypair | null = null;

    constructor() {
        super();
        console.warn(
            'Your application is presently configured to use the `UnsafeBurnerWalletAdapter`. ' +
                'Find and remove it, then replace it with a list of adapters for ' +
                'wallets you would like your application to support. See ' +
                'https://github.com/anza-xyz/wallet-adapter#usage for an example.',
        );
    }

    get connecting() {
        return false;
    }

    get publicKey() {
        return this._keypair && this._keypair.publicKey;
    }

    get readyState() {
        return WalletReadyState.Loadable;
    }

    async autoConnect(): Promise<void> {
        return;
    }

    async connect(): Promise<void> {
        this._keypair = new Keypair();
        const modal = new EmbeddedDialogModal('title');
        modal.init();
        // this.emit('connect', this._keypair.publicKey);
    }

    async disconnect(): Promise<void> {
        this._keypair = null;
        this.emit('disconnect');
    }

    async signTransaction<T extends Transaction | VersionedTransaction>(transaction: T): Promise<T> {
        if (!this._keypair) throw new WalletNotConnectedError();

        if (isVersionedTransaction(transaction)) {
            transaction.sign([this._keypair]);
        } else {
            transaction.partialSign(this._keypair);
        }

        return transaction;
    }

    async signMessage(message: Uint8Array): Promise<Uint8Array> {
        if (!this._keypair) throw new WalletNotConnectedError();

        return ed25519.sign(message, this._keypair.secretKey.slice(0, 32));
    }

    async signIn(input: SolanaSignInInput = {}): Promise<SolanaSignInOutput> {
        const { publicKey, secretKey } = (this._keypair ||= new Keypair());
        const domain = input.domain || window.location.host;
        const address = input.address || publicKey.toBase58();

        const signedMessage = createSignInMessage({
            ...input,
            domain,
            address,
        });
        const signature = ed25519.sign(signedMessage, secretKey.slice(0, 32));

        this.emit('connect', publicKey);

        return {
            account: {
                address,
                publicKey: publicKey.toBytes(),
                chains: [],
                features: [],
            },
            signedMessage,
            signature,
        };
    }
}
