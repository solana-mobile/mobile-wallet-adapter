import {useConnection, useWallet} from '@solana/wallet-adapter-react';
import {LAMPORTS_PER_SOL, PublicKey} from '@solana/web3.js';
import React, {useCallback, useEffect, useMemo} from 'react';
import {StyleSheet, View} from 'react-native';
import {Subheading, Text} from 'react-native-paper';
import useSWR, {mutate} from 'swr';

type Props = Readonly<{
  publicKey: PublicKey;
}>;

export default function AccountBalance({publicKey}: Props) {
  const {connection} = useConnection();
  const balanceFetcher = useCallback(
    async function (_: 'accountBalance'): Promise<number> {
      return await connection.getBalance(publicKey);
    },
    [connection, publicKey],
  );
  const {data: lamports} = useSWR('accountBalance', balanceFetcher, {
    suspense: true,
  });
  const balance = useMemo(
    () =>
      new Intl.NumberFormat(undefined, {maximumFractionDigits: 1}).format(
        (lamports || 0) / LAMPORTS_PER_SOL,
      ),
    [lamports],
  );
  const {wallet} = useWallet();
  useEffect(() => {
    if (wallet) {
      wallet.adapter.on('disconnect', () => {
        mutate('accountBalance', 0, {revalidate: false});
      });
      return () => {
        wallet.adapter.off('disconnect');
      };
    }
  }, [wallet]);
  return (
    <View style={styles.container}>
      <Subheading>Balance: </Subheading>
      <Text style={styles.currencySymbol} variant="titleLarge">
        {'\u25ce'}
      </Text>
      <Subheading>{balance}</Subheading>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    display: 'flex',
    flexDirection: 'row',
  },
  currencySymbol: {
    marginRight: 4,
  },
});
