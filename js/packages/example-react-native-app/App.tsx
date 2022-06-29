import React from 'react';
import {SafeAreaView, StyleSheet, Text} from 'react-native';

const App = () => {
  return (
    <SafeAreaView style={styles.shell}>
      <Text>TODO</Text>
    </SafeAreaView>
  );
};

const styles = StyleSheet.create({
  shell: {
    alignItems: 'center',
    display: 'flex',
    height: '100%',
    justifyContent: 'center',
  },
});

export default App;
