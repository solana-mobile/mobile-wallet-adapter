import React, {useState} from 'react';
import {ScrollView, StyleSheet, View} from 'react-native';
import {Appbar, Divider, Portal, Text, TextInput} from 'react-native-paper';

import AccountInfo from '../components/AccountInfo';
import RecordMessageButton from '../components/RecordMessageButton';
import SignMessageButton from '../components/SignMessageButton';
import useAuthorization from '../utils/useAuthorization';
import SignInButton from '../components/SignInButton';

export default function MainScreen() {
  const {accounts, onChangeAccount, selectedAccount} = useAuthorization();
  const [memoText, setMemoText] = useState('');
  return (
    <>
      <Appbar.Header elevated mode="center-aligned">
        <Appbar.Content title="React Native dApp" />
      </Appbar.Header>
      <Portal.Host>
        <ScrollView contentContainerStyle={styles.container}>
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
          <SignMessageButton message={memoText}>Sign Message</SignMessageButton>
        </ScrollView>
        {accounts && selectedAccount ? (
          <AccountInfo
            accounts={accounts}
            onChange={onChangeAccount}
            selectedAccount={selectedAccount}
          />
        ) : (
          <View style={styles.container}>
            <SignInButton mode="contained">
              Sign In
            </SignInButton>
          </View>
        )}
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
