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
import { useWallet } from '@solana/wallet-adapter-react';
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

export default function SignMessageButton({ children, message }: Props) {
    const { enqueueSnackbar } = useSnackbar();
    const { publicKey, signMessage } = useWallet();
    const [previewSignature, setPreviewSignature] = useState<Uint8Array | null>(null);
    const [signMessageTutorialOpen, setSignMessageTutorialOpen] = useState(false);
    const signMessageGuarded = useGuardedCallback(
        async (buffer) => {
            // eslint-disable-next-line @typescript-eslint/no-non-null-assertion
            return await signMessage!(buffer);
        },
        [signMessage],
    );
    return (
        <>
            <ButtonGroup fullWidth={true} variant="outlined">
                <Button
                    disabled={publicKey == null || !message}
                    onClick={async () => {
                        if (publicKey == null) {
                            return;
                        }
                        const messageBuffer = new Uint8Array(message.split('').map((c) => c.charCodeAt(0)));
                        const signature = await signMessageGuarded(messageBuffer);
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
                    {children}
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
}
