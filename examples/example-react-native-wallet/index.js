/**
 * @format
 */
import 'react-native-get-random-values';

import { AppRegistry } from 'react-native';

import App from './App';
import { name as appName } from './app.json';
import MobileWalletAdapterEntrypointBottomSheet from './screens/MobileWalletAdapterEntrypointBottomSheet';

// Mock event listener functions to prevent them from fataling.
window.addEventListener = () => {};
window.removeEventListener = () => {};

AppRegistry.registerComponent(appName, () => App);
// AppRegistry.registerComponent(
//   'MobileWalletAdapterEntrypoint',
//   () => TestingEntrypointBottomSheet,
// );

AppRegistry.registerComponent('MobileWalletAdapterEntrypoint', () => MobileWalletAdapterEntrypointBottomSheet);
