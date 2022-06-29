/**
 * Metro configuration for React Native
 * https://github.com/facebook/react-native
 *
 * @format
 */

const exclusionList = require('metro-config/src/defaults/exclusionList');
const getWorkspaces = require('get-yarn-workspaces');
const path = require('path');

function getConfig(appDir) {
  const workspaces = getWorkspaces(appDir).filter(
    workspaceDir => !(workspaceDir === appDir),
  );

  // Add additional Yarn workspace package roots to the module map
  // https://bit.ly/2LHHTP0
  const watchFolders = [
    path.resolve(appDir), // The present directory.
    path.resolve(appDir, '../../node_modules'), // The Lerna root.
    ...workspaces,
  ];

  return {
    watchFolders,
    resolver: {
      blockList: exclusionList([
        new RegExp(
          path.resolve(appDir, '../../node_modules/react-native') + '/.*',
        ),
        ...workspaces.map(
          workspacePath =>
            new RegExp(`${workspacePath}/node_modules/react-native/.*`),
        ),
      ]),
      extraNodeModules: {
        // Resolve all react-native module imports to the locally-installed version
        'react-native': path.resolve(appDir, 'node_modules', 'react-native'),
      },
    },
  };
}

module.exports = getConfig(__dirname);
