import { Box } from '@mui/material';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import { LAMPORTS_PER_SOL, PublicKey } from '@solana/web3.js';
import { useCallback, useEffect, useMemo } from 'react';
import useSWR, { mutate } from 'swr';

type Props = Readonly<{
    publicKey: PublicKey;
}>;

export default function AccountBalance({ publicKey }: Props) {
    const { connection } = useConnection();
    const balanceFetcher = useCallback(
        async function (_: 'accountBalance'): Promise<number> {
            return await connection.getBalance(publicKey);
        },
        [connection, publicKey],
    );
    const { data: lamports } = useSWR('accountBalance', balanceFetcher, { suspense: true });
    const balance = useMemo(
        () => new Intl.NumberFormat(undefined, { maximumFractionDigits: 1 }).format((lamports || 0) / LAMPORTS_PER_SOL),
        [lamports],
    );
    const { wallet } = useWallet();
    useEffect(() => {
        if (wallet) {
            function handleDisconnect() {
                mutate('accountBalance', 0, { revalidate: false });
            }
            wallet.adapter.on('disconnect', handleDisconnect);
            return () => {
                wallet.adapter.off('disconnect', handleDisconnect);
            };
        }
    }, [wallet]);
    return (
        <>
            <Box
                component="span"
                fontSize={22}
                lineHeight={0}
                position="relative"
                mr={0.4}
                top={-2}
                sx={{ verticalAlign: 'middle' }}
            >
                {'\u25ce'}
            </Box>
            {balance}
        </>
    );
}
