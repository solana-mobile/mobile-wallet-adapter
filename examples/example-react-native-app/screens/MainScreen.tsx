import React, {useState} from 'react';
import {ScrollView, StyleSheet} from 'react-native';
import {
  Appbar,
  Divider,
  Portal,
  Text,
  TextInput,
  useTheme,
} from 'react-native-paper';

import AccountInfo from '../components/AccountInfo';
import ConnectButton from '../components/ConnectButton';
import DisconnectButton from '../components/DisconnectButton';
import FundAccountButton from '../components/FundAccountButton';
import RecordMessageButton from '../components/RecordMessageButton';
import SignMessageButton from '../components/SignMessageButton';
import useAuthorization from '../utils/useAuthorization';

export default function MainScreen() {
  const {authorization} = useAuthorization();
  const {colors} = useTheme();
  const [memoText, setMemoText] = useState('');
  return (
    <>
      <Appbar.Header elevated mode="center-aligned">
        <Appbar.Content title="React Native dApp" />
      </Appbar.Header>
      <Portal.Host>
        <ScrollView contentContainerStyle={styles.container}>
          {authorization?.publicKey ? (
            <>
              <Text variant="bodyLarge">
                Write a message to record on the blockchain.
              </Text>
              <Divider style={styles.spacer} />
              <TextInput
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
        {authorization?.publicKey ? (
          <AccountInfo publicKey={authorization.publicKey} />
        ) : null}
      </Portal.Host>
    </>
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
