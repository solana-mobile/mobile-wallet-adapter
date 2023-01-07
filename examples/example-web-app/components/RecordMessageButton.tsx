import InfoIcon from '@mui/icons-material/Info';
import { LoadingButton } from '@mui/lab';
import { Button, ButtonGroup, Dialog, DialogActions, DialogContent, DialogContentText, Typography } from '@mui/material';
import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import {
    PublicKey,
    RpcResponseAndContext,
    SignatureResult,
    Transaction,
    TransactionInstruction,
    TransactionMessage,
    VersionedTransaction,
} from '@solana/web3.js';
import { useSnackbar } from 'notistack';
import React, { useState } from 'react';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<{
    children?: React.ReactNode;
    message: string;
    transactionVersion?: 'legacy' | 0
}>;

export default function RecordMessageButton({ children, message, transactionVersion = 0 }: Props) {
    const { enqueueSnackbar } = useSnackbar();
    const { connection } = useConnection();
    const { publicKey, sendTransaction, wallet } = useWallet();
    const [recordMessageTutorialOpen, setRecordMessageTutorialOpen] = useState(false);
    const [recordingInProgress, setRecordingInProgress] = useState(false);
    const transactionVersionLabel = transactionVersion === 'legacy'? transactionVersion : `V${transactionVersion}`;
    const supportedTxnVersions = wallet?.adapter.supportedTransactionVersions;
    const transactionSupported = transactionVersion === 'legacy' || supportedTxnVersions?.has(transactionVersion);
    const recordMessageGuarded = useGuardedCallback(
        async (messageBuffer: Buffer): Promise<[string | null, RpcResponseAndContext<SignatureResult>]> => {
            
            const {
                context,
                value: { blockhash, lastValidBlockHeight },
            } = await connection.getLatestBlockhashAndContext();

            const memoInstruction = new TransactionInstruction({
                data: messageBuffer,
                keys: [],
                programId: new PublicKey('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr'),
            });

            let memoProgramTransaction: Transaction | VersionedTransaction;

            if (transactionVersion === 'legacy') {
                memoProgramTransaction = new Transaction({
                    blockhash: blockhash,
                    lastValidBlockHeight: lastValidBlockHeight,
                    feePayer: publicKey,
                }).add(memoInstruction)
            } else {
                let memoProgramMessage = new TransactionMessage({
                    payerKey: publicKey!,
                    recentBlockhash: blockhash,
                    instructions: [ memoInstruction ],
                })
                memoProgramTransaction = new VersionedTransaction(memoProgramMessage.compileToV0Message())
            }

            const signature = await sendTransaction(memoProgramTransaction, connection, { minContextSlot: context.slot});
            return [signature, await connection.confirmTransaction({ blockhash, lastValidBlockHeight, signature })];
        },
        [connection, publicKey, sendTransaction],
    );
    return (
        <>
            <ButtonGroup fullWidth={true} variant="contained">
                <LoadingButton
                    disabled={publicKey == null || !message || !transactionSupported}
                    loading={recordingInProgress}
                    onClick={async () => {
                        if (publicKey == null || sendTransaction == null) {
                            return;
                        }
                        setRecordingInProgress(true);
                        try {
                            const result = await recordMessageGuarded(Buffer.from(message))
                            if (result) {
                                const [signature, response] = result;
                                const {
                                    value: { err },
                                } = response;
                                if (err) {
                                    enqueueSnackbar(
                                        'Failed to record message:' + (err instanceof Error ? err.message : err),
                                        { variant: 'error' },
                                    );
                                } else {
                                    enqueueSnackbar('Message recorded', {
                                        action() {
                                            const explorerUrl =
                                                'https://explorer.solana.com/tx/' +
                                                signature +
                                                '?cluster=' +
                                                WalletAdapterNetwork.Devnet;
                                            return (
                                                <Button color="inherit" href={explorerUrl} target="_blank">
                                                    View
                                                </Button>
                                            );
                                        },
                                        variant: 'success',
                                    });
                                }
                            }
                        } finally {
                            setRecordingInProgress(false);
                        }
                    }}
                    variant="contained"
                >
                    {children}
                </LoadingButton>
                <Button
                    disabled={publicKey == null}
                    onClick={() => {
                        setRecordMessageTutorialOpen(true);
                    }}
                    sx={{ flexBasis: 0 }}
                >
                    <InfoIcon fontSize="small" />
                </Button>
            </ButtonGroup>
            <Dialog
                open={recordMessageTutorialOpen}
                onClose={() => {
                    setRecordMessageTutorialOpen(false);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        Clicking &ldquo;Record&rdquo; will send a {transactionVersionLabel} transaction that 
                        records the text you&apos;ve written on the Solana blockchain using the Memo program. 
                    </DialogContentText>
                    {!transactionSupported && (
                        <Typography>
                            Note: this operation is not supported by the currently connected wallet
                        </Typography>
                    )}
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setRecordMessageTutorialOpen(false);
                            }}
                        >
                            Got it
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
        </>
    );
}
