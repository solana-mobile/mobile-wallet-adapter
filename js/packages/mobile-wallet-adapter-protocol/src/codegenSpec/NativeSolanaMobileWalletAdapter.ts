import { TurboModule, TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
    startSession(config?: { baseUri?: string }): Promise<{
        protocol_version: 'legacy' | 'v1';
    }>;

    // React Native codegen rejects the primitive `object` type in TurboModule specs.
    // eslint-disable-next-line @typescript-eslint/no-wrapper-object-types
    invoke(method: string, params: Object | undefined): Promise<Object>;

    endSession(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SolanaMobileWalletAdapter') as Spec;
