import { Platform } from 'react-native';

export default function getIsSupported() {
    return Platform.OS === 'android';
}
