import InfoIcon from '@mui/icons-material/Info';
import {
    AppBar,
    Button,
    ButtonGroup,
    Dialog,
    DialogActions,
    DialogContent,
    DialogContentText,
    Stack,
    styled,
    TextField,
    Toolbar,
    Typography,
} from '@mui/material';
import { WalletAdapterNetwork, WalletName } from '@solana/wallet-adapter-base';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import { PublicKey, Transaction, TransactionInstruction } from '@solana/web3.js';
import type { NextPage } from 'next';
import { useSnackbar } from 'notistack';
import React, { useCallback, useEffect, useMemo, useState } from 'react';

const Offset = styled('div')(({ theme }) => theme.mixins.toolbar);

function getBase64StringFromByteArray(byteArray: Uint8Array): string {
    return btoa(String.fromCharCode.call(null, ...byteArray));
}

const Home: NextPage = () => {
    const { connection } = useConnection();
    const { connect, connected, disconnect, publicKey, select, sendTransaction, signMessage, wallet } = useWallet();
    const [memoText, setMemoText] = useState('');
    const [signMessageTutorialOpen, setSignMessageTutorialOpen] = useState(false);
    const [recordMessageTutorialOpen, setRecordMessageTutorialOpen] = useState(false);
    const [previewSignature, setPreviewSignature] = useState<Uint8Array | null>(null);
    useEffect(() => {
        if (wallet?.adapter.name !== 'Native') {
            select('Native' as WalletName);
        }
    }, [select, wallet]);
    const { enqueueSnackbar } = useSnackbar();
    const tryGuarded = useCallback(
        async function <TReturn>(cb: () => TReturn) {
            try {
                return await cb();
            } catch (e: any) {
                enqueueSnackbar(`${e.name}: ${e.message}`, { variant: 'error' });
            }
        },
        [enqueueSnackbar],
    );
    const createMemoProgramTransaction = useCallback(
        async (text: string) => {
            return new Transaction({
                ...(await connection.getLatestBlockhash()),
                feePayer: publicKey,
            }).add(
                new TransactionInstruction({
                    data: Buffer.from(text),
                    keys: [],
                    programId: new PublicKey('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr'),
                }),
            );
        },
        [connection, publicKey],
    );
    const publicKeyBase58String = useMemo(() => publicKey?.toBase58(), [publicKey]);
    if (wallet == null) {
        return null;
    }
    return (
        <>
            <AppBar position="fixed">
                <Toolbar>
                    <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                        Mobile web dApp
                    </Typography>
                    {connected ? (
                        <Typography>
                            <code>{publicKeyBase58String?.slice(0, 8)}</code>
                        </Typography>
                    ) : (
                        <Button
                            color="inherit"
                            onClick={() => {
                                tryGuarded(connect);
                            }}
                            variant="outlined"
                        >
                            Connect
                        </Button>
                    )}
                </Toolbar>
            </AppBar>
            <Offset />
            <Stack p={2} spacing={2} flexGrow={1}>
                <Typography>Write a message to record on the blockchain.</Typography>
                <TextField
                    disabled={publicKey == null}
                    label="Message"
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement>) => {
                        setMemoText(e.target.value);
                    }}
                    placeholder="Hello world&hellip;"
                    value={memoText}
                />
                <ButtonGroup fullWidth={true} variant="contained">
                    <Button
                        disabled={publicKey == null}
                        color="primary"
                        onClick={async () => {
                            if (publicKey == null || sendTransaction == null) {
                                return;
                            }
                            const memoProgramTransaction = await createMemoProgramTransaction(memoText);
                            const signature = await tryGuarded(() =>
                                sendTransaction(memoProgramTransaction, connection),
                            );
                            if (signature) {
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
                        }}
                    >
                        Record Message
                    </Button>
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
                <ButtonGroup fullWidth={true} variant="outlined">
                    <Button
                        disabled={publicKey == null}
                        onClick={async () => {
                            if (publicKey == null || signMessage == null) {
                                return;
                            }
                            const message = new Uint8Array(memoText.split('').map((c) => c.charCodeAt(0)));
                            const signature = await tryGuarded(() => {
                                return signMessage(message);
                            });
                            if (signature) {
                                enqueueSnackbar('Message signed', {
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
                    >
                        Sign Message
                    </Button>
                    <Button
                        disabled={publicKey == null}
                        onClick={() => {
                            setSignMessageTutorialOpen(true);
                        }}
                        sx={{ flexBasis: 0 }}
                    >
                        <InfoIcon fontSize="small" />
                    </Button>
                </ButtonGroup>
                <Typography paragraph={true} variant="caption"></Typography>
                <Button
                    color="error"
                    disabled={publicKey == null}
                    onClick={() => {
                        tryGuarded(disconnect);
                    }}
                    variant="outlined"
                >
                    Disconnect
                </Button>
            </Stack>
            <Dialog
                open={recordMessageTutorialOpen}
                onClose={() => {
                    setRecordMessageTutorialOpen(false);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        Clicking &ldquo;Record&rdquo; will send a transaction that records the text you&apos;ve written
                        on the Solana blockchain using the Memo program.
                    </DialogContentText>
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
            <Dialog
                open={signMessageTutorialOpen}
                onClose={() => {
                    setSignMessageTutorialOpen(false);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        Clicking &ldquo;Sign Message&rdquo; will sign the text you&apos;ve written and display the
                        signature.
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setSignMessageTutorialOpen(false);
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
                    setPreviewSignature(null);
                }}
            >
                <DialogContent>
                    <DialogContentText>
                        <Typography sx={{ wordBreak: 'break-all' }}>
                            {previewSignature ? getBase64StringFromByteArray(previewSignature) : null}
                        </Typography>
                    </DialogContentText>
                    <DialogActions>
                        <Button
                            autoFocus
                            onClick={() => {
                                setPreviewSignature(null);
                            }}
                        >
                            Neat!
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
        </>
    );
};

export default Home;
