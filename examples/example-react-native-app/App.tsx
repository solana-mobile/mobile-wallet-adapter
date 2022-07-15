import {ConnectionProvider} from '@solana/wallet-adapter-react';
import {clusterApiUrl} from '@solana/web3.js';
import React, {Suspense} from 'react';
import {ActivityIndicator, SafeAreaView, StyleSheet, View} from 'react-native';
import {Provider as PaperProvider} from 'react-native-paper';

import SnackbarProvider from './components/SnackbarProvider';
import MainScreen from './screens/MainScreen';

const DEVNET_ENDPOINT = /*#__PURE__*/ clusterApiUrl('devnet');

export default function App() {
  return (
    <ConnectionProvider endpoint={DEVNET_ENDPOINT}>
      <SafeAreaView style={styles.shell}>
        <PaperProvider>
          <SnackbarProvider>
            <Suspense
              fallback={
                <View style={styles.loadingContainer}>
                  <ActivityIndicator
                    size="large"
                    style={styles.loadingIndicator}
                  />
                </View>
              }>
              <MainScreen />
            </Suspense>
          </SnackbarProvider>
        </PaperProvider>
      </SafeAreaView>
    </ConnectionProvider>
  );
}

const styles = StyleSheet.create({
  loadingContainer: {
    height: '100%',
    justifyContent: 'center',
  },
  loadingIndicator: {
    marginVertical: 'auto',
  },
  shell: {
    height: '100%',
  },
});
