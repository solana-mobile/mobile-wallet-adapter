import React from 'react';
import {View} from 'react-native';

import MainScreen from './screens/MainScreen';
import {SafeAreaProvider} from 'react-native-safe-area-context';
import WalletProvider from './components/WalletProvider';

export default function App() {
  return (
    <SafeAreaProvider>
      <WalletProvider>
        <View>
          <MainScreen />
        </View>
      </WalletProvider>
    </SafeAreaProvider>
  );
}
