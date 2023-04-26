import React from 'react';
import {StyleSheet, View, Image, ImageSourcePropType} from 'react-native';
import {Text} from 'react-native-paper';

interface BottomsheetHeaderProps {
  title: String;
  iconSource?: ImageSourcePropType;
}

export default function BottomsheetHeader({
  title,
  iconSource = null,
}: BottomsheetHeaderProps) {
  return (
    <View>
      {iconSource ? (
        <View style={styles.headerImage}>
          <Image source={iconSource} style={styles.icon} />
        </View>
      ) : null}
      <Text style={styles.header}>{title}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  icon: {
    width: 75,
    height: 75,
  },
  header: {
    textAlign: 'center',
    color: 'black',
    fontSize: 32,
  },
  headerImage: {
    alignSelf: 'center',
    paddingBottom: 8,
  },
});
