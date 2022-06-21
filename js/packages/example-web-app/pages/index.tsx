import { AppBar, Stack, TextField, Toolbar, Typography } from '@mui/material';
import { styled } from '@mui/material/styles';
import { useWallet } from '@solana/wallet-adapter-react';
import { SolanaMobileWalletAdapterWalletName } from '@solana-mobile/wallet-adapter-mobile';
import type { NextPage } from 'next';
import React, { useEffect, useState } from 'react';

import AccountInfo from '../components/AccountInfo';
import ConnectButton from '../components/ConnectButton';
import DisconnectButton from '../components/DisconnectButton';
import FundAccountButton from '../components/FundAccountButton';
import RecordMessageButton from '../components/RecordMessageButton';
import SignMessageButton from '../components/SignMessageButton';

const Offset = styled('div')(
    // @ts-ignore
    ({ theme }) => theme.mixins.toolbar,
);

const Home: NextPage = () => {
    const { publicKey, select, wallet } = useWallet();
    const [memoText, setMemoText] = useState('');
    useEffect(() => {
        if (wallet?.adapter.name !== SolanaMobileWalletAdapterWalletName) {
            select(SolanaMobileWalletAdapterWalletName);
        }
    }, [select, wallet]);
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
                    {publicKey ? (
                        <AccountInfo publicKey={publicKey} />
                    ) : (
                        <ConnectButton color="inherit" variant="outlined">
                            Connect
                        </ConnectButton>
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
                <SignMessageButton message={memoText}>Sign Message</SignMessageButton>
                <FundAccountButton>Fund Account (devnet)</FundAccountButton>
                <DisconnectButton color="error" variant="outlined">
                    Disconnect
                </DisconnectButton>
            </Stack>
        </>
    );
};

export default Home;
