import {PublicKey} from '@solana/web3.js';
import React, {Suspense, useMemo} from 'react';
import {ActivityIndicator, Linking, StyleSheet} from 'react-native';
import {Card, Headline, Surface} from 'react-native-paper';

import AccountBalance from './AccountBalance';

type Props = Readonly<{
  publicKey: PublicKey;
}>;

export default function AccountInfo({publicKey}: Props) {
  const publicKeyBase58String = useMemo(
    () => publicKey?.toBase58(),
    [publicKey],
  );
  return (
    <Surface elevation={4} style={styles.container}>
      <Card.Content>
        <Headline
          numberOfLines={1}
          onPress={() => {
            Linking.openURL(
              `https://explorer.solana.com/address/${publicKeyBase58String}?cluster=devnet`,
            );
          }}>
          {publicKeyBase58String}
        </Headline>
        <Suspense fallback={<ActivityIndicator />}>
          <AccountBalance publicKey={publicKey} />
        </Suspense>
      </Card.Content>
    </Surface>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingVertical: 12,
  },
});
