import InfoIcon from '@mui/icons-material/Info';
import {
    Button,
    ButtonGroup,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    Typography,
} from '@mui/material';
import { LoadingButton } from '@mui/lab';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import type { TransactionSignature } from '@solana/web3.js';
import { PublicKey, TransactionMessage, VersionedTransaction } from '@solana/web3.js';
import { useSnackbar } from 'notistack';
import React, { useState } from 'react';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<{
    children?: React.ReactNode;
    message: string;
}>;

function getBase64StringFromByteArray(byteArray: Uint8Array): string {
    return btoa(String.fromCharCode.call(null, ...byteArray));
}

export default function SendV0TransactionButton({ children, message }: Props) {
    const { enqueueSnackbar } = useSnackbar();
    const { connection } = useConnection();
    const { publicKey, sendTransaction, wallet } = useWallet();
    const supportedTransactionVersions = wallet?.adapter.supportedTransactionVersions;
    const [sendV0TransactionTutorialOpen, setSendV0TransactionTutorialOpen] = useState(false);
    const [awaitingConfirmation, setAwaitingConfirmation] = useState(false);
    const [previewSignature, setPreviewSignature] = useState<TransactionSignature | undefined>(undefined);
    return (
        <>
            <ButtonGroup fullWidth={true} variant="outlined">
                <LoadingButton
                    loading={awaitingConfirmation}
                    disabled={publicKey == null || !message}
                    onClick={async () => {
                        if (publicKey == null) {
                            return;
                        }
                        if (!supportedTransactionVersions) throw new Error("Wallet doesn't support versioned transactions!");
                        if (!supportedTransactionVersions.has(0)) throw new Error("Wallet doesn't support v0 transactions!");

                        const messageBuffer = new Uint8Array(message.split('').map((c) => c.charCodeAt(0)));

                        /**
                         * This lookup table only exists on devnet and can be replaced as
                         * needed.  To create and manage a lookup table, use the `solana
                         * address-lookup-table` commands.
                         */
                        const { value: lookupTable } = await connection.getAddressLookupTable(
                            new PublicKey('F3MfgEJe1TApJiA14nN2m4uAH4EBVrqdBnHeGeSXvQ7B')
                        );
                        if (!lookupTable) throw new Error("Address lookup table wasn't found!");
            
                        const {
                            context: { slot: minContextSlot },
                            value: { blockhash, lastValidBlockHeight },
                        } = await connection.getLatestBlockhashAndContext();
            
                        const txnMessage = new TransactionMessage({
                            payerKey: publicKey,
                            recentBlockhash: blockhash,
                            instructions: [
                                {
                                    data: Buffer.from(messageBuffer),
                                    keys: lookupTable.state.addresses.map((pubkey, index) => ({
                                        pubkey,
                                        isWritable: index % 2 == 0,
                                        isSigner: false,
                                    })),
                                    programId: new PublicKey('Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo'),
                                },
                            ],
                        });
                        const transaction = new VersionedTransaction(txnMessage.compileToV0Message([lookupTable]));

                        let signature: TransactionSignature | undefined = undefined;
                        signature = await sendTransaction(transaction, connection, { minContextSlot });

                        if (signature){
                            enqueueSnackbar('Transaction Sent', {
                                action() {
                                    return (
                                        <Button color="inherit" onClick={() => setPreviewSignature(signature)}>
                                            View Signature
                                        </Button>
                                    );
                                },
                                variant: 'success',
                            });
                        }
                        
                        setAwaitingConfirmation(true);
                        let result = await connection.confirmTransaction({ blockhash, lastValidBlockHeight, signature });
                        setAwaitingConfirmation(false);

                        if (!result.value.err){
                            enqueueSnackbar('Transaction Confirmed', {
                                action() {
                                    return (
                                        <Button color="inherit" onClick={() => setPreviewSignature(signature)}>
                                            View Signature
                                        </Button>
                                    );
                                },
                                variant: 'success',
                            });
                        }
                    }}
                    variant="outlined"
                >
                    {children}
                </LoadingButton>
                <Button
                    disabled={publicKey == null}
                    onClick={() => {
                        setSendV0TransactionTutorialOpen(true);
                    }}
                    sx={{ flexBasis: 0 }}
                >
                    <InfoIcon fontSize="small" />
                </Button>
            </ButtonGroup>
            <Dialog
                open={sendV0TransactionTutorialOpen}
                onClose={() => {
                    setSendV0TransactionTutorialOpen(false);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        Clicking &ldquo;Send V0 Transaction&rdquo; will sign the text you&apos;ve written, record it to the blockchain (devnet), and display the transaction
                        signature.
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setSendV0TransactionTutorialOpen(false);
                            }}
                        >
                            Got it
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
            <Dialog
                open={previewSignature != null}
                onClose={() => {
                    setPreviewSignature(undefined);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        <Typography sx={{ wordBreak: 'break-all' }}>
                            {previewSignature ? previewSignature : undefined}
                        </Typography>
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setPreviewSignature(undefined);
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