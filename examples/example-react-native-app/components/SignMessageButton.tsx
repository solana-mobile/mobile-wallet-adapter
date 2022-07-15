import {transact} from '@solana-mobile/mobile-wallet-adapter-protocol-web3js';
import React, {useContext, useState} from 'react';
import {StyleSheet, View} from 'react-native';
import {Button, Dialog, Paragraph, Portal, Text} from 'react-native-paper';

import useAuthorization from '../utils/useAuthorization';
import useGuardedCallback from '../utils/useGuardedCallback';
import {SnackbarContext} from './SnackbarProvider';

type Props = Readonly<{
  children?: React.ReactNode;
  message: string;
}>;

function getBase64StringFromByteArray(byteArray: Uint8Array): string {
  return globalThis.btoa(String.fromCharCode.call(null, ...byteArray));
}

export default function SignMessageButton({children, message}: Props) {
  const {authorization} = useAuthorization();
  const [previewSignature, setPreviewSignature] = useState<Uint8Array | null>(
    null,
  );
  const [previewSignatureDialogOpen, setPreviewSignatureDialogOpen] =
    useState(false);
  const setSnackbarProps = useContext(SnackbarContext);
  const [signMessageTutorialOpen, setSignMessageTutorialOpen] = useState(false);
  const signMessageGuarded = useGuardedCallback(async buffer => {
    const [signature] = await transact(async walletAPI => {
      return await walletAPI('sign_message', {
        auth_token: authorization!.auth_token,
        payloads: [buffer],
      });
    });
    return signature;
  }, []);
  return (
    <>
      <View style={styles.buttonGroup}>
        <Button
          disabled={!message}
          onPress={async () => {
            if (authorization?.publicKey == null) {
              return;
            }
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
          mode="contained"
          style={styles.actionButton}>
          {children}
        </Button>
        <Button
          mode="outlined"
          onPress={() => {
            setSignMessageTutorialOpen(true);
          }}>
          ?
        </Button>
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
                {previewSignature
                  ? getBase64StringFromByteArray(previewSignature)
                  : null}
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
  buttonGroup: {
    display: 'flex',
    flexDirection: 'row',
    width: '100%',
  },
});
