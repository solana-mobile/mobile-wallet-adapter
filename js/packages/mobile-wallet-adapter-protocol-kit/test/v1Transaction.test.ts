// @vitest-environment node
import { readFile } from 'node:fs/promises';

import {
    address,
    appendTransactionMessageInstruction,
    type Blockhash,
    blockhash,
    createKeyPairSignerFromBytes,
    createSolanaRpc,
    createTransactionMessage,
    generateKeyPairSigner,
    getAddressEncoder,
    getBase58Decoder,
    getBase58Encoder,
    getBase64Decoder,
    getBase64Encoder,
    getTransactionEncoder,
    getUtf8Encoder,
    type KeyPairSigner,
    pipe,
    setTransactionMessageConfig,
    setTransactionMessageFeePayerSigner,
    setTransactionMessageLifetimeUsingBlockhash,
    signature,
    signTransactionMessageWithSigners,
} from '@solana/kit';
import { afterEach, describe, expect, it, vi } from 'vitest';

const { mockBaseTransact } = vi.hoisted(() => ({
    mockBaseTransact: vi.fn(),
}));

vi.mock('@solana-mobile/mobile-wallet-adapter-protocol', async () => {
    const actual = await vi.importActual<typeof import('@solana-mobile/mobile-wallet-adapter-protocol')>(
        '@solana-mobile/mobile-wallet-adapter-protocol',
    );

    return {
        ...actual,
        transact: mockBaseTransact,
    };
});

import { transact } from '../src/transact.js';

/** Largest transaction a legacy or v0 message can be; v1 raises this to 4096 bytes. */
const LEGACY_MAX_TRANSACTION_SIZE = 1232;
/** Byte 0 of every v1 transaction. Legacy and v0 transactions start with a compact-u16 signature count. */
const V1_TRANSACTION_DISCRIMINATOR = 0x81;
const MEMO_PROGRAM_ADDRESS = address('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr');
/** Long enough that the transaction cannot fit in a legacy or v0 envelope. */
const MEMO = 'v1'.repeat(1024);

const KEYPAIR_PATH = process.env.MWA_TEST_KEYPAIR_PATH;
const RPC_URL = process.env.MWA_TEST_RPC_URL ?? 'https://api.devnet.solana.com';

afterEach(() => {
    mockBaseTransact.mockReset();
});

function createV1TransactionMessage(feePayer: KeyPairSigner, recentBlockhash: Blockhash, lastValidBlockHeight: bigint) {
    return pipe(
        createTransactionMessage({ version: 1 }),
        (message) => setTransactionMessageFeePayerSigner(feePayer, message),
        (message) =>
            setTransactionMessageLifetimeUsingBlockhash({ blockhash: recentBlockhash, lastValidBlockHeight }, message),
        (message) =>
            appendTransactionMessageInstruction(
                { data: getUtf8Encoder().encode(MEMO), programAddress: MEMO_PROGRAM_ADDRESS },
                message,
            ),
        // A v1 transaction with no compute unit limit is budgeted zero compute units and fails at execution.
        (message) =>
            setTransactionMessageConfig(
                { computeUnitLimit: 1_400_000, loadedAccountsDataSizeLimit: 1024 * 1024 },
                message,
            ),
    );
}

function decodeBase64(payload: string): Uint8Array {
    return new Uint8Array(getBase64Encoder().encode(payload));
}

describe('v1 transactions', () => {
    it('passes a signed v1 transaction through signTransactions unchanged', async () => {
        const feePayer = await generateKeyPairSigner();
        const transactionMessage = createV1TransactionMessage(
            feePayer,
            blockhash('11111111111111111111111111111111'),
            0n,
        );
        const signedTransaction = await signTransactionMessageWithSigners(transactionMessage);
        const wireTransaction = getTransactionEncoder().encode(signedTransaction);
        expect(wireTransaction[0]).toBe(V1_TRANSACTION_DISCRIMINATOR);
        expect(wireTransaction.length).toBeGreaterThan(LEGACY_MAX_TRANSACTION_SIZE);
        // Fixed-position v1 header the reference fakewallet signer relies on
        // (android/fakewallet/.../usecase/SolanaSigningUseCase.kt): signature count at byte 1, account
        // count at byte 41, inline accounts from byte 42, signatures at the tail with no length prefix.
        expect(wireTransaction[1]).toBe(1);
        expect(wireTransaction[41]).toBe(2);
        expect(wireTransaction.slice(42, 74)).toEqual(new Uint8Array(getAddressEncoder().encode(feePayer.address)));
        expect(wireTransaction.slice(-64)).toEqual(new Uint8Array(signedTransaction.signatures[feePayer.address]!));

        const baseWallet = {
            signTransactions: vi.fn(async ({ payloads }: { payloads: string[] }) => ({ signed_payloads: payloads })),
        };
        mockBaseTransact.mockImplementation(async (callback) => callback(baseWallet));

        const [roundTripped] = await transact((wallet) =>
            wallet.signTransactions({ transactions: [signedTransaction] }),
        );

        expect(baseWallet.signTransactions).toHaveBeenCalledTimes(1);
        const [{ payloads }] = baseWallet.signTransactions.mock.calls[0];
        expect(payloads).toHaveLength(1);
        expect(decodeBase64(payloads[0])).toEqual(wireTransaction);
        expect(getTransactionEncoder().encode(roundTripped)).toEqual(wireTransaction);
        expect(roundTripped.messageBytes).toEqual(signedTransaction.messageBytes);
        expect(roundTripped.signatures).toEqual(signedTransaction.signatures);
    });

    it('compiles an unsigned v1 transaction message into a v1 payload for signAndSendTransactions', async () => {
        const feePayer = await generateKeyPairSigner();
        const transactionMessage = createV1TransactionMessage(
            feePayer,
            blockhash('11111111111111111111111111111111'),
            0n,
        );
        const signatureBytes = new Uint8Array(64).fill(7);
        const baseWallet = {
            signAndSendTransactions: vi.fn(async () => ({
                signatures: [getBase64Decoder().decode(signatureBytes)],
            })),
        };
        mockBaseTransact.mockImplementation(async (callback) => callback(baseWallet));

        const [returnedSignature] = await transact((wallet) =>
            wallet.signAndSendTransactions({ transactions: [transactionMessage] }),
        );

        const [{ payloads }] = baseWallet.signAndSendTransactions.mock.calls[0] as unknown as [{ payloads: string[] }];
        const wireTransaction = decodeBase64(payloads[0]);
        expect(wireTransaction[0]).toBe(V1_TRANSACTION_DISCRIMINATOR);
        expect(wireTransaction.length).toBeGreaterThan(LEGACY_MAX_TRANSACTION_SIZE);
        expect(new Uint8Array(returnedSignature)).toEqual(signatureBytes);
    });
});

/**
 * Opt-in live check. Set `MWA_TEST_KEYPAIR_PATH` to a funded Solana CLI keypair file to run it against
 * devnet (or `MWA_TEST_RPC_URL` to pick another cluster). Until the `larger-transaction-sizes` feature
 * gate activates on mainnet (epoch 1035), pointing this at a mainnet RPC must fail.
 */
describe.skipIf(!KEYPAIR_PATH)('v1 transactions against a live cluster', () => {
    it('lands a v1 transaction larger than the legacy limit through the kit wallet API', async () => {
        const rpc = createSolanaRpc(RPC_URL);
        const feePayer = await createKeyPairSignerFromBytes(
            new Uint8Array(JSON.parse(await readFile(KEYPAIR_PATH!, 'utf8'))),
        );
        const { value: latestBlockhash } = await rpc.getLatestBlockhash({ commitment: 'confirmed' }).send();
        const transactionMessage = createV1TransactionMessage(
            feePayer,
            latestBlockhash.blockhash,
            latestBlockhash.lastValidBlockHeight,
        );
        const signedTransaction = await signTransactionMessageWithSigners(transactionMessage);
        expect(getTransactionEncoder().encode(signedTransaction).length).toBeGreaterThan(LEGACY_MAX_TRANSACTION_SIZE);

        // Stand in for the wallet: submit the payload the dapp side produced, exactly as received.
        const baseWallet = {
            signAndSendTransactions: vi.fn(async ({ payloads }: { payloads: string[] }) => {
                const signatures = await Promise.all(
                    payloads.map(async (payload) => {
                        const transactionSignature = await rpc
                            .sendTransaction(payload as Parameters<typeof rpc.sendTransaction>[0], {
                                encoding: 'base64',
                                preflightCommitment: 'confirmed',
                            })
                            .send();
                        return getBase64Decoder().decode(getBase58Encoder().encode(transactionSignature));
                    }),
                );
                return { signatures };
            }),
        };
        mockBaseTransact.mockImplementation(async (callback) => callback(baseWallet));

        const [signatureBytes] = await transact((wallet) =>
            wallet.signAndSendTransactions({ transactions: [signedTransaction] }),
        );
        const transactionSignature = signature(getBase58Decoder().decode(signatureBytes));

        const deadline = Date.now() + 60_000;
        let confirmed = false;
        while (!confirmed && Date.now() < deadline) {
            const { value } = await rpc.getSignatureStatuses([transactionSignature]).send();
            const status = value[0];
            if (status?.err) throw new Error(`Transaction failed: ${JSON.stringify(status.err)}`);
            confirmed = status?.confirmationStatus === 'confirmed' || status?.confirmationStatus === 'finalized';
            if (!confirmed) await new Promise((resolve) => setTimeout(resolve, 1_000));
        }
        expect(confirmed).toBe(true);
    }, 90_000);
});
