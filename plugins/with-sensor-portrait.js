const { AndroidConfig, withAndroidManifest } = require('@expo/config-plugins');

/**
 * Portrait either way up, following the phone even with auto-rotate off.
 *
 * Holding the phone upside down puts the front camera at the bottom, to look
 * at the chin and neck from below. The picture and the gestures turn over with
 * it, so the gear and the zoom circle stay where the thumb is. Expo's
 * `orientation` only knows "portrait", which is the right way up only.
 */
module.exports = function withSensorPortrait(config) {
  return withAndroidManifest(config, (config) => {
    const activity = AndroidConfig.Manifest.getMainActivityOrThrow(config.modResults);
    activity.$['android:screenOrientation'] = 'sensorPortrait';
    return config;
  });
};
