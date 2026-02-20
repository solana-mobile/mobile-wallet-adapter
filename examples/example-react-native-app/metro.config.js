const {getDefaultConfig, mergeConfig} = require('@react-native/metro-config');
// const path = require('path');

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = {
  resolver: {
    unstable_enableSymlinks: true, // this enable the use of Symlinks
    // nodeModulesPaths: [
    //     path.resolve(__dirname, 'node_modules'),
    //     path.join(__dirname, '..', '..', 'js/node_modules'),
    // ]
  },
  // this specifies the folder where are located the node_modules for the project
//   watchFolders: [path.join(__dirname, '..', '..', 'js')],
};

module.exports = mergeConfig(getDefaultConfig(__dirname), config);
