const { withAppBuildGradle } = require('@expo/config-plugins');

/**
 * Teaches the generated android/app/build.gradle about a release keystore.
 *
 * `expo prebuild` rewrites the android folder from scratch, so the signing
 * config cannot simply be committed: it has to be re-applied on every build.
 * With no MYAPP_UPLOAD_* properties the release build is refused outright,
 * which is better than quietly shipping an APK signed with the debug key.
 */
const RELEASE_SIGNING_CONFIG = `        release {
            if (project.hasProperty('MYAPP_UPLOAD_STORE_FILE')) {
                def uploadStoreFile = file(MYAPP_UPLOAD_STORE_FILE)
                if (!uploadStoreFile.exists()) {
                    throw new GradleException("Release keystore not found: " + uploadStoreFile.absolutePath)
                }
                storeFile uploadStoreFile
                storePassword MYAPP_UPLOAD_STORE_PASSWORD
                keyAlias MYAPP_UPLOAD_KEY_ALIAS
                keyPassword MYAPP_UPLOAD_KEY_PASSWORD
            }
        }
`;

const RELEASE_SIGNING_BUILD_TYPE = `if (project.hasProperty('MYAPP_UPLOAD_STORE_FILE')) {
                signingConfig signingConfigs.release
            } else {
                signingConfig signingConfigs.debug
            }`;

const RELEASE_TASK_GUARD = `
gradle.taskGraph.whenReady { graph ->
    def runningRelease = graph.allTasks.any { it.name == "assembleRelease" || it.name == "bundleRelease" }
    if (runningRelease && !project.hasProperty('MYAPP_UPLOAD_STORE_FILE')) {
        throw new GradleException("Release signing is not configured. Set MYAPP_UPLOAD_STORE_FILE, MYAPP_UPLOAD_STORE_PASSWORD, MYAPP_UPLOAD_KEY_ALIAS and MYAPP_UPLOAD_KEY_PASSWORD.")
    }
}
`;

function alreadyApplied(gradle) {
  return (
    gradle.includes("if (project.hasProperty('MYAPP_UPLOAD_STORE_FILE'))") &&
    gradle.includes('gradle.taskGraph.whenReady')
  );
}

function applyReleaseBuildType(gradle) {
  if (gradle.includes(RELEASE_SIGNING_BUILD_TYPE)) {
    return gradle;
  }
  if (gradle.includes('signingConfig signingConfigs.release')) {
    return gradle.replace('signingConfig signingConfigs.release', RELEASE_SIGNING_BUILD_TYPE);
  }
  const withReleaseType = gradle.replace(
    /(buildTypes\s*\{[\s\S]*?release\s*\{[\s\S]*?)signingConfig signingConfigs\.debug/,
    `$1${RELEASE_SIGNING_BUILD_TYPE}`,
  );
  if (withReleaseType === gradle) {
    throw new Error('with-android-release-signing: could not set buildTypes.release.signingConfig');
  }
  return withReleaseType;
}

function applyReleaseSigning(gradle) {
  if (alreadyApplied(gradle)) {
    return gradle;
  }
  if (!/signingConfigs\s*\{/.test(gradle)) {
    throw new Error(
      'with-android-release-signing: signingConfigs block not found in android/app/build.gradle',
    );
  }
  const next = applyReleaseBuildType(
    gradle.replace(/signingConfigs\s*\{/, `signingConfigs {\n${RELEASE_SIGNING_CONFIG}`),
  );
  return `${next.trimEnd()}\n${RELEASE_TASK_GUARD}`;
}

function withAndroidReleaseSigning(config) {
  return withAppBuildGradle(config, (mod) => {
    mod.modResults.contents = applyReleaseSigning(mod.modResults.contents);
    return mod;
  });
}

module.exports = withAndroidReleaseSigning;
module.exports.applyReleaseSigning = applyReleaseSigning;
