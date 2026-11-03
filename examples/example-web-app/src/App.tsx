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
import { useCallback, useEffect, useMemo, useState } from 'react';

import { base58Encode } from './base58.js';
import { CHAIN, config, EXPLORER_CLUSTER } from './config.js';
import { buildMemoTransaction } from './memoTransaction.js';
import { confirmSignature, getBalance, getLatestBlockhash, LAMPORTS_PER_SOL, requestAirdrop } from './rpc.js';
import { useWallets } from './useWallets.js';

type MwaFeatures = StandardConnectFeature &
    StandardDisconnectFeature &
    StandardEventsFeature &
    SolanaSignMessageFeature &
    SolanaSignInFeature &
    SolanaSignTransactionFeature &
    SolanaSignAndSendTransactionFeature;

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

const explorerTxUrl = (signature: string) => `https://explorer.solana.com/tx/${signature}?cluster=${EXPLORER_CLUSTER}`;

const formatSol = (lamports: number) =>
    new Intl.NumberFormat(undefined, { maximumFractionDigits: 4 }).format(lamports / LAMPORTS_PER_SOL);

export function App() {
    const wallets = useWallets();
    const wallet = useMemo(() => wallets.find((w) => MWA_WALLET_NAMES.includes(w.name)), [wallets]);
    const features = wallet?.features as MwaFeatures | undefined;

    const [accounts, setAccounts] = useState<readonly WalletAccount[]>([]);
    const [balance, setBalance] = useState<{ address: string; lamports: number }>();
    const [busy, setBusy] = useState<string>();
    const [log, setLog] = useState<LogEntry[]>([]);
    const [message, setMessage] = useState('');

    const account = accounts[0];

    const append = useCallback((entry: Omit<LogEntry, 'id'>) => {
        setLog((current) => [{ ...entry, id: (current[0]?.id ?? 0) + 1 }, ...current]);
    }, []);

    // Bumped after actions that move funds, to refetch the balance.
    const [balanceVersion, setBalanceVersion] = useState(0);
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
    useEffect(() => {
        if (!features) return;
        return features[StandardEvents].on('change', (properties) => {
            if (properties.accounts) setAccounts(properties.accounts);
        });
    }, [features]);

    // Restore a cached session on load. Only the on-device wallet supports a silent connect;
    // the remote wallet would open its QR code dialog instead.
    useEffect(() => {
        if (!features || wallet?.name !== SolanaMobileWalletAdapterWalletName) return;
        features[StandardConnect].connect({ silent: true })
            .then(({ accounts: restored }) => {
                setAccounts(restored);
                if (restored.length) append({ ok: true, text: 'Restored the previous session' });
            })
            .catch(() => undefined);
    }, [features, wallet, append]);

    const run = useCallback(
        (label: string, action: () => Promise<Outcome>) => {
            if (busy) return;
            setBusy(label);
            action()
                .then(({ text, href }) => append({ ok: true, text: `${label} ✓ ${text}`, href }))
                .catch((error: unknown) =>
                    append({ ok: false, text: `${label} ✗ ${error instanceof Error ? error.message : String(error)}` }),
                )
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
                        Mobile Wallet Adapter isn't available in this browser. Open this page in Chrome on an Android
                        device with a compatible wallet installed, or on a desktop browser with a relay configured.
                    </p>
                </section>
            </main>
        );
    }

    const memoTransaction = async (feePayer: WalletAccount) =>
        buildMemoTransaction(
            feePayer.publicKey as Uint8Array,
            await getLatestBlockhash(config.rpcUrl),
            message || 'Hello from the Mobile Wallet Adapter example',
        );

    const connect = () =>
        run('Connect', async () => {
            const { accounts: connected } = await features[StandardConnect].connect();
            setAccounts(connected);
            return { text: connected[0] ? connected[0].address : 'no accounts' };
        });

    const signIn = () =>
        run('Sign in', async () => {
            const input: SolanaSignInInput = {
                domain: window.location.host,
                statement: 'Sign in to the Mobile Wallet Adapter example',
                uri: window.location.origin,
            };
            const [output] = await features[SolanaSignIn].signIn(input);
            if (!output || !verifySignIn(input, output)) throw new Error('Sign-in verification failed');
            setAccounts([output.account]);
            return { text: `verified ${output.account.address}` };
        });

    const disconnect = () =>
        run('Disconnect', async () => {
            await features[StandardDisconnect].disconnect();
            setAccounts([]);
            return { text: 'done' };
        });

    const fundAccount = (current: WalletAccount) =>
        run('Fund account', async () => {
            const signature = await requestAirdrop(config.rpcUrl, current.address, AIRDROP_LAMPORTS);
            await confirmSignature(config.rpcUrl, signature);
            refreshBalance();
            return { text: `received ◎${formatSol(AIRDROP_LAMPORTS)}`, href: explorerTxUrl(signature) };
        });

    const recordMessage = (current: WalletAccount) =>
        run('Record message', async () => {
            const [output] = await features[SolanaSignAndSendTransaction].signAndSendTransaction({
                account: current,
                chain: CHAIN,
                transaction: await memoTransaction(current),
            });
            const signature = base58Encode(output!.signature);
            await confirmSignature(config.rpcUrl, signature);
            refreshBalance();
            return { text: signature, href: explorerTxUrl(signature) };
        });

    const signTransaction = (current: WalletAccount) =>
        run('Sign transaction', async () => {
            const [output] = await features[SolanaSignTransaction].signTransaction({
                account: current,
                chain: CHAIN,
                transaction: await memoTransaction(current),
            });
            // The fee payer's signature is the first 64 bytes after the one-byte signature count.
            return { text: base58Encode(output!.signedTransaction.slice(1, 65)) };
        });

    const signMessage = (current: WalletAccount) =>
        run('Sign message', async () => {
            const [output] = await features[SolanaSignMessage].signMessage({
                account: current,
                message: new TextEncoder().encode(message),
            });
            return { text: base58Encode(output!.signature) };
        });

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
                        Authorize this app with a mobile wallet to sign messages and transactions on devnet.
                    </p>
                    <div className="row">
                        <button className="primary" data-testid="connect" disabled={!!busy} onClick={connect}>
                            {busy === 'Connect' ? 'Connecting…' : 'Connect'}
                        </button>
                        <button data-testid="sign-in" disabled={!!busy} onClick={signIn}>
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
                                <code data-testid="account-address">{account.address}</code>
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
                                disabled={!message}
                                onClick={() => recordMessage(account)}
                                testId="record-message"
                            >
                                Signs and sends a transaction that writes the message on-chain with the Memo program.
                            </Action>
                            <Action
                                label="Sign transaction"
                                buttonText="Sign"
                                busy={busy}
                                disabled={!message}
                                onClick={() => signTransaction(account)}
                                testId="sign-transaction"
                            >
                                Asks the wallet to sign the same memo transaction without sending it.
                            </Action>
                            <Action
                                label="Sign message"
                                buttonText="Sign"
                                busy={busy}
                                disabled={!message}
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

function Header() {
    return (
        <header>
            <h1>Mobile Wallet Adapter</h1>
            <p className="subtitle">Example web dapp</p>
        </header>
    );
}

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
