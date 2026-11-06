import type {
    SolanaSignAndSendTransactionFeature,
    SolanaSignInFeature,
    SolanaSignInInput,
    SolanaSignMessageFeature,
    SolanaSignTransactionFeature,
} from '@solana/wallet-standard-features';
import {
    SolanaSignAndSendTransaction,
    SolanaSignIn,
    SolanaSignMessage,
    SolanaSignTransaction,
} from '@solana/wallet-standard-features';
import { verifySignIn } from '@solana/wallet-standard-util';
import {
    SolanaMobileWalletAdapterRemoteWalletName,
    SolanaMobileWalletAdapterWalletName,
} from '@solana-mobile/wallet-standard-mobile';
import type { WalletAccount } from '@wallet-standard/base';
import type {
    StandardConnectFeature,
    StandardDisconnectFeature,
    StandardEventsFeature,
} from '@wallet-standard/features';
import { StandardConnect, StandardDisconnect, StandardEvents } from '@wallet-standard/features';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { base58Encode } from './base58.js';
import { CHAIN, config, EXPLORER_CLUSTER } from './config.js';
import { describeError, walletCall, WalletError } from './errors.js';
import { buildMemoTransaction, type TransactionVersion } from './memoTransaction.js';
import {
    confirmSignature,
    getBalance,
    getLatestBlockhash,
    LAMPORTS_PER_SOL,
    requestAirdrop,
    sendTransaction,
} from './rpc.js';
import { useWallets } from './useWallets.js';

// Any Solana wallet can connect; the rest of its features are optional, as with wallet-adapter.
type WalletFeatures = StandardConnectFeature &
    Partial<
        StandardDisconnectFeature &
            StandardEventsFeature &
            SolanaSignMessageFeature &
            SolanaSignInFeature &
            SolanaSignTransactionFeature &
            SolanaSignAndSendTransactionFeature
    >;

const MWA_WALLET_NAMES: readonly string[] = [
    SolanaMobileWalletAdapterWalletName,
    SolanaMobileWalletAdapterRemoteWalletName,
];
const AIRDROP_LAMPORTS = LAMPORTS_PER_SOL / 10;

interface LogEntry {
    id: number;
    ok: boolean;
    text: string;
    href?: string;
}

interface Outcome {
    text: string;
    href?: string;
}

/** The Solana Explorer link for a devnet transaction. */
const explorerTxUrl = (signature: string) => `https://explorer.solana.com/tx/${signature}?cluster=${EXPLORER_CLUSTER}`;

/** Lamports as SOL, with up to 4 decimals. */
const formatSol = (lamports: number) =>
    new Intl.NumberFormat(undefined, { maximumFractionDigits: 4 }).format(lamports / LAMPORTS_PER_SOL);

/** The example dapp: wallet selection, the account, and the Mobile Wallet Adapter actions. */
export function App() {
    const wallets = useWallets();
    // Every registered Solana wallet that can connect, Mobile Wallet Adapter first.
    const supportedWallets = useMemo(
        () =>
            wallets
                .filter((w) => StandardConnect in w.features && w.chains.some((chain) => chain.startsWith('solana:')))
                .sort((a, b) => Number(MWA_WALLET_NAMES.includes(b.name)) - Number(MWA_WALLET_NAMES.includes(a.name))),
        [wallets],
    );
    const [selectedWalletName, setSelectedWalletName] = useState<string>();
    const wallet = supportedWallets.find((w) => w.name === selectedWalletName) ?? supportedWallets[0];
    const features = wallet?.features as WalletFeatures | undefined;

    const [accounts, setAccounts] = useState<readonly WalletAccount[]>([]);
    const [balance, setBalance] = useState<{ address: string; lamports: number }>();
    const [busy, setBusy] = useState<string>();
    const [log, setLog] = useState<LogEntry[]>([]);
    // The latest outcome, shown where the user can see it, like the old app's snackbar.
    const [toast, setToast] = useState<LogEntry>();
    const nextLogId = useRef(0);
    const [message, setMessage] = useState('');

    const account = accounts[0];

    /** Adds an outcome to the activity log and shows it as the toast. */
    const append = useCallback((entry: Omit<LogEntry, 'id'>) => {
        const logEntry = { ...entry, id: ++nextLogId.current };
        setLog((current) => [logEntry, ...current]);
        setToast(logEntry);
    }, []);

    useEffect(() => {
        if (!toast) return;
        const timeout = setTimeout(() => setToast(undefined), 10_000);
        return () => clearTimeout(timeout);
    }, [toast]);

    // Bumped after actions that move funds, to refetch the balance.
    const [balanceVersion, setBalanceVersion] = useState(0);
    /** Refetches the balance, after actions that move funds. */
    const refreshBalance = useCallback(() => setBalanceVersion((version) => version + 1), []);
    useEffect(() => {
        if (!account) return;
        let cancelled = false;
        getBalance(config.rpcUrl, account.address).then(
            (lamports) => {
                if (!cancelled) setBalance({ address: account.address, lamports });
            },
            () => undefined,
        );
        return () => {
            cancelled = true;
        };
    }, [account, balanceVersion]);
    // Refetch when the user comes back (e.g. from the wallet app) or the network returns. A failed fetch
    // keeps showing the last known balance.
    useEffect(() => {
        window.addEventListener('focus', refreshBalance);
        window.addEventListener('online', refreshBalance);
        return () => {
            window.removeEventListener('focus', refreshBalance);
            window.removeEventListener('online', refreshBalance);
        };
    }, [refreshBalance]);
    // Keyed on the wallet for the same reason as the restore below: `features` is a new object on every read.
    useEffect(() => {
        if (!wallet) return;
        return (wallet.features as WalletFeatures)[StandardEvents]?.on('change', (properties) => {
            if (properties.accounts) setAccounts(properties.accounts);
        });
    }, [wallet]);

    // Restore a cached session on load. Only the on-device wallet supports a silent connect;
    // the remote wallet would open its QR code dialog instead.
    // Keyed on the wallet, not `features`: the wallet's `features` getter returns a new object on every
    // read, so depending on it re-ran this effect after every render and looped forever on Android.
    useEffect(() => {
        if (wallet?.name !== SolanaMobileWalletAdapterWalletName) return;
        (wallet.features as WalletFeatures)[StandardConnect].connect({ silent: true })
            .then(({ accounts: restored }) => {
                if (!restored.length) return;
                setAccounts(restored);
                append({ ok: true, text: 'Restored the previous session' });
            })
            .catch((error: unknown) =>
                append({
                    ok: false,
                    text: `Restore session ✗ ${describeError(new WalletError('WalletConnectionError', '', error))}`,
                }),
            );
    }, [wallet, append]);

    const run = useCallback(
        (
            label: string,
            action: () => Promise<Outcome>,
            // The old app's wording for a failure, where it had one.
            failureText = (description: string) => `${label} ✗ ${description}`,
        ) => {
            if (busy) return;
            setBusy(label);
            action()
                .then(({ text, href }) => append({ ok: true, text, href }))
                .catch((error: unknown) => append({ ok: false, text: failureText(describeError(error)) }))
                .finally(() => setBusy(undefined));
        },
        [busy, append],
    );

    if (!wallet || !features) {
        return (
            <main>
                <Header />
                <section className="card">
                    <p className="status" data-testid="wallet-status">
                        No Solana wallet was found. On Android, open this page in Chrome with a Mobile Wallet Adapter
                        wallet installed. On a desktop browser, configure a relay for Mobile Wallet Adapter or install a
                        wallet extension.
                    </p>
                </section>
            </main>
        );
    }

    /** Whether both the wallet and the account support `name`, as wallet-adapter checks before each call. */
    const supports = (name: keyof WalletFeatures, current: WalletAccount | undefined) =>
        !!features[name] && !!current?.features.includes(name);

    const requireFeature = <T,>(feature: T | undefined, name: keyof WalletFeatures, current: WalletAccount): T => {
        if (!feature) throw new WalletError('WalletConfigError', `${wallet.name} does not support ${name}`);
        if (!current.features.includes(name)) {
            throw new WalletError('WalletAccountError', `${current.address} does not support ${name}`);
        }
        return feature;
    };

    /** The memo transaction for `feePayer`: v0 when the wallet supports it, legacy otherwise, like the old app. */
    const memoTransaction = (
        feePayer: WalletAccount,
        { blockhash }: { blockhash: Uint8Array },
        supportedVersions: readonly TransactionVersion[],
    ) =>
        buildMemoTransaction(
            feePayer.publicKey as Uint8Array,
            blockhash,
            message || 'Hello from the Mobile Wallet Adapter example',
            supportedVersions.includes(0) ? 0 : 'legacy',
        );

    /** Connects the selected wallet; fails if it returns no accounts. */
    const connect = () =>
        run('Connect', async () => {
            const { accounts: connected } = await walletCall('WalletConnectionError', () =>
                features[StandardConnect].connect(),
            );
            if (!connected[0]) throw new WalletError('WalletAccountError', `${wallet.name} returned no accounts`);
            setAccounts(connected);
            return { text: `Connect ✓ ${connected[0].address}` };
        });

    /** Signs in with Solana, then verifies the signed message and its signature. */
    const signIn = () =>
        run('Sign in', async () => {
            const input: SolanaSignInInput = {
                domain: window.location.host,
                statement: 'Sign in to the Mobile Wallet Adapter example',
                uri: window.location.origin,
            };
            const signInFeature = features[SolanaSignIn];
            if (!signInFeature) throw new Error('Sign In not available, wallet does not support sign in');
            const [output] = await walletCall('WalletSignInError', () => signInFeature.signIn(input));
            if (!output) throw new WalletError('WalletSignInError', `${wallet.name} returned no sign-in result`);
            if (!verifySignIn(input, output)) throw new Error('Sign In verification failed!');
            setAccounts([output.account]);
            return { text: `Sign in ✓ verified ${output.account.address}` };
        });

    /** Disconnects; the account is cleared even if the wallet fails to disconnect. */
    const disconnect = () =>
        run('Disconnect', async () => {
            try {
                await walletCall('WalletDisconnectionError', async () => features[StandardDisconnect]?.disconnect());
            } finally {
                // Even if the wallet failed to disconnect, the app is done with this account.
                setAccounts([]);
            }
            return { text: 'Disconnect ✓ done' };
        });

    /** Airdrops 0.1 devnet SOL to the account and waits for confirmation. */
    const fundAccount = (current: WalletAccount) =>
        run(
            'Fund account',
            async () => {
                const signature = await requestAirdrop(config.rpcUrl, current.address, AIRDROP_LAMPORTS);
                await confirmSignature(config.rpcUrl, signature);
                refreshBalance();
                return { text: 'Funding successful', href: explorerTxUrl(signature) };
            },
            (description) => `Failed to fund account: ${description}`,
        );

    /** Records the message on chain: signed and sent by the wallet, or signed by it and sent over RPC. */
    const recordMessage = (current: WalletAccount) =>
        run(
            'Record message',
            async () => {
                const latest = await getLatestBlockhash(config.rpcUrl);
                const options = { preflightCommitment: 'confirmed' as const, minContextSlot: latest.slot };
                let signature: string;
                if (supports(SolanaSignAndSendTransaction, current)) {
                    const signAndSend = features[SolanaSignAndSendTransaction]!;
                    const [output] = await walletCall('WalletSendTransactionError', () =>
                        signAndSend.signAndSendTransaction({
                            account: current,
                            chain: CHAIN,
                            transaction: memoTransaction(current, latest, signAndSend.supportedTransactionVersions),
                            options,
                        }),
                    );
                    signature = base58Encode(output!.signature);
                } else {
                    const sign = requireFeature(features[SolanaSignTransaction], SolanaSignTransaction, current);
                    const [output] = await walletCall('WalletSendTransactionError', () =>
                        sign.signTransaction({
                            account: current,
                            chain: CHAIN,
                            transaction: memoTransaction(current, latest, sign.supportedTransactionVersions),
                            options,
                        }),
                    );
                    signature = await sendTransaction(config.rpcUrl, output!.signedTransaction, latest.slot);
                }
                await confirmSignature(config.rpcUrl, signature, { lastValidBlockHeight: latest.lastValidBlockHeight });
                refreshBalance();
                return { text: 'Message recorded', href: explorerTxUrl(signature) };
            },
            (description) => `Failed to record message: ${description}`,
        );

    /** Signs the memo transaction without sending it and shows the fee payer signature. */
    const signTransaction = (current: WalletAccount) =>
        run(
            'Sign transaction',
            async () => {
                const sign = requireFeature(features[SolanaSignTransaction], SolanaSignTransaction, current);
                const latest = await getLatestBlockhash(config.rpcUrl);
                const [output] = await walletCall('WalletSignTransactionError', () =>
                    sign.signTransaction({
                        account: current,
                        chain: CHAIN,
                        transaction: memoTransaction(current, latest, sign.supportedTransactionVersions),
                    }),
                );
                // The fee payer's signature is the first 64 bytes after the one-byte signature count.
                return { text: `Transaction signed: ${base58Encode(output!.signedTransaction.slice(1, 65))}` };
            },
            // Wallet errors read `Name: message`, as the old app's wallet error handler showed them.
            (description) => description,
        );

    /** Signs the message text off-chain. */
    const signMessage = (current: WalletAccount) =>
        run(
            'Sign message',
            async () => {
                const sign = requireFeature(features[SolanaSignMessage], SolanaSignMessage, current);
                const [output] = await walletCall('WalletSignMessageError', () =>
                    sign.signMessage({ account: current, message: new TextEncoder().encode(message) }),
                );
                return { text: `Message signed: ${base58Encode(output!.signature)}` };
            },
            (description) => description,
        );

    return (
        <main>
            <Header />

            <p className="status" data-testid="wallet-status">
                {wallet.name} · {CHAIN}
            </p>

            {!account ? (
                <section className="card">
                    <h2>Connect a wallet</h2>
                    <p className="hint">
                        Authorize this app with a wallet to sign messages and transactions on devnet.
                    </p>
                    {supportedWallets.length > 1 && (
                        <label className="wallet-select">
                            Wallet
                            <select
                                data-testid="wallet-select"
                                disabled={!!busy}
                                value={wallet.name}
                                onChange={(event) => setSelectedWalletName(event.target.value)}
                            >
                                {supportedWallets.map((w) => (
                                    <option key={w.name} value={w.name}>
                                        {w.name}
                                    </option>
                                ))}
                            </select>
                        </label>
                    )}
                    <div className="row">
                        <button className="primary" data-testid="connect" disabled={!!busy} onClick={connect}>
                            {busy === 'Connect' ? 'Connecting…' : 'Connect'}
                        </button>
                        <button data-testid="sign-in" disabled={!!busy || !features[SolanaSignIn]} onClick={signIn}>
                            {busy === 'Sign in' ? 'Signing in…' : 'Sign in with Solana'}
                        </button>
                    </div>
                </section>
            ) : (
                <>
                    <section className="card">
                        <div className="account">
                            <div>
                                <h2>Account</h2>
                                <a
                                    className="address"
                                    href={`https://explorer.solana.com/address/${account.address}?cluster=${EXPLORER_CLUSTER}`}
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    <code data-testid="account-address">{account.address}</code>
                                </a>
                            </div>
                            <p className="balance" data-testid="balance">
                                ◎{balance?.address === account.address ? formatSol(balance.lamports) : '–'}
                            </p>
                        </div>
                        <div className="row">
                            <button data-testid="fund-account" disabled={!!busy} onClick={() => fundAccount(account)}>
                                {busy === 'Fund account' ? 'Funding…' : 'Fund account'}
                            </button>
                            <button className="quiet" data-testid="disconnect" disabled={!!busy} onClick={disconnect}>
                                Disconnect
                            </button>
                        </div>
                    </section>

                    <section className="card">
                        <h2>
                            <label htmlFor="message">Message</label>
                        </h2>
                        <textarea
                            id="message"
                            data-testid="message"
                            placeholder="What's on your mind?"
                            rows={3}
                            value={message}
                            onChange={(event) => setMessage(event.target.value)}
                        />
                        <ul className="actions">
                            <Action
                                label="Record message"
                                buttonText="Record"
                                busy={busy}
                                disabled={
                                    !message ||
                                    !(
                                        supports(SolanaSignAndSendTransaction, account) ||
                                        supports(SolanaSignTransaction, account)
                                    )
                                }
                                onClick={() => recordMessage(account)}
                                testId="record-message"
                            >
                                Signs and sends a transaction that writes the message on-chain with the Memo program.
                            </Action>
                            <Action
                                label="Sign transaction"
                                buttonText="Sign"
                                busy={busy}
                                disabled={!message || !supports(SolanaSignTransaction, account)}
                                onClick={() => signTransaction(account)}
                                testId="sign-transaction"
                            >
                                Asks the wallet to sign the same memo transaction without sending it.
                            </Action>
                            <Action
                                label="Sign message"
                                buttonText="Sign"
                                busy={busy}
                                disabled={!message || !supports(SolanaSignMessage, account)}
                                onClick={() => signMessage(account)}
                                testId="sign-message"
                            >
                                Signs the message text off-chain. Nothing is sent to the network.
                            </Action>
                        </ul>
                    </section>
                </>
            )}

            <section className="card">
                <h2>Activity</h2>
                <ol className="log" data-testid="log">
                    {log.length === 0 ? (
                        <li className="empty">No activity yet.</li>
                    ) : (
                        log.map((entry) => (
                            <li key={entry.id} className={entry.ok ? 'ok' : 'error'}>
                                <span>{entry.text}</span>
                                {entry.href && (
                                    <a href={entry.href} target="_blank" rel="noreferrer">
                                        View on explorer
                                    </a>
                                )}
                            </li>
                        ))
                    )}
                </ol>
            </section>

            {toast && (
                <div
                    className={`toast ${toast.ok ? 'ok' : 'error'}`}
                    data-testid="toast"
                    role={toast.ok ? 'status' : 'alert'}
                >
                    <span>{toast.text}</span>
                    {toast.href && (
                        <a href={toast.href} target="_blank" rel="noreferrer">
                            View on explorer
                        </a>
                    )}
                    <button className="quiet" aria-label="Dismiss" onClick={() => setToast(undefined)}>
                        ×
                    </button>
                </div>
            )}

            <footer>
                <a href="https://github.com/solana-mobile/mobile-wallet-adapter" target="_blank" rel="noreferrer">
                    Source on GitHub
                </a>
                <a href="https://docs.solanamobile.com" target="_blank" rel="noreferrer">
                    Solana Mobile docs
                </a>
            </footer>
        </main>
    );
}

/** The page header. */
function Header() {
    return (
        <header>
            <h1>Mobile Wallet Adapter</h1>
            <p className="subtitle">Example web dapp</p>
        </header>
    );
}

/** One message action: what it does, and its button. */
function Action({
    busy,
    buttonText,
    children,
    disabled,
    label,
    onClick,
    testId,
}: {
    busy: string | undefined;
    buttonText: string;
    children: string;
    disabled: boolean;
    label: string;
    onClick: () => void;
    testId: string;
}) {
    return (
        <li>
            <div>
                <p className="action-title">{label}</p>
                <p className="hint">{children}</p>
            </div>
            <button aria-label={label} data-testid={testId} disabled={!!busy || disabled} onClick={onClick}>
                {busy === label ? 'Working…' : buttonText}
            </button>
        </li>
    );
}
