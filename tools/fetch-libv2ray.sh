#!/usr/bin/env bash
# Put libv2ray.aar in V2rayNG/app/libs for the AndroidLibXrayLite commit this tree pins.
#
# Upstream publishes the AAR as a release asset of AndroidLibXrayLite, tagged with the newest tag
# reachable from its HEAD. That tag is not the commit a build pins: on 2026-10-01 PattNG moved the
# submodule to a commit four days newer than the newest release, v26.9.27, whose AAR predates the
# native exit-inbound work its Kotlin calls. So downloading by tag succeeds and hands over an AAR
# without Libv2ray.openExit, and the failure is an unresolved reference at compile time that names
# neither the tag nor the AAR -- which reads as a broken merge rather than a stale artifact.
#
# So the release is used only when it actually describes the pinned commit; otherwise the AAR is
# built from the submodule with the recipe in AndroidLibXrayLite's own workflow.
set -euo pipefail

sub="$(cd "$(dirname "$0")/.." && pwd)/AndroidLibXrayLite"
out="$(cd "$(dirname "$0")/.." && pwd)/V2rayNG/app/libs/libv2ray.aar"
mkdir -p "$(dirname "$out")"

[ -d "$sub/.git" ] || [ -f "$sub/.git" ] || {
  echo "AndroidLibXrayLite is not checked out; actions/checkout needs submodules: recursive" >&2
  exit 1
}

tag=$(git -C "$sub" describe --tags --abbrev=0)
at=$(git -C "$sub" rev-parse "$tag^{commit}")
pin=$(git -C "$sub" rev-parse HEAD)

if [ "$at" = "$pin" ]; then
  echo "the pinned commit is $tag itself, so its released AAR describes it"
  curl -fsSL --retry 3 -o "$out" \
    "https://github.com/patterniha/AndroidLibXrayLite/releases/download/$tag/libv2ray.aar"
else
  echo "the pinned commit $pin is $tag + $(git -C "$sub" rev-list --count "$at..$pin") commit(s);"
  echo "no released AAR describes it, so building libv2ray.aar from the submodule"
  export ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_HOME:?}/ndk/29.0.14206865}"
  [ -d "$ANDROID_NDK_HOME" ] || {
    echo "no NDK at $ANDROID_NDK_HOME; the calling workflow has to install ndk;29.0.14206865" >&2
    exit 1
  }
  go install golang.org/x/mobile/cmd/gomobile@latest
  PATH="$PATH:$(go env GOPATH)/bin"
  export PATH
  pushd "$sub" >/dev/null
  mkdir -p assets data
  bash gen_assets.sh download
  cp -v data/*.dat assets/
  gomobile init
  go mod tidy
  gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid= -checklinkname=0' ./
  popd >/dev/null
  cp "$sub/libv2ray.aar" "$out"
fi

# The AAR is the only thing that defines Libv2ray, so a build with the wrong one is a build whose
# Kotlin does not resolve rather than a build that quietly ships the wrong core.
unzip -l "$out" classes.jar >/dev/null || {
  echo "$out is not a readable AAR" >&2
  exit 1
}
echo "libv2ray.aar: $(du -h "$out" | cut -f1)"