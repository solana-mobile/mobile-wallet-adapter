import InfoIcon from '@mui/icons-material/Info';
import { LoadingButton } from '@mui/lab';
import { Button, ButtonGroup, Dialog, DialogActions, DialogContent, DialogContentText, Typography } from '@mui/material';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import {
    PublicKey,
    Transaction,
    TransactionInstruction,
    TransactionMessage,
    VersionedTransaction,
} from '@solana/web3.js';
import { useSnackbar } from 'notistack';
import React, { useState } from 'react';

import useGuardedCallback from '../utils/useGuardedCallback';
import { isVersionedTransaction } from '@solana/wallet-adapter-base';

type Props = Readonly<{
    children?: React.ReactNode;
    message: string;
}>;

function getBase64SignatureFromTransaction(transaction: Transaction | VersionedTransaction): string {
    if (isVersionedTransaction(transaction)) {
        return btoa(String.fromCharCode.call(null, ...transaction.signatures[0]));
    } else {
        return btoa(String.fromCharCode.call(null, ...new Uint8Array(transaction.signature!!)));
    }
}

export default function SignMemoTransactionButton({ children, message }: Props) {
    const { enqueueSnackbar } = useSnackbar();
    const { connection } = useConnection();
    const { publicKey, signTransaction, wallet } = useWallet();
    const [previewSignedTransaction, setPreviewSignedTransaction] = useState<Transaction | VersionedTransaction | null>(null);
    const [signMemoTransactionTutorialOpen, setSignMemoTransactionTutorialOpen] = useState(false);
    const supportedTxnVersions = wallet?.adapter.supportedTransactionVersions;
    const transactionVersion = supportedTxnVersions?.has(0) ? 0 : 'legacy';
    const signMemoTransactionGuarded = useGuardedCallback(
        async (messageBuffer: Buffer): Promise<Transaction | VersionedTransaction> => {
            
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

            // if the wallet only supports legacy transactions, use the web3.js legacy transaction 
            if (transactionVersion === 'legacy') {
                memoProgramTransaction = new Transaction({
                    blockhash: blockhash,
                    lastValidBlockHeight: lastValidBlockHeight,
                    feePayer: publicKey,
                }).add(memoInstruction)
            } else {
                // otherwise, if versioned transactions are supported, use a V0 versioned transaction
                let memoProgramMessage = new TransactionMessage({
                    payerKey: publicKey!,
                    recentBlockhash: blockhash,
                    instructions: [ memoInstruction ],
                })
                memoProgramTransaction = new VersionedTransaction(memoProgramMessage.compileToV0Message())
            }

            return await signTransaction!!(memoProgramTransaction);
        },
        [connection, publicKey, signTransaction],
    );
    return (
        <>
            <ButtonGroup fullWidth={true} variant="contained">
                <Button
                    disabled={publicKey == null || !message }
                    onClick={async () => {
                        if (publicKey == null || signTransaction == null) {
                            return;
                        }
                        const result = await signMemoTransactionGuarded(Buffer.from(message));
                        if (result) {
                            const signed = result;
                            if (signed) {
                                enqueueSnackbar('Transaction signed', {
                                    action() {
                                        return (
                                            <Button color="inherit" onClick={() => setPreviewSignedTransaction(signed)}>
                                                View Signature
                                            </Button>
                                        );
                                    },
                                    variant: 'success',
                                });
                            }
                        }
                    }}
                >
                    {children}
                </Button>
                <Button
                    disabled={publicKey == null}
                    onClick={() => {
                        setSignMemoTransactionTutorialOpen(true);
                    }}
                    sx={{ flexBasis: 0 }}
                >
                    <InfoIcon fontSize="small" />
                </Button>
            </ButtonGroup>
            <Dialog
                open={signMemoTransactionTutorialOpen}
                onClose={() => {
                    setSignMemoTransactionTutorialOpen(false);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        Clicking &ldquo;Sign Tx&rdquo; will sign a memo transaction that records the text you&apos;ve written
                        on the Solana blockchain using the Memo program. The transaction will not be sent to the network.
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setSignMemoTransactionTutorialOpen(false);
                            }}
                        >
                            Got it
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
            <Dialog
                open={previewSignedTransaction != null}
                onClose={() => {
                    setPreviewSignedTransaction(null);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        <Typography sx={{ wordBreak: 'break-all' }}>
                            {previewSignedTransaction ? getBase64SignatureFromTransaction(previewSignedTransaction) : null}
                        </Typography>
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setPreviewSignedTransaction(null);
                            }}
                        >
                            Neat!
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
        </>
    );
}