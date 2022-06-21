import { Chip, CircularProgress, Link, Typography } from '@mui/material';
import { PublicKey } from '@solana/web3.js';
import { Suspense, useMemo } from 'react';

import AccountBalance from './AccountBalance';

type Props = Readonly<{
    publicKey: PublicKey;
}>;

export default function AccountInfo({ publicKey }: Props) {
    const publicKeyBase58String = useMemo(() => publicKey?.toBase58(), [publicKey]);
    return (
        <>
            <Chip
                color="info"
                sx={{ marginRight: 1 }}
                label={
                    <Suspense
                        fallback={<CircularProgress color="inherit" size={18} sx={{ verticalAlign: 'middle' }} />}
                    >
                        <AccountBalance publicKey={publicKey} />
                    </Suspense>
                }
            />
            <Typography>
                <Link
                    color="inherit"
                    href={`https://explorer.solana.com/address/${publicKeyBase58String}?cluster=devnet`}
                    rel="noreferrer"
                    target="_blank"
                    underline="none"
                >
                    <code>{publicKeyBase58String.slice(0, 8)}</code>
                </Link>
            </Typography>
        </>
    );
}
