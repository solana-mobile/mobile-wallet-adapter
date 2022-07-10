import {WalletAdapterNetwork} from '@solana/wallet-adapter-base';
import {useConnection, useWallet} from '@solana/wallet-adapter-react';
import {
  PublicKey,
  RpcResponseAndContext,
  SignatureResult,
  Transaction,
  TransactionInstruction,
} from '@solana/web3.js';
import React, {ReactNode, useState} from 'react';
import {Linking, StyleSheet, View} from 'react-native';
import {Button, Dialog, Paragraph, Portal, Snackbar} from 'react-native-paper';

import useGuardedCallback from '../utils/useGuardedCallback';

type Props = Readonly<{
  children?: React.ReactNode;
  message: string;
}>;

export default function RecordMessageButton({children, message}: Props) {
  //   const {enqueueSnackbar} = useSnackbar();
  const {connection} = useConnection();
  const {publicKey, sendTransaction} = useWallet();
  const [snackbarProps, setSnackbarProps] = useState<
    | (Partial<React.ComponentProps<typeof Snackbar>> & {children: ReactNode})
    | null
  >(null);
  const [recordMessageTutorialOpen, setRecordMessageTutorialOpen] =
    useState(false);
  const [recordingInProgress, setRecordingInProgress] = useState(false);
  const recordMessageGuarded = useGuardedCallback(
    async (
      messageBuffer: Buffer,
    ): Promise<[string, RpcResponseAndContext<SignatureResult>]> => {
      const memoProgramTransaction = new Transaction({
        ...(await connection.getLatestBlockhash()),
        feePayer: publicKey,
      }).add(
        new TransactionInstruction({
          data: messageBuffer,
          keys: [],
          programId: new PublicKey(
            'MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr',
          ),
        }),
      );
      const signature = await sendTransaction(
        memoProgramTransaction,
        connection,
      );
      return [signature, await connection.confirmTransaction(signature)];
    },
    setSnackbarProps,
    [connection, publicKey, sendTransaction],
  );
  return (
    <>
      <View style={styles.buttonGroup}>
        <Button
          disabled={!message}
          loading={recordingInProgress}
          onPress={async () => {
            if (
              recordingInProgress ||
              publicKey == null ||
              sendTransaction == null
            ) {
              return;
            }
            setRecordingInProgress(true);
            try {
              const result = await recordMessageGuarded(
                new (globalThis as any).TextEncoder().encode(message) as Buffer,
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
        <Button
          mode="outlined"
          onPress={() => {
            setRecordMessageTutorialOpen(true);
          }}>
          ?
        </Button>
      </View>
      <Portal>
        <Snackbar
          children={null}
          onDismiss={() => {
            setSnackbarProps(null);
          }}
          visible={snackbarProps != null}
          {...snackbarProps}
        />
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
  buttonGroup: {
    display: 'flex',
    flexDirection: 'row',
    width: '100%',
  },
});
