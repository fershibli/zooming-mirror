import Constants from 'expo-constants';
import { Fragment, useEffect, useState } from 'react';
import {
  Animated,
  BackHandler,
  Image,
  type NativeScrollEvent,
  type NativeSyntheticEvent,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  View,
} from 'react-native';

import type { CameraInfo } from '../modules/mirror-view';
import { colors } from './lib/colors';
import { strings } from './lib/strings';
import {
  SENSITIVITY_LEVELS,
  SHARPENING_TYPES,
  type ToggleKey,
  useSettings,
} from './store/settings';

/** The switches below the light ones. */
const ORDER: ToggleKey[] = ['facePriority', 'highResolution', 'gpuSharpening', 'trueView'];

/** The zoom's own extras, each one easy to turn off. */
const ZOOM_ORDER: ToggleKey[] = ['volumeZoom', 'dragInertia', 'rememberZoom', 'zoomHaptics'];

/** Finger speed (dp per ms) of a fling at the end of the list that closes it. */
const CLOSE_FLING = 0.8;
/** How close to the end (dp) still counts as the end. */
const END_SLOP = 4;

/** False only when the camera said it cannot; unknown counts as available. */
function isAvailable(key: ToggleKey, camera: CameraInfo | null): boolean {
  if (!camera) {
    return true;
  }
  switch (key) {
    case 'autoLight':
      return camera.meteringArea || camera.exposureCompensation || camera.exposureLock;
    case 'facePriority':
      return camera.facePriority;
    case 'gpuSharpening':
      return !camera.gpuFailed;
    default:
      return true;
  }
}

type Props = {
  visible: boolean;
  camera: CameraInfo | null;
  onClose: () => void;
};

/** Full-screen settings at 90% opacity, over the still-running camera. */
export function Settings({ visible, camera, onClose }: Props) {
  const [opacity] = useState(() => new Animated.Value(0));

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: visible ? 1 : 0,
      duration: 160,
      useNativeDriver: true,
    }).start();
  }, [visible, opacity]);

  useEffect(() => {
    if (!visible) {
      return;
    }
    const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
      onClose();
      return true;
    });
    return () => subscription.remove();
  }, [visible, onClose]);

  // Flinging on past the end of the list closes it, like a sheet sliding away.
  const closeOnFlingPastEnd = (event: NativeSyntheticEvent<NativeScrollEvent>) => {
    const { contentOffset, contentSize, layoutMeasurement, velocity } = event.nativeEvent;
    const atEnd = contentOffset.y + layoutMeasurement.height >= contentSize.height - END_SLOP;
    // The finger's speed: negative while it pushes the list on towards its end.
    if (atEnd && velocity && velocity.y < -CLOSE_FLING) {
      onClose();
    }
  };

  const version = Constants.expoConfig?.version;
  const autoLight = useSettings((state) => state.autoLight);
  const gpuSharpening = useSettings((state) => state.gpuSharpening);
  const lightHeld = useSettings(
    (state) => !state.autoLight || state.lightSensitivity < SENSITIVITY_LEVELS - 1,
  );

  return (
    // Stays mounted so it can fade out; while closed it takes no touches and is
    // hidden from screen readers.
    <Animated.View
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      pointerEvents={visible ? 'auto' : 'none'}
      style={[styles.overlay, { opacity }]}
    >
      <ScrollView contentContainerStyle={styles.content} onScrollEndDrag={closeOnFlingPastEnd}>
        <View style={styles.header}>
          <Image source={require('../assets/gear.png')} style={styles.headerIcon} />
          <Text style={styles.title}>{strings.settings}</Text>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={strings.close}
            hitSlop={16}
            onPress={onClose}
          >
            <Text style={styles.close}>✕</Text>
          </Pressable>
        </View>

        <SettingRow setting="autoLight" available={isAvailable('autoLight', camera)} />
        <SensitivityRow available={autoLight} />
        {ORDER.map((key) => (
          <Fragment key={key}>
            <SettingRow setting={key} available={isAvailable(key, camera)} />
            {key === 'gpuSharpening' ? (
              <SharpeningRow available={gpuSharpening && isAvailable(key, camera)} />
            ) : null}
          </Fragment>
        ))}
        {camera?.gpuFailed ? <Text style={styles.warning}>{strings.gpuFailed}</Text> : null}

        <Text style={styles.section}>{strings.zoomTitle}</Text>
        {ZOOM_ORDER.map((key) => (
          <SettingRow key={key} setting={key} available />
        ))}

        <Text style={styles.section}>{strings.gesturesTitle}</Text>
        {strings.gestures.map(([gesture, effect]) => (
          <View key={gesture} style={styles.gesture}>
            <Text style={styles.gestureName}>{gesture}</Text>
            <Text style={styles.gestureEffect}>{effect}</Text>
          </View>
        ))}

        <View style={styles.footer}>
          {camera && camera.width > 0 ? (
            <Text style={styles.footerText}>
              {strings.camera(camera.width, camera.height, camera.renderer === 'gpu')}
            </Text>
          ) : null}
          {camera && camera.width > 0 && lightHeld ? (
            <Text style={styles.footerText}>{strings.lightHold(camera.exposureLock)}</Text>
          ) : null}
          {version ? <Text style={styles.footerText}>{strings.version(version)}</Text> : null}
        </View>
      </ScrollView>
    </Animated.View>
  );
}

function SettingRow({ setting, available }: { setting: ToggleKey; available: boolean }) {
  const value = useSettings((state) => state[setting]);
  const setSetting = useSettings((state) => state.setSetting);
  const { title, detail } = strings.options[setting];
  const toggle = () => setSetting(setting, !value);

  return (
    <Pressable
      accessibilityRole="switch"
      accessibilityState={{ checked: value && available, disabled: !available }}
      disabled={!available}
      onPress={toggle}
      style={[styles.row, !available && styles.rowDisabled]}
    >
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>{title}</Text>
        <Text style={styles.rowDetail}>{available ? detail : strings.unsupported}</Text>
      </View>
      <Switch
        disabled={!available}
        onValueChange={toggle}
        thumbColor={value && available ? colors.gold : colors.mist}
        trackColor={{ false: colors.slate, true: colors.orange }}
        value={value && available}
      />
    </Pressable>
  );
}

/**
 * Five bars, like a signal meter: tap one to pick that level. Screen readers
 * get it as one adjustable control.
 */
function SensitivityRow({ available }: { available: boolean }) {
  const level = useSettings((state) => state.lightSensitivity);
  const setSetting = useSettings((state) => state.setSetting);
  const { title, levels, onlyWithAutoLight } = strings.sensitivity;
  const [name, detail] = levels[level] ?? levels[0];
  const pick = (next: number) => {
    if (available) {
      setSetting('lightSensitivity', Math.max(0, Math.min(SENSITIVITY_LEVELS - 1, next)));
    }
  };

  return (
    <View
      accessible
      accessibilityRole="adjustable"
      accessibilityLabel={title}
      accessibilityValue={{ text: name }}
      accessibilityState={{ disabled: !available }}
      accessibilityActions={[{ name: 'increment' }, { name: 'decrement' }]}
      onAccessibilityAction={(event) =>
        pick(level + (event.nativeEvent.actionName === 'increment' ? 1 : -1))
      }
      style={[styles.row, !available && styles.rowDisabled]}
    >
      <View style={styles.rowText}>
        <Text style={styles.rowTitle}>
          {title} <Text style={styles.level}>{name}</Text>
        </Text>
        <Text style={styles.rowDetail}>{available ? detail : onlyWithAutoLight}</Text>
      </View>
      <View style={styles.bars}>
        {levels.map(([barName], index) => (
          <Pressable
            key={barName}
            disabled={!available}
            hitSlop={{ top: 12, bottom: 12 }}
            onPress={() => pick(index)}
            style={styles.barSlot}
          >
            <View
              style={[
                styles.bar,
                { height: BAR_MIN + index * BAR_STEP },
                available && index <= level ? styles.barOn : styles.barOff,
              ]}
            />
          </Pressable>
        ))}
      </View>
    </View>
  );
}

/** Three side-by-side choices for what the GPU path does after enlarging. */
function SharpeningRow({ available }: { available: boolean }) {
  const type = useSettings((state) => state.sharpening);
  const setSetting = useSettings((state) => state.setSetting);
  const { title, types, onlyWithGpu } = strings.sharpening;

  return (
    <View style={[styles.column, !available && styles.rowDisabled]}>
      <Text style={styles.rowTitle}>{title}</Text>
      <Text style={styles.rowDetail}>{available ? types[type][1] : onlyWithGpu}</Text>
      <View accessibilityRole="radiogroup" style={styles.segments}>
        {SHARPENING_TYPES.map((option) => {
          const selected = option === type;
          return (
            <Pressable
              key={option}
              accessibilityRole="radio"
              accessibilityState={{ checked: selected, disabled: !available }}
              disabled={!available}
              onPress={() => setSetting('sharpening', option)}
              style={[styles.segment, selected && available && styles.segmentOn]}
            >
              <Text style={[styles.segmentText, selected && available && styles.segmentTextOn]}>
                {types[option][0]}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const BAR_MIN = 8;
const BAR_STEP = 6;

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: colors.overlay,
  },
  content: {
    paddingTop: 56,
    paddingHorizontal: 24,
    paddingBottom: 48,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: 24,
  },
  headerIcon: {
    width: 32,
    height: 32,
    marginRight: 12,
  },
  title: {
    flex: 1,
    color: colors.white,
    fontSize: 24,
    fontWeight: '700',
  },
  close: {
    color: colors.mist,
    fontSize: 22,
    paddingHorizontal: 4,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.slate,
  },
  column: {
    paddingVertical: 14,
    borderBottomWidth: StyleSheet.hairlineWidth,
    borderBottomColor: colors.slate,
  },
  segments: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  // Square corners, like the pixel-art icons.
  segment: {
    flex: 1,
    alignItems: 'center',
    paddingVertical: 8,
    borderWidth: 1.5,
    borderColor: colors.slate,
  },
  segmentOn: {
    backgroundColor: colors.gold,
    borderColor: colors.gold,
  },
  segmentText: {
    color: colors.mist,
    fontSize: 14,
    fontWeight: '600',
  },
  segmentTextOn: {
    color: colors.night,
  },
  rowDisabled: {
    opacity: 0.5,
  },
  rowText: {
    flex: 1,
    paddingRight: 16,
  },
  rowTitle: {
    color: colors.white,
    fontSize: 16,
    fontWeight: '600',
  },
  level: {
    color: colors.gold,
  },
  bars: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    height: BAR_MIN + (SENSITIVITY_LEVELS - 1) * BAR_STEP,
  },
  barSlot: {
    width: 14,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'flex-end',
  },
  // Square corners, like the pixel-art gear.
  bar: {
    width: 8,
  },
  barOn: {
    backgroundColor: colors.gold,
  },
  barOff: {
    backgroundColor: colors.slate,
  },
  rowDetail: {
    color: colors.mist,
    fontSize: 13,
    marginTop: 3,
  },
  warning: {
    color: colors.orange,
    fontSize: 13,
    marginTop: 12,
  },
  section: {
    color: colors.gold,
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1,
    textTransform: 'uppercase',
    marginTop: 32,
    marginBottom: 8,
  },
  gesture: {
    flexDirection: 'row',
    paddingVertical: 6,
  },
  gestureName: {
    flex: 1,
    color: colors.white,
    fontSize: 14,
  },
  gestureEffect: {
    flex: 1,
    color: colors.mist,
    fontSize: 14,
  },
  footer: {
    marginTop: 32,
    gap: 4,
  },
  footerText: {
    color: colors.steel,
    fontSize: 12,
  },
});
