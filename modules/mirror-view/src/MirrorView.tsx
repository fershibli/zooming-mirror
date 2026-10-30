import { requireNativeView } from 'expo';
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

export type MirrorViewProps = ViewProps & {
  autoLight: boolean;
  /** 0 to 4: how far the light has to change before it is metered again (2 by default). */
  lightSensitivity?: number;
  facePriority: boolean;
  highResolution: boolean;
  gpuSharpening: boolean;
  trueView: boolean;
  /** A light tick when the pinch reaches 1× or the top zoom (on by default). */
  zoomHaptics?: boolean;
  /** An inward swipe from the right edge, at the zoom circle's height. */
  onSettingsGesture?: () => void;
  onCameraInfo?: (event: NativeSyntheticEvent<CameraInfo>) => void;
};

const NativeMirrorView = requireNativeView<MirrorViewProps>('MirrorView');

/** The front camera, full size, with every gesture of the mirror. Android only. */
export function MirrorView(props: MirrorViewProps) {
  return <NativeMirrorView {...props} />;
}
