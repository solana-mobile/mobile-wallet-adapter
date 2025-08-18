import { 
  appendTransactionMessageInstruction, 
  createTransactionMessage,
  pipe,
  setTransactionMessageLifetimeUsingBlockhash,
  TransactionSendingSigner,
  setTransactionMessageFeePayerSigner,
  signAndSendTransactionMessageWithSigners,
} from '@solana/kit';
import {
  createBlockHeightExceedencePromiseFactory,
  createRecentSignatureConfirmationPromiseFactory,
  waitForRecentTransactionConfirmation,
} from '@solana/transaction-confirmation';
import {
  SignaturesMap,
  type Transaction,
  TransactionWithBlockhashLifetime,
  compileTransaction,
} from '@solana/transactions';
import { WalletAdapterNetwork } from '@solana/wallet-adapter-base';
import { getAddMemoInstruction } from "@solana-program/memo";
import { transact } from '@solana-mobile/mobile-wallet-adapter-protocol-kit';
import React, { useContext, useState } from 'react';
import { Linking, StyleSheet, View } from 'react-native';
import {
  Button,
  Dialog,
  IconButton,
  Paragraph,
  Portal,
} from 'react-native-paper';
import bs58 from 'bs58';

import useAuthorization from '../utils/useAuthorization';
import useGuardedCallback from '../utils/useGuardedCallback';
import { SnackbarContext } from '../context/SnackbarProvider';
import { RpcContext } from '../context/RpcContext';

type Props = Readonly<{
  children?: React.ReactNode;
  message: string;
}>;

export default function RecordMessageButton({children, message}: Props) {
  const { authorizeSession, selectedAccount } = useAuthorization();
  const { rpc, rpcSubscriptions } = useContext(RpcContext);
  const setSnackbarProps = useContext(SnackbarContext);
  const [recordMessageTutorialOpen, setRecordMessageTutorialOpen] =
    useState(false);
  const [recordingInProgress, setRecordingInProgress] = useState(false);
  const recordMessageGuarded = useGuardedCallback(
    async (
      message: string,
    ): Promise<[string, Promise<void>]> => {
      const [transaction, signature] = await transact(async wallet => {
        // Authorize session (get account) and fetch latest blockhash
        const [freshAccount, { value: latestBlockhash }] = await Promise.all([
          authorizeSession(wallet),
          rpc.getLatestBlockhash().send(),
        ]);
        
        // create an MWA transaction signer
        const mwaTransactionSigner: TransactionSendingSigner = {
          address: selectedAccount?.publicKey ?? freshAccount.publicKey,
          signAndSendTransactions: async (transactions: Transaction[]) => {
            return await wallet.signAndSendTransactions({ transactions });
          }
        };

        // Build memo transaction
        const memoInstruction = getAddMemoInstruction({ memo: message });
        const memoTransactionMessage = pipe(
          createTransactionMessage({ version: 0 }),
          (tx) => setTransactionMessageFeePayerSigner(mwaTransactionSigner, tx),
          (tx) => setTransactionMessageLifetimeUsingBlockhash(latestBlockhash, tx),
          (tx) => appendTransactionMessageInstruction(memoInstruction, tx)
        );

        // Sign transaction with MWA signer and prepare outputs
        const signature = await signAndSendTransactionMessageWithSigners(memoTransactionMessage);
        const transaction = compileTransaction(memoTransactionMessage);
        const signaturesMap: SignaturesMap = { [memoTransactionMessage.feePayer.address]: signature }

        return [{ 
          messageBytes: transaction.messageBytes, 
          signatures: signaturesMap,
          lifetimeConstraint: transaction.lifetimeConstraint,
        } as Transaction & TransactionWithBlockhashLifetime, bs58.encode(signature)];
      });
      const getBlockHeightExceedencePromise = createBlockHeightExceedencePromiseFactory({
        rpc,
        rpcSubscriptions,
      });
      const getRecentSignatureConfirmationPromise = createRecentSignatureConfirmationPromiseFactory({
        rpc,
        rpcSubscriptions,
      });

      // Return transaction signature and a promise that resolves when the transaction is confirmed
      return [signature, waitForRecentTransactionConfirmation({
        commitment: 'confirmed',
        transaction: transaction,
        getBlockHeightExceedencePromise,
        getRecentSignatureConfirmationPromise
      })];
    },
    [authorizeSession, rpc, rpcSubscriptions, selectedAccount],
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
              const result = await recordMessageGuarded(message);
              if (result) {
                const [signature, confirmationPromise] = result;
                // TODO AbortController and AbortSignal are not fully implemented in RN so
                // transaction confirmation with kit does not work. Need to find a polyfill
                // await confirmationPromise;
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
            } catch(err) {
              setSnackbarProps({
                children:
                  'Failed to record message:' +
                  (err instanceof Error ? err.message : err),
              });
              console.error('Failed to record message:', err);
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
