import { NativeModules, Platform } from 'react-native';

const LINKING_ERROR =
    `The package 'solana-mobile-wallet-adapter-walletlib' doesn't seem to be linked. Make sure: \n\n` +
    '- You rebuilt the app after installing the package\n' +
    '- If you are using Lerna workspaces\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` as an explicit dependency, and\n' +
    '  - You have added `@solana-mobile/mobile-wallet-adapter-walletlib` to the `nohoist` section of your package.json\n' +
    '- You are not using Expo managed workflow\n';

const SolanaMobileDigitalAssetLinks =
    Platform.OS === 'android' && NativeModules.SolanaMobileDigitalAssetLinks
        ? NativeModules.SolanaMobileDigitalAssetLinks
        : new Proxy(
              {},
              {
                  get() {
                      throw new Error(
                          Platform.OS !== 'android'
                              ? 'The package `solana-mobile-wallet-adapter-walletlib` is only compatible with React Native Android'
                              : LINKING_ERROR,
                      );
                  },
              },
          );

export async function getCallingPackage(): Promise<string | undefined> {
    return await SolanaMobileDigitalAssetLinks.getCallingPackage()
}

export async function verifyCallingPackage(clientIdentityUri: string) {
    return await SolanaMobileDigitalAssetLinks.verifyCallingPackage(clientIdentityUri)
}

export async function getCallingPackageUid() {
    return await SolanaMobileDigitalAssetLinks.getCallingPackageUid()
}

export async function getUidForPackage(packageName: string) {
    return await SolanaMobileDigitalAssetLinks.getUidForPackage(packageName)
}