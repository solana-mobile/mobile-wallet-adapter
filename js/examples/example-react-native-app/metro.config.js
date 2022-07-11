/**
 * Metro configuration for React Native
 * https://github.com/facebook/react-native
 *
 * @format
 */

const defaultSourceExts =
  require('metro-config/src/defaults/defaults').sourceExts;

/** @type { import("@types/metro-config").ConfigT } */
module.exports = {
  resolver: {
    // Add `.cjs` to allowable extensions. See https://github.com/facebook/metro/issues/535
    sourceExts: process.env.RN_SRC_EXT
      ? [...process.env.RN_SRC_EXT.split(',').concat(defaultSourceExts), 'cjs']
      : [...defaultSourceExts, 'cjs'],
  },
};
