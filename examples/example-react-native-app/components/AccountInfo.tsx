import React, {Suspense, useMemo, useState} from 'react';
import {
  ActivityIndicator,
  Linking,
  StyleSheet,
  TouchableWithoutFeedback,
  View,
} from 'react-native';
import {
  Button,
  Card,
  Menu,
  Subheading,
  Surface,
  useTheme,
} from 'react-native-paper';
import Icon from 'react-native-vector-icons/MaterialIcons';

import {Account} from '../utils/useAuthorization';
import AccountBalance from './AccountBalance';
import DisconnectButton from './DisconnectButton';
import FundAccountButton from './FundAccountButton';

type Props = Readonly<{
  accounts: Account[];
  onChange(nextSelectedAccount: Account): void;
  selectedAccount: Account;
}>;

function getLabelFromAccount(account: Account) {
  const base58EncodedPublicKey = account.publicKey.toBase58();
  if (account.label) {
    return `${account.label} (${base58EncodedPublicKey.slice(0, 8)})`;
  } else {
    return base58EncodedPublicKey;
  }
}

export default function AccountInfo({
  accounts,
  onChange,
  selectedAccount,
}: Props) {
  const {colors} = useTheme();
  const selectedAccountPublicKeyBase58String = useMemo(
    () => selectedAccount.publicKey.toBase58(),
    [selectedAccount],
  );
  const selectedAccountLabel = useMemo(
    () => getLabelFromAccount(selectedAccount),
    [selectedAccount],
  );
  const [menuVisible, setMenuVisible] = useState(false);
  return (
    <Surface elevation={4} style={styles.container}>
      <Card.Content>
        <Suspense fallback={<ActivityIndicator />}>
          <View style={styles.balanceRow}>
            <AccountBalance publicKey={selectedAccount.publicKey} />
            <FundAccountButton publicKey={selectedAccount.publicKey}>
              Add Funds
            </FundAccountButton>
          </View>
        </Suspense>
        <TouchableWithoutFeedback
          onPress={() => {
            Linking.openURL(
              `https://explorer.solana.com/address/${selectedAccountPublicKeyBase58String}?cluster=devnet`,
            );
          }}>
          <View style={styles.labelRow}>
            <Icon
              name="account-balance-wallet"
              size={18}
              style={styles.labelIcon}
            />
            <Subheading numberOfLines={1} style={styles.keyRow}>
              {selectedAccountLabel}
            </Subheading>
          </View>
        </TouchableWithoutFeedback>
        {accounts.length > 1 ? (
          <Menu
            anchor={
              <Button
                onPress={() => setMenuVisible(true)}
                style={styles.addressMenuTrigger}>
                Change Address
              </Button>
            }
            onDismiss={() => {
              setMenuVisible(false);
            }}
            style={styles.addressMenu}
            visible={menuVisible}>
            {accounts.map(account => {
              const base58PublicKey = account.publicKey.toBase58();
              return (
                <Menu.Item
                  disabled={account.address === selectedAccount.address}
                  style={styles.addressMenuItem}
                  contentStyle={styles.addressMenuItem}
                  onPress={() => {
                    onChange(account);
                    setMenuVisible(false);
                  }}
                  key={base58PublicKey}
                  title={getLabelFromAccount(account)}
                />
              );
            })}
          </Menu>
        ) : null}
        <DisconnectButton buttonColor={colors.error} mode="contained">
          Disconnect
        </DisconnectButton>
      </Card.Content>
    </Surface>
  );
}

const styles = StyleSheet.create({
  addressMenu: {
    end: 18,
  },
  addressMenuItem: {
    maxWidth: '100%',
  },
  addressMenuTrigger: {
    marginBottom: 12,
  },
  balanceRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 12,
  },
  labelIcon: {
    marginRight: 4,
    top: 4,
  },
  labelRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
  },
  container: {
    paddingVertical: 12,
  },
  keyRow: {
    marginBottom: 12,
  },
});
