# Bootstrap rootfs — auto-installed at first launch

In v2, `OnboardingActivity` runs `BootstrapInstaller.install()` which downloads
the right Alpine Linux minirootfs for the device's primary ABI (`arm64-v8a` ->
`aarch64` build, `armeabi-v7a` -> `armv7` build) directly from Alpine's CDN. It
also runs `PRootInstaller.install()` to fetch a static `proot` binary so we can
chroot into the rootfs without needing `su`.

**You don't need to drop anything in this folder before building.**

If you'd rather ship offline (no network at first launch), drop these files
here and the installer will pick them up before going to the network:

```
bootstrap/
├── alpine-arm64.tar.gz   (for arm64-v8a)
├── alpine-armv7.tar.gz   (for armeabi-v7a)
├── proot-arm64           (static binary)
└── proot-armv7
```

## Where the auto-download fetches from

- Alpine: `https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/{aarch64,armv7}/alpine-minirootfs-3.20.3-{aarch64,armv7}.tar.gz`
- PRoot:  `https://proot.gitlab.io/proot/bin/proot-{aarch64,armv7l}`

Bump versions in `BootstrapDownloader.kt` (`ALPINE_VERSION`) and
`PRootInstaller.kt` (URLs) when newer releases ship.

## Inside the rootfs

Once installed, open the in-app terminal and run:

```
apk update
apk add gcc musl-dev make python3 git nano vim go rust
```

Anything Alpine offers via `apk` works. Network access for `apk` requires the
INTERNET permission (already declared) and an active connection.

## Root vs PRoot vs Direct mode

`TerminalSessionProvider` decides at session creation:

| Settings              | Mode    | What runs                                                |
|-----------------------|---------|----------------------------------------------------------|
| useRoot=on, su found  | root    | `su -c <bootstrap>/bin/bash`                             |
| useProot=on, proot ok | proot   | `proot -r <bootstrap> -0 -b /proc -b /dev ... /bin/sh`   |
| neither               | direct  | `/system/bin/sh -i` with PATH/LD_LIBRARY_PATH augmented  |

The active mode is exposed as the `LOWLEVEL_MODE` environment variable inside
the shell, so scripts can detect it.
