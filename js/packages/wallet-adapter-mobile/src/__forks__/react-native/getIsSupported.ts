import { Platform } from 'react-native';

export function getIsSupported() {
    return Platform.OS === 'android';
}

/**
 * @deprecated Use {@link getIsSupported} instead.
 */
export default getIsSupported;
