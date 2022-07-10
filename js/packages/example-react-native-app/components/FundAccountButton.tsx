import {useConnection, useWallet} from '@solana/wallet-adapter-react';
import React, {ReactNode, useState} from 'react';
import {Button, Portal, Snackbar} from 'react-native-paper';
import {useSWRConfig} from 'swr';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<{
  children?: React.ReactNode;
}>;

const LAMPORTS_PER_AIRDROP = 100000000;

export default function FundAccountButton({children}: Props) {
  const {publicKey} = useWallet();
  const {connection} = useConnection();
  const {mutate} = useSWRConfig();
  const [snackbarProps, setSnackbarProps] = useState<
    | (Partial<React.ComponentProps<typeof Snackbar>> & {children: ReactNode})
    | null
  >(null);
  const [airdropInProgress, setAirdropInProgress] = useState(false);
  const requestAirdropGuarded = useGuardedCallback(
    async publicKeyToFund => {
      const signature = await connection.requestAirdrop(
        publicKeyToFund,
        LAMPORTS_PER_AIRDROP,
      );
      return await connection.confirmTransaction(signature, 'finalized');
    },
    setSnackbarProps,
    [connection],
  );
  return (
    <>
      <Button
        mode="outlined"
        loading={airdropInProgress}
        onPress={async () => {
          if (airdropInProgress) {
            return;
          }
          setAirdropInProgress(true);
          try {
            const result = await requestAirdropGuarded(publicKey);
            if (result) {
              const {
                value: {err},
              } = result;
              if (err) {
                setSnackbarProps({
                  children:
                    'Failed to fund account: ' +
                    (err instanceof Error ? err.message : err),
                });
              } else {
                setSnackbarProps({children: 'Funding successful'});
                mutate(
                  'accountBalance',
                  // Optimistic update; will be revalidated automatically by SWR.
                  (currentBalance?: number) =>
                    (currentBalance || 0) + LAMPORTS_PER_AIRDROP,
                );
              }
            }
          } finally {
            setAirdropInProgress(false);
          }
        }}>
        {children}
      </Button>
      <Portal>
        <Snackbar
          children={null}
          onDismiss={() => {
            setSnackbarProps(null);
          }}
          visible={snackbarProps != null}
          {...snackbarProps}
        />
      </Portal>
    </>
  );
}
