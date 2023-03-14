import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Appbar, Text} from 'react-native-paper';

export default function MainScreen() {
  return (
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          alignItems: 'center',
        }}>
        <Text variant='titleSmall'>Loading...</Text>
        <Text variant='bodySmall'>(replace me with spinner)</Text>
      </View>
  );
}
