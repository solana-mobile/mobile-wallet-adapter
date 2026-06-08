import React from 'react';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';

import WalletProvider from './components/WalletProvider';
import MainScreen from './screens/MainScreen';

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
