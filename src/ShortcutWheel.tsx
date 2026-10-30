import { useEffect, useState } from 'react';
import {
  Animated,
  Image,
  type ImageSourcePropType,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';

import { colors } from './lib/colors';
import { strings } from './lib/strings';
import type { SharpeningType } from './store/settings';

export type Shortcut = 'fillLight' | 'sharpening' | 'freeze' | 'settings';

/** Top to bottom along the wheel. */
const SHORTCUTS: Shortcut[] = ['fillLight', 'sharpening', 'freeze', 'settings'];

const ICONS: Record<Shortcut, ImageSourcePropType> = {
  fillLight: require('../assets/sun.png'),
  sharpening: require('../assets/sparkle.png'),
  freeze: require('../assets/snowflake.png'),
  settings: require('../assets/gear.png'),
};

/** How long the wheel waits, after its last change, before it fades away. */
const VISIBLE_MS = 4000;
const SIZE = 56;
const RADIUS = 96;
/** How close to a button's centre (dp) a finger has to let go to pick it. */
const HIT_RADIUS = 34;
/** Same height as the native zoom circle: its centre sits 27% up. */
const ANCHOR_FROM_TOP = 0.73;

export type Size = { width: number; height: number };

/** Centre of each button, in dp: a half circle opening left from the right edge. */
function centres({ width, height }: Size) {
  const anchorY = height * ANCHOR_FROM_TOP;
  return SHORTCUTS.map((_, index) => {
    const angle = Math.PI / 2 + (Math.PI * (index + 0.5)) / SHORTCUTS.length;
    return { x: width + RADIUS * Math.cos(angle), y: anchorY - RADIUS * Math.sin(angle) };
  });
}

/** The shortcut a finger letting go at this point (dp) would pick, if any. */
export function shortcutAt(x: number, y: number, size: Size): Shortcut | null {
  const index = centres(size).findIndex(
    (centre) => Math.hypot(centre.x - x, centre.y - y) <= HIT_RADIUS,
  );
  return index >= 0 ? SHORTCUTS[index] : null;
}

type Props = {
  visible: boolean;
  size: Size;
  /** Under the finger that opened the wheel and is still dragging. */
  hovered: Shortcut | null;
  fillLight: boolean;
  frozen: boolean;
  sharpening: SharpeningType;
  sharpeningAvailable: boolean;
  onPick: (shortcut: Shortcut) => void;
  onClose: () => void;
};

/**
 * The shortcuts, on an inward swipe from the right edge: a half circle of
 * pixel-art buttons around the zoom circle's height. A tap picks one; so does
 * dragging the same finger onto it and letting go. A tap anywhere else, or a
 * few seconds without use, closes it.
 */
export function ShortcutWheel({
  visible,
  size,
  hovered,
  fillLight,
  frozen,
  sharpening,
  sharpeningAvailable,
  onPick,
  onClose,
}: Props) {
  const [opacity] = useState(() => new Animated.Value(0));

  useEffect(() => {
    Animated.timing(opacity, {
      toValue: visible ? 1 : 0,
      duration: visible ? 120 : 200,
      useNativeDriver: true,
    }).start();
    if (!visible) {
      return;
    }
    // Restarted by any change on the wheel: a type picked, a finger over it.
    const timer = setTimeout(onClose, VISIBLE_MS);
    return () => clearTimeout(timer);
  }, [visible, opacity, onClose, hovered, sharpening, fillLight, frozen]);

  const points = centres(size);
  const labels: Record<Shortcut, string> = {
    fillLight: strings.shortcuts.fillLight,
    sharpening: sharpeningAvailable
      ? `${strings.shortcuts.sharpening}: ${strings.sharpening.types[sharpening][0]}`
      : strings.shortcuts.sharpeningOff,
    freeze: frozen ? strings.shortcuts.unfreeze : strings.shortcuts.freeze,
    settings: strings.settings,
  };

  return (
    <Animated.View
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      pointerEvents={visible ? 'box-none' : 'none'}
      style={[StyleSheet.absoluteFill, { opacity }]}
    >
      {visible ? (
        <Pressable
          accessibilityLabel={strings.shortcuts.close}
          onPress={onClose}
          style={StyleSheet.absoluteFill}
        />
      ) : null}
      {SHORTCUTS.map((shortcut, index) => {
        const disabled = shortcut === 'sharpening' && !sharpeningAvailable;
        const active = (shortcut === 'fillLight' && fillLight) || (shortcut === 'freeze' && frozen);
        const point = points[index];
        return (
          <View
            key={shortcut}
            pointerEvents="box-none"
            style={[styles.slot, { left: point.x - SIZE / 2, top: point.y - SIZE / 2 }]}
          >
            <View pointerEvents="none" style={styles.label}>
              <Text numberOfLines={1} style={styles.labelText}>
                {labels[shortcut]}
              </Text>
            </View>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={labels[shortcut]}
              accessibilityState={{ disabled, selected: active }}
              disabled={disabled}
              hitSlop={6}
              onPress={() => onPick(shortcut)}
              style={[
                styles.button,
                active && styles.active,
                hovered === shortcut && styles.hovered,
                disabled && styles.disabled,
              ]}
            >
              <Image source={ICONS[shortcut]} style={styles.icon} />
            </Pressable>
          </View>
        );
      })}
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  slot: {
    position: 'absolute',
    width: SIZE,
    height: SIZE,
  },
  label: {
    position: 'absolute',
    right: SIZE + 8,
    top: SIZE / 2 - 12,
    height: 24,
    justifyContent: 'center',
    paddingHorizontal: 8,
    backgroundColor: colors.bubble,
  },
  labelText: {
    color: colors.white,
    fontSize: 13,
    fontWeight: '600',
  },
  button: {
    width: SIZE,
    height: SIZE,
    borderRadius: SIZE / 2,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: colors.bubble,
    borderWidth: 1.5,
    borderColor: colors.bubbleBorder,
  },
  active: {
    borderWidth: 3,
    borderColor: colors.gold,
  },
  hovered: {
    backgroundColor: colors.slate,
    transform: [{ scale: 1.15 }],
  },
  disabled: {
    opacity: 0.4,
  },
  icon: {
    width: 32,
    height: 32,
  },
});
