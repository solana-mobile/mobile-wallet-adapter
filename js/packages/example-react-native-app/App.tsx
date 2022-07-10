import {useWallet} from '@solana/wallet-adapter-react';
import {SolanaMobileWalletAdapterWalletName} from '@solana-mobile/wallet-adapter-mobile';
import React, {useEffect, useState} from 'react';
import {SafeAreaView, ScrollView, StyleSheet} from 'react-native';
import {
  Appbar,
  Divider,
  Portal,
  Text,
  TextInput,
  useTheme,
} from 'react-native-paper';

import AccountInfo from './components/AccountInfo';
import ConnectButton from './components/ConnectButton';
import DisconnectButton from './components/DisconnectButton';
import FundAccountButton from './components/FundAccountButton';
import RecordMessageButton from './components/RecordMessageButton';
import SignMessageButton from './components/SignMessageButton';
import Shell from './Shell';

function AppImpl() {
  const {publicKey, select, wallet} = useWallet();
  const {colors} = useTheme();
  const [memoText, setMemoText] = useState('');
  useEffect(() => {
    if (wallet?.adapter.name !== SolanaMobileWalletAdapterWalletName) {
      select(SolanaMobileWalletAdapterWalletName);
    }
  }, [select, wallet]);
  if (wallet == null) {
    return null;
  }
  return (
    <>
      <SafeAreaView style={styles.shell}>
        <Appbar.Header elevated mode="center-aligned">
          <Appbar.Content title="React Native dApp" />
        </Appbar.Header>
        <Portal.Host>
          <ScrollView contentContainerStyle={styles.container}>
            {publicKey ? (
              <>
                <Text variant="bodyLarge">
                  Write a message to record on the blockchain.
                </Text>
                <Divider style={styles.spacer} />
                <TextInput
                  disabled={publicKey == null}
                  label="What's on your mind?"
                  onChangeText={text => {
                    setMemoText(text);
                  }}
                  style={styles.textInput}
                  value={memoText}
                />
                <Divider style={styles.spacer} />
                <RecordMessageButton message={memoText}>
                  Record Message
                </RecordMessageButton>
                <Divider style={styles.spacer} />
                <SignMessageButton message={memoText}>
                  Sign Message
                </SignMessageButton>
                <Divider style={styles.spacer} />
                <FundAccountButton>Fund Account (devnet)</FundAccountButton>
                <Divider style={styles.spacer} />
                <DisconnectButton buttonColor={colors.error} mode="contained">
                  Disconnect
                </DisconnectButton>
              </>
            ) : (
              <ConnectButton mode="contained">Connect</ConnectButton>
            )}
          </ScrollView>
          {publicKey ? <AccountInfo publicKey={publicKey} /> : null}
        </Portal.Host>
      </SafeAreaView>
    </>
  );
}

export default function App() {
  return (
    <Shell>
      <AppImpl />
    </Shell>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: 16,
  },
  shell: {
    height: '100%',
  },
  spacer: {
    marginVertical: 16,
    width: '100%',
  },
  textInput: {
    width: '100%',
  },
});
