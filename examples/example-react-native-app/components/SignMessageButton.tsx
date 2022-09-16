import {transact} from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import {fromUint8Array} from 'js-base64';
import React, {useContext, useState} from 'react';
import {StyleSheet, View} from 'react-native';
import {
  Button,
  Dialog,
  IconButton,
  Paragraph,
  Portal,
  Text,
} from 'react-native-paper';

import useAuthorization from '../utils/useAuthorization';
import useGuardedCallback from '../utils/useGuardedCallback';
import {SnackbarContext} from './SnackbarProvider';

type Props = Readonly<{
  children?: React.ReactNode;
  message: string;
}>;

export default function SignMessageButton({children, message}: Props) {
  const {authorizeSession, selectedAccount} = useAuthorization();
  const [previewSignature, setPreviewSignature] = useState<Uint8Array | null>(
    null,
  );
  const [previewSignatureDialogOpen, setPreviewSignatureDialogOpen] =
    useState(false);
  const setSnackbarProps = useContext(SnackbarContext);
  const [signMessageTutorialOpen, setSignMessageTutorialOpen] = useState(false);
  const signMessageGuarded = useGuardedCallback(
    async buffer => {
      const [signature] = await transact(async wallet => {
        const freshAccount = await authorizeSession(wallet);
        return await wallet.signMessages({
          addresses: [
            // Either the address that was already selected when this method was called...
            selectedAccount?.address ??
            // ...or the newly authorized address
            freshAccount.address
          ],
          payloads: [buffer],
        });
      });
      return signature;
    },
    [authorizeSession, selectedAccount],
  );
  return (
    <>
      <View style={styles.buttonGroup}>
        <Button
          disabled={!message}
          onPress={async () => {
            const messageBuffer = new Uint8Array(
              message.split('').map(c => c.charCodeAt(0)),
            );
            const signature = await signMessageGuarded(messageBuffer);
            if (signature) {
              setSnackbarProps({
                action: {
                  label: 'View Signature',
                  onPress() {
                    setPreviewSignature(signature!);
                    setPreviewSignatureDialogOpen(true);
                  },
                },
                children: 'Message signed',
              });
            }
          }}
          mode="outlined"
          style={styles.actionButton}>
          {children}
        </Button>
        <IconButton
          icon="help"
          mode="outlined"
          onPress={() => {
            setSignMessageTutorialOpen(true);
          }}
          style={styles.infoButton}
        />
      </View>
      <Portal>
        <Dialog
          onDismiss={() => {
            setSignMessageTutorialOpen(false);
          }}
          visible={signMessageTutorialOpen}>
          <Dialog.Content>
            <Paragraph>
              Clicking &ldquo;Sign Message&rdquo; will sign the text you&apos;ve
              written and display the signature.
            </Paragraph>
            <Dialog.Actions>
              <Button
                onPress={() => {
                  setSignMessageTutorialOpen(false);
                }}>
                Got it
              </Button>
            </Dialog.Actions>
          </Dialog.Content>
        </Dialog>
        <Dialog
          visible={previewSignatureDialogOpen}
          onDismiss={() => {
            setPreviewSignatureDialogOpen(false);
          }}>
          <Dialog.Content>
            <Paragraph>
              <Text>
                {previewSignature ? fromUint8Array(previewSignature) : null}
              </Text>
            </Paragraph>
            <Dialog.Actions>
              <Button
                onPress={() => {
                  setPreviewSignatureDialogOpen(false);
                }}>
                Neat!
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
    flexDirection: 'row',
    width: '100%',
  },
});
