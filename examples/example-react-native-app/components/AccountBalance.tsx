import type { Address } from '@solana/addresses';
import React, { useCallback, useContext, useMemo } from 'react';
import { StyleSheet, View } from 'react-native';
import { Headline, Text } from 'react-native-paper';
import useSWR from 'swr';

import { RpcContext } from '../context/RpcContext';

const LAMPORTS_PER_SOL = 1000000000;

type Props = Readonly<{
  address: Address;
}>;

export default function AccountBalance({address}: Props) {
  const { rpc } = useContext(RpcContext);
  const balanceFetcher = useCallback(
    async function ([_, selectedAddress]: [
      'accountBalance',
      Address,
    ]): Promise<number> {
      const { value: balance } = await rpc.getBalance(selectedAddress).send();
      return Number(balance);
    },
    [rpc],
  );
  const {data: lamports} = useSWR(
    ['accountBalance', address],
    balanceFetcher,
    {
      suspense: true,
    },
  );
  const balance = useMemo(
    () =>
      new Intl.NumberFormat(undefined, {maximumFractionDigits: 1}).format(
        (lamports || 0) / LAMPORTS_PER_SOL,
      ),
    [lamports],
  );
  return (
    <View style={styles.container}>
      <Headline>Balance: </Headline>
      <Text style={styles.currencySymbol} variant="headlineLarge">
        {'\u25ce'}
      </Text>
      <Headline>{balance}</Headline>
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
