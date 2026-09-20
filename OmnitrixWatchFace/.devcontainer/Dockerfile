# Reproducible build sandbox for the Omnitrix watch face.
# Pins JDK + Android SDK components so `./gradlew` behaves identically on any
# machine, without installing anything globally on the host.
#
# Scope: build tooling + adb only. It does NOT bundle the Wear OS emulator
# system image (1GB+, x86_64-only, and hits a known image-decoder limitation
# in that image regardless of container vs host) — adb connects out to either
# a real watch or an emulator running on the host instead.

FROM eclipse-temurin:17-jdk-jammy

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        unzip \
        curl \
    && rm -rf /var/lib/apt/lists/*

# Android SDK Command-line Tools (revision 23.0, current as of this Dockerfile).
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" \
    && curl -sL -o /tmp/cmdline-tools.zip \
        "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip" \
    && unzip -q /tmp/cmdline-tools.zip -d "$ANDROID_HOME/cmdline-tools" \
    && mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" \
    && rm /tmp/cmdline-tools.zip

ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# Pin exactly the SDK components wear-app/build.gradle.kts needs.
RUN yes | sdkmanager --licenses > /dev/null \
    && sdkmanager \
        "platform-tools" \
        "platforms;android-35" \
        "build-tools;35.0.0"

WORKDIR /workspace
