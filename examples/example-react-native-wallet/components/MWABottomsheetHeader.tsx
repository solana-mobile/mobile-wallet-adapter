import React from 'react';
import {StyleSheet, View, Image} from 'react-native';
import {Divider, Text} from 'react-native-paper';

interface MWABottomsheetHeaderProps {
  title: String;
  cluster: String;
  appIdentity: AppIdentity;
  children?: React.ReactNode;
}

type AppIdentity = Readonly<{
  identityUri?: string;
  iconRelativeUri?: string;
  identityName?: string;
}>;

export default function MWABottomsheetHeader({
  title,
  cluster,
  appIdentity,
  children,
}: MWABottomsheetHeaderProps) {
  const iconSource =
    appIdentity?.iconRelativeUri && appIdentity.identityUri
      ? {
          uri: new URL(
            appIdentity.iconRelativeUri,
            appIdentity.identityUri,
          ).toString(),
        }
      : require('../img/unknownapp.jpg');

  return (
    <>
      {iconSource ? (
        <View style={styles.headerImage}>
          <Image source={iconSource} style={styles.icon} />
        </View>
      ) : null}
      <Text style={styles.header}>{title}</Text>
      <Divider style={styles.spacer} />
      <View style={styles.metadataSection}>
        <Text style={styles.metadataHeader}>Request Metadata</Text>
        <Text style={styles.metadataText}>Cluster: {cluster}</Text>
        <Text style={styles.metadataText}>
          App name: {appIdentity?.identityName}
        </Text>
        <Text style={styles.metadataText}>
          App URI: {appIdentity?.identityUri}
        </Text>
      </View>
      <View style={styles.childrenContainer}>{children}</View>
      <Divider style={styles.spacer} />
    </>
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
  metadataSection: {
    display: 'flex',
    flexDirection: 'column',
    backgroundColor: '#d3d3d3', // light gray background
    borderRadius: 8, // rounded corners
    padding: 10, // tight padding
    marginVertical: 10, // some vertical margin
  },
  metadataText: {
    textAlign: 'left',
    color: 'black',
    fontSize: 16,
  },
  metadataHeader: {
    textAlign: 'left',
    color: 'black',
    fontWeight: 'bold',
    fontSize: 18,
  },
  spacer: {
    marginVertical: 16,
    width: '100%',
    height: 1,
  },
});
