import { requireNativeView } from 'expo';
import type { Ref } from 'react';
import type { NativeSyntheticEvent, ViewProps } from 'react-native';

/** What the camera turned out to support and deliver on this phone. */
export type CameraInfo = {
  /** Stream size, in the camera's own (landscape) orientation. */
  width: number;
  height: number;
  renderer: 'preview' | 'gpu';
  /** The GPU path failed to start here and the app fell back to the other. */
  gpuFailed: boolean;
  meteringArea: boolean;
  facePriority: boolean;
  exposureCompensation: boolean;
  /** Held light uses the camera's own lock (otherwise, the compensation). */
  exposureLock: boolean;
};

/** Where the finger that opened the shortcuts is, in dp over the view. */
export type ShortcutsDrag = {
  x: number;
  y: number;
  /** The finger let go (or the touch was taken away, with [cancelled]). */
  end: boolean;
  cancelled: boolean;
};

/** What a ref to the view can do. */
export type MirrorViewRef = {
  /** Freezes or unfreezes, like a long press. */
  toggleFreeze(): Promise<boolean>;
};

export type MirrorViewProps = ViewProps & {
  ref?: Ref<MirrorViewRef>;
  autoLight: boolean;
  /** 0 to 4: how far the light has to change before it is metered again (2 by default). */
  lightSensitivity?: number;
  facePriority: boolean;
  highResolution: boolean;
  gpuSharpening: boolean;
  /** What the GPU path does in its second pass ("natural" by default). */
  sharpening?: 'smooth' | 'natural' | 'detail';
  trueView: boolean;
  /** A light tick when the pinch reaches 1× or the top zoom (on by default). */
  zoomHaptics?: boolean;
  /** A quick one-finger drag keeps sliding a little (on by default). */
  dragInertia?: boolean;
  /** Opens at the zoom and position it was left at (on by default). */
  rememberZoom?: boolean;
  /** The volume keys zoom instead of changing the volume (on by default). */
  volumeZoom?: boolean;
  /** The screen as a light: full brightness and a white border (off by default). */
  fillLight?: boolean;
  /** An inward swipe from the right edge, at the zoom circle's height. */
  onShortcutsGesture?: () => void;
  /** The same finger, until it lets go: to pick a shortcut by dragging onto it. */
  onShortcutsDrag?: (event: NativeSyntheticEvent<ShortcutsDrag>) => void;
  onFreezeChange?: (event: NativeSyntheticEvent<{ frozen: boolean }>) => void;
  onCameraInfo?: (event: NativeSyntheticEvent<CameraInfo>) => void;
};

const NativeMirrorView = requireNativeView<MirrorViewProps>('MirrorView');

/** The front camera, full size, with every gesture of the mirror. Android only. */
export function MirrorView(props: MirrorViewProps) {
  return <NativeMirrorView {...props} />;
}
