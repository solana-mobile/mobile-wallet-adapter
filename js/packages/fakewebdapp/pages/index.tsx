import { WalletName } from '@solana/wallet-adapter-base';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import { PublicKey, SystemProgram, Transaction, TransactionInstruction } from '@solana/web3.js';
import type { NextPage } from 'next';
import React, { useEffect } from 'react';

import Button from '../components/Button';

const Home: NextPage = () => {
    const { connection } = useConnection();
    const { connect, connected, publicKey, select, signAllTransactions, signTransaction, wallet } = useWallet();
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
            <Button
                disabled={publicKey == null}
                onClick={async () => {
                    if (publicKey == null || signTransaction == null) {
                        return;
                    }
                    const tx = new Transaction({
                        ...(await connection.getLatestBlockhash()),
                        feePayer: publicKey,
                    });
                    tx.add(
                        new TransactionInstruction({
                            data: Buffer.from('hello world'),
                            keys: [],
                            programId: new PublicKey('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr'),
                        }),
                    );
                    const transaction = await signTransaction(tx);
                }}
            >
                Sign Transaction
            </Button>
            <Button
                disabled={publicKey == null}
                onClick={async () => {
                    if (publicKey == null || signAllTransactions == null) {
                        return;
                    }
                    const latestBlockHash = await connection.getLatestBlockhash();
                    const tx1 = new Transaction({ ...latestBlockHash, feePayer: publicKey }).add(
                        new TransactionInstruction({
                            data: Buffer.from('hello world'),
                            keys: [],
                            programId: new PublicKey('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr'),
                        }),
                    );
                    const tx2 = new Transaction({ ...latestBlockHash, feePayer: publicKey }).add(
                        new TransactionInstruction({
                            data: Buffer.from('hello world'),
                            keys: [],
                            programId: new PublicKey('MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr'),
                        }),
                    );
                    const transactions = await signAllTransactions([tx1, tx2]);
                }}
            >
                Sign Multiple Transactions
            </Button>
        </>
    );
};

export default Home;
