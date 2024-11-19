import { TurboModule, TurboModuleRegistry } from 'react-native';

export interface Spec extends TurboModule {
    startSession(config?: { baseUri?: string }): Promise<{
        protocol_version: 'legacy' | 'v1';
    }>;

    invoke(method: string, params: Object | undefined): Promise<Object>;

    endSession(): Promise<boolean>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('SolanaMobileWalletAdapter') as Spec;
