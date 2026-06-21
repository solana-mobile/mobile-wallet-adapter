import { TurboModule, TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
    startSession(config?: { baseUri?: string; packageName?: string }): Promise<{
        protocol_version: 'legacy' | 'v1';
    }>;

    // React Native codegen rejects the primitive `object` type in TurboModule specs.
    // eslint-disable-next-line @typescript-eslint/no-wrapper-object-types
    invoke(method: string, params: Object | undefined): Promise<Object>;

    endSession(): Promise<boolean>;

    getInstalledWallets(): Promise<Array<{ packageName: string; appName: string }>>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SolanaMobileWalletAdapter') as Spec;
