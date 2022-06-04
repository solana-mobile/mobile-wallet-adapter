import { WalletName } from '@solana/wallet-adapter-base';
import { useWallet } from '@solana/wallet-adapter-react';
import type { NextPage } from 'next';
import React, { useEffect } from 'react';

import Button from '../components/Button';

const Home: NextPage = () => {
    const { connect, connected, publicKey, select, wallet } = useWallet();
    useEffect(() => {
        select('Native' as WalletName);
    }, [select]);
    if (wallet == null) {
        return null;
    }
    return (
        <>
            {connected ? <h1>Public key: {publicKey?.toBase58()}</h1> : null}
            <Button
                disabled={connected}
                onClick={() => {
                    connect();
                }}
            >
                Authorize
            </Button>
        </>
    );
};

export default Home;
