import { LoadingButton } from '@mui/lab';
import { useConnection, useWallet } from '@solana/wallet-adapter-react';
import { useSnackbar } from 'notistack';
import React, { useState } from 'react';
import { useSWRConfig } from 'swr';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<{
    children?: React.ReactNode;
}>;

const LAMPORTS_PER_AIRDROP = 100000000;

export default function FundAccountButton({ children }: Props) {
    const { publicKey } = useWallet();
    const { connection } = useConnection();
    const { mutate } = useSWRConfig();
    const { enqueueSnackbar } = useSnackbar();
    const [airdropInProgress, setAirdropInProgress] = useState(false);
    const requestAirdropGuarded = useGuardedCallback(
        async (publicKey) => {
            const signature = await connection.requestAirdrop(publicKey, LAMPORTS_PER_AIRDROP);
            return await connection.confirmTransaction(signature);
        },
        [connection],
    );
    return (
        <LoadingButton
            loading={airdropInProgress}
            color="primary"
            disabled={publicKey == null}
            onClick={async () => {
                if (publicKey == null) {
                    return;
                }
                setAirdropInProgress(true);
                try {
                    const result = await requestAirdropGuarded(publicKey);
                    if (result) {
                        const {
                            value: { err },
                        } = result;
                        if (err) {
                            enqueueSnackbar('Failed to fund account: ' + (err instanceof Error ? err.message : err), {
                                variant: 'error',
                            });
                        } else {
                            enqueueSnackbar('Funding successful', { variant: 'success' });
                            mutate(
                                'accountBalance',
                                // Optimistic update; will be revalidated automatically by SWR.
                                (currentBalance: number) => currentBalance + LAMPORTS_PER_AIRDROP,
                            );
                        }
                    }
                } finally {
                    setAirdropInProgress(false);
                }
            }}
            variant="outlined"
        >
            {children}
        </LoadingButton>
    );
}
