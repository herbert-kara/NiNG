#!/bin/bash
# Keep the Poco F4 attached to adb for the length of this session.
#
# The device is reached over USB, so the transport survives the app being killed, the screen going
# off and the build running for ten minutes. What does break it is the phone restarting, USB
# debugging being toggled, or the cable moving. This re-connects when that happens and logs when
# the device goes away, so a later failure is not mistaken for a code problem.
SERIAL=6bf60932
while true; do
  if adb devices | tr -d '\r' | grep -q "^${SERIAL}[[:space:]]"; then
    sleep 20
    continue
  fi
  echo "[$(date +%H:%M:%S)] ${SERIAL} is gone; reconnecting"
  adb connect "${SERIAL}" >/dev/null 2>&1
  adb wait-for-device 2>/dev/null
  sleep 5
  if adb devices | tr -d '\r' | grep -q "^${SERIAL}[[:space:]]"; then
    echo "[$(date +%H:%M:%S)] ${SERIAL} back"
  fi
done
