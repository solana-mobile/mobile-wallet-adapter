import AsyncStorage from '@react-native-async-storage/async-storage';
import { Address } from '@solana/kit';
import React, { Suspense } from 'react';
import {
  ActivityIndicator,
  AppState,
  SafeAreaView,
  StyleSheet,
  View,
} from 'react-native';
import { Provider as PaperProvider } from 'react-native-paper';
import { Cache, SWRConfig } from 'swr';

import { ChainContextProvider } from './context/ChainContextProvider';
import { RpcContextProvider } from './context/RpcContextProvider';
import SnackbarProvider from './context/SnackbarProvider';
import MainScreen from './screens/MainScreen';

function cacheReviver(key: string, value: any) {
  if (key === 'publicKey') {
    return value as Address;
  } else {
    return value;
  }
}

const STORAGE_KEY = 'app-cache';
let initialCacheFetchPromise: Promise<void>;
let initialCacheFetchResult: any;
function asyncStorageProvider() {
  if (initialCacheFetchPromise == null) {
    initialCacheFetchPromise = AsyncStorage.getItem(STORAGE_KEY).then(
      result => {
        initialCacheFetchResult = result;
      },
    );
    throw initialCacheFetchPromise;
  }
  let storedAppCache;
  try {
    storedAppCache = JSON.parse(initialCacheFetchResult, cacheReviver);
  } catch {}
  const map = new Map(storedAppCache || []);
  initialCacheFetchResult = undefined;
  function persistCache() {
    const appCache = JSON.stringify(Array.from(map.entries()));
    AsyncStorage.setItem(STORAGE_KEY, appCache);
  }
  AppState.addEventListener('change', state => {
    if (state !== 'active') {
      persistCache();
    }
  });
  AppState.addEventListener('memoryWarning', () => {
    persistCache();
  });
  return map as Cache<any>;
}

export default function App() {
  return (
    <ChainContextProvider>
      <RpcContextProvider>
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
                <SWRConfig value={{provider: asyncStorageProvider}}>
                  <MainScreen />
                </SWRConfig>
              </Suspense>
            </SnackbarProvider>
          </PaperProvider>
        </SafeAreaView>
      </RpcContextProvider>
    </ChainContextProvider>
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
