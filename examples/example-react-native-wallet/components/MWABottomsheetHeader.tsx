import React from 'react';
import {StyleSheet, View, Image} from 'react-native';
import {Divider, Text} from 'react-native-paper';
import { 
  VerificationFailed, 
  VerificationInProgress, 
  VerificationState, 
  VerificationSucceeded 
} from '../utils/ClientTrustUseCase';

interface MWABottomsheetHeaderProps {
  title: String;
  cluster: String;
  appIdentity: AppIdentity;
  verificationState?: VerificationState | undefined;
  children?: React.ReactNode;
}

type AppIdentity =
  | Readonly<{
      identityUri?: string;
      iconRelativeUri?: string;
      identityName?: string;
    }>
  | undefined
  | null;

export default function MWABottomsheetHeader({
  title,
  cluster,
  appIdentity,
  verificationState,
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

  let statusText = <Text style={styles.metadataText}>Status: Verification in progress </Text>
  if (verificationState instanceof VerificationSucceeded) {
    statusText = <Text style={styles.metadataText}>Status: Verification Succeeded </Text>
  } else if (verificationState instanceof VerificationFailed) {
    statusText = <Text style={styles.metadataText}>Status: Verification Failed </Text>
  }

  const verificationStatusText = function(): string {
    if (verificationState instanceof VerificationFailed) return 'Verification Failed'
    if (verificationState instanceof VerificationSucceeded) return 'Verification Succeeded'
    else return 'Verification in progress'
  }

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
        {verificationState && <Text style={styles.metadataText}>Status: {verificationStatusText()}</Text>}
        {verificationState && <Text style={styles.metadataText}>Scope: {verificationState?.authorizationScope}</Text>}
      </View>
      <View>{children}</View>
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
