import { 
  Address, 
  airdropFactory, 
  isSolanaError, 
  lamports, 
  SOLANA_ERROR__RPC__TRANSPORT_HTTP_ERROR 
} from '@solana/kit';
import React, { useContext, useState } from 'react';
import { Button } from 'react-native-paper';
import { useSWRConfig } from 'swr';

import useGuardedCallback from '../utils/useGuardedCallback';
import { SnackbarContext } from '../context/SnackbarProvider';
import { RpcContext } from '../context/RpcContext';

type Props = Readonly<{
  children?: React.ReactNode;
  address: Address;
}>;

const LAMPORTS_PER_AIRDROP = lamports(100000000n);

export default function FundAccountButton({children, address}: Props) {
  const { rpc, rpcSubscriptions } = useContext(RpcContext);
  const {mutate} = useSWRConfig();
  const setSnackbarProps = useContext(SnackbarContext);
  const [airdropInProgress, setAirdropInProgress] = useState(false);
  const requestAirdropFactoryGuarded = useGuardedCallback(async () => {
    const airdrop = airdropFactory({ rpc, rpcSubscriptions });
    return airdrop({
      commitment: 'confirmed',
      recipientAddress: address,
      lamports: LAMPORTS_PER_AIRDROP,
    }).catch((e) => {
      if (isSolanaError(e, SOLANA_ERROR__RPC__TRANSPORT_HTTP_ERROR)) {
        setSnackbarProps({
            children: 'Failed to fund account: rate limit exceeded',
        });
      } else throw e;
    });
  }, [rpc, rpcSubscriptions]);
  return (
    <Button
      icon="hand-coin-outline"
      mode="elevated"
      loading={airdropInProgress}
      onPress={async () => {
        if (airdropInProgress) {
          return;
        }
        setAirdropInProgress(true);
        try {
          const signature = await requestAirdropFactoryGuarded();
          if (signature) {
              setSnackbarProps({children: 'Funding successful'});
              mutate(
                ['accountBalance', address],
                // Optimistic update; will be revalidated automatically by SWR.
                (currentBalance?: number) =>
                  (currentBalance || 0) + Number(LAMPORTS_PER_AIRDROP),
              );
          }
        } catch (err) {
          setSnackbarProps({
            children:
              'Failed to fund account: ' +
              (err instanceof Error ? err.message : err),
          });
        } finally {
          setAirdropInProgress(false);
        }
      }}>
      {children}
    </Button>
  );
}
