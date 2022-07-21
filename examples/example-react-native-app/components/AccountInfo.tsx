import {PublicKey} from '@solana/web3.js';
import React, {Suspense, useMemo} from 'react';
import {ActivityIndicator, Linking, StyleSheet, View} from 'react-native';
import {Card, Subheading, Surface, useTheme} from 'react-native-paper';

import AccountBalance from './AccountBalance';
import DisconnectButton from './DisconnectButton';
import FundAccountButton from './FundAccountButton';

type Props = Readonly<{
  publicKey: PublicKey;
}>;

export default function AccountInfo({publicKey}: Props) {
  const {colors} = useTheme();
  const publicKeyBase58String = useMemo(
    () => publicKey.toBase58(),
    [publicKey],
  );
  return (
    <Surface elevation={4} style={styles.container}>
      <Card.Content>
        <Suspense fallback={<ActivityIndicator />}>
          <View style={styles.balanceRow}>
            <AccountBalance publicKey={publicKey} />
            <FundAccountButton publicKey={publicKey}>
              Add Funds
            </FundAccountButton>
          </View>
        </Suspense>
        <Subheading
          numberOfLines={1}
          onPress={() => {
            Linking.openURL(
              `https://explorer.solana.com/address/${publicKeyBase58String}?cluster=devnet`,
            );
          }}
          style={styles.keyRow}>
          {'\u{1f5dd} ' + publicKeyBase58String}
        </Subheading>
        <DisconnectButton buttonColor={colors.error} mode="contained">
          Disconnect
        </DisconnectButton>
      </Card.Content>
    </Surface>
  );
}

const styles = StyleSheet.create({
  balanceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  container: {
    paddingVertical: 12,
  },
  keyRow: {
    marginBottom: 12,
  },
});
