import {WalletAdapterNetwork} from '@solana/wallet-adapter-base';
import {useConnection} from '@solana/wallet-adapter-react';
import {
  PublicKey,
  RpcResponseAndContext,
  SignatureResult,
  Transaction,
  TransactionInstruction,
} from '@solana/web3.js';
import {transact} from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import React, {useContext, useState} from 'react';
import {Linking, StyleSheet, View} from 'react-native';
import {
  Button,
  Dialog,
  IconButton,
  Paragraph,
  Portal,
} from 'react-native-paper';
import {TextEncoder} from 'text-encoding';

import useAuthorization from '../utils/useAuthorization';
import useGuardedCallback from '../utils/useGuardedCallback';
import {SnackbarContext} from './SnackbarProvider';

type Props = Readonly<{
  children?: React.ReactNode;
  message: string;
}>;

export default function RecordMessageButton({children, message}: Props) {
  const {authorizeSession, selectedAccount} = useAuthorization();
  const {connection} = useConnection();
  const setSnackbarProps = useContext(SnackbarContext);
  const [recordMessageTutorialOpen, setRecordMessageTutorialOpen] =
    useState(false);
  const [recordingInProgress, setRecordingInProgress] = useState(false);
  const recordMessageGuarded = useGuardedCallback(
    async (
      messageBuffer: Buffer,
    ): Promise<[string, RpcResponseAndContext<SignatureResult>]> => {
      const [signature] = await transact(async wallet => {
        const [freshAccount, latestBlockhash] = await Promise.all([
          authorizeSession(wallet),
          connection.getLatestBlockhash(),
        ]);
        const memoProgramTransaction = new Transaction({
          ...latestBlockhash,
          feePayer:
            // Either the public key that was already selected when this method was called...
            selectedAccount?.publicKey ??
            // ...or the newly authorized public key.
            freshAccount.publicKey,
        }).add(
          new TransactionInstruction({
            data: messageBuffer,
            keys: [],
            programId: new PublicKey(
              'MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr',
            ),
          }),
        );
        return await wallet.signAndSendTransactions({
          transactions: [memoProgramTransaction],
        });
      });
      return [signature, await connection.confirmTransaction(signature)];
    },
    [authorizeSession, connection, selectedAccount],
  );
  return (
    <>
      <View style={styles.buttonGroup}>
        <Button
          disabled={!message}
          loading={recordingInProgress}
          onPress={async () => {
            if (recordingInProgress) {
              return;
            }
            setRecordingInProgress(true);
            try {
              const result = await recordMessageGuarded(
                new TextEncoder().encode(message) as Buffer,
              );
              if (result) {
                const [signature, response] = result;
                const {
                  value: {err},
                } = response;
                if (err) {
                  setSnackbarProps({
                    children:
                      'Failed to record message:' +
                      (err instanceof Error ? err.message : err),
                  });
                } else {
                  setSnackbarProps({
                    action: {
                      label: 'View',
                      onPress() {
                        const explorerUrl =
                          'https://explorer.solana.com/tx/' +
                          signature +
                          '?cluster=' +
                          WalletAdapterNetwork.Devnet;
                        Linking.openURL(explorerUrl);
                      },
                    },
                    children: 'Message recorded',
                  });
                }
              }
            } finally {
              setRecordingInProgress(false);
            }
          }}
          mode="contained"
          style={styles.actionButton}>
          {children}
        </Button>
        <IconButton
          icon="help"
          mode="outlined"
          onPress={() => {
            setRecordMessageTutorialOpen(true);
          }}
          style={styles.infoButton}
        />
      </View>
      <Portal>
        <Dialog
          onDismiss={() => {
            setRecordMessageTutorialOpen(false);
          }}
          visible={recordMessageTutorialOpen}>
          <Dialog.Content>
            <Paragraph>
              Clicking &ldquo;Record&rdquo; will send a transaction that records
              the text you&apos;ve written on the Solana blockchain using the
              Memo program.
            </Paragraph>
            <Dialog.Actions>
              <Button
                onPress={() => {
                  setRecordMessageTutorialOpen(false);
                }}>
                Got it
              </Button>
            </Dialog.Actions>
          </Dialog.Content>
        </Dialog>
      </Portal>
    </>
  );
}

const styles = StyleSheet.create({
  actionButton: {
    flex: 1,
    marginEnd: 8,
  },
  infoButton: {
    margin: 0,
  },
  buttonGroup: {
    display: 'flex',
    flexDirection: 'row',
    width: '100%',
  },
});
