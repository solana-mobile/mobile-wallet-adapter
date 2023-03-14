import React from 'react';
import {StyleSheet, View} from 'react-native';
import {Appbar, Text} from 'react-native-paper';

export default function MainScreen() {
  return (
    <>
      <Appbar.Header elevated mode="center-aligned">
        <Appbar.Content title="React Native Wallet" />
      </Appbar.Header>
      <View
        style={{
          height: '90%',
          justifyContent: 'center',
          alignItems: 'center',
        }}>
        <Text>I'm a Wallet!</Text>
      </View>
    </>
  );
}
