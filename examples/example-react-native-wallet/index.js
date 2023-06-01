/**
 * @format
 */
import 'react-native-get-random-values'

import {AppRegistry} from 'react-native';
import App from './App';
import MobileWalletAdapterEntrypointBottomSheet from './screens/MobileWalletAdapterEntrypointBottomSheet';
import TestingEntrypointBottomSheet from './screens/TestingEntrypointBottomSheet';
import {name as appName} from './app.json';

// Mock event listener functions to prevent them from fataling.
window.addEventListener = () => {};
window.removeEventListener = () => {};

AppRegistry.registerComponent(appName, () => App);
// AppRegistry.registerComponent(
//   'MobileWalletAdapterEntrypoint',
//   () => TestingEntrypointBottomSheet,
// );

AppRegistry.registerComponent(
  'MobileWalletAdapterEntrypoint',
  () => MobileWalletAdapterEntrypointBottomSheet,
);
