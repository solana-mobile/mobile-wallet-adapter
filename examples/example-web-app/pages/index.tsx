import { AppBar, Stack, TextField, Toolbar, Typography } from '@mui/material';
import React, { useState } from 'react';

import AccountInfo from '../components/AccountInfo';
import DisconnectButton from '../components/DisconnectButton';
import FundAccountButton from '../components/FundAccountButton';
import type { NextPage } from 'next';
import RecordMessageButton from '../components/RecordMessageButton';
import SignInButton from '../components/SignInButton';
import SignMemoTransactionButton from '../components/SignMemoTransactionButton';
import SignMessageButton from '../components/SignMessageButton';
import { styled } from '@mui/material/styles';
import { useWallet } from '@solana/wallet-adapter-react';
import dynamic from 'next/dynamic';

const Offset = styled('div')(
    // @ts-ignore
    ({ theme }) => theme.mixins.toolbar,
);

const ConnectButtonDynamic = dynamic(() => import('../components/ConnectButton'), { ssr: false });

const Home: NextPage = () => {
    const { publicKey } = useWallet();
    const [memoText, setMemoText] = useState('');
    return (
        <>
            <AppBar position="fixed">
                <Toolbar>
                    <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
                        Mobile web dApp
                    </Typography>
                    {publicKey ? (
                        <AccountInfo publicKey={publicKey} />
                    ) : (
                        <ConnectButtonDynamic color="inherit" variant="outlined">
                            Connect
                        </ConnectButtonDynamic>
                    )}
                </Toolbar>
            </AppBar>
            <Offset />
            <Stack p={2} spacing={2} flexGrow={1}>
                <Typography>Write a message to record on the blockchain.</Typography>
                <TextField
                    disabled={publicKey == null}
                    label="What's on your mind?"
                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement | HTMLInputElement>) => {
                        setMemoText(e.target.value);
                    }}
                    value={memoText}
                />
                <RecordMessageButton message={memoText}>Record Message</RecordMessageButton>
                <SignMemoTransactionButton message={memoText}>Sign Tx</SignMemoTransactionButton>
                <SignMessageButton message={memoText}>Sign Message</SignMessageButton>
                <FundAccountButton>Fund Account (devnet)</FundAccountButton>
                {publicKey ? (
                    <DisconnectButton color="error" variant="outlined">
                        Disconnect
                    </DisconnectButton>
                ) : (
                    <SignInButton variant="outlined">
                        Sign In
                    </SignInButton>
                )}
            </Stack>
        </>
    );
};

export default Home;
