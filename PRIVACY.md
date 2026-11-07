# Privacy

Packet Guard has no server, no account, no ads and no analytics. The developer collects nothing about you or your device, and the app contains no tracking or crash-reporting code.

## What the app connects to

- **The sites you open.** Your traffic goes straight from your phone to the sites, through the bypass running on the phone. Nothing is relayed through a server of ours.
- **Your DNS server**, in VPN mode, to look up site addresses. It is 1.1.1.1 (Cloudflare) unless you choose another one, or the system default, under Settings → General.
- **The sites in your switched-on lists**, during a Benchmark or a command line search, and for the Connect screen's latency check while protection is on (up to three sites, every 20 seconds). If no list is on, the check uses google.com. The sites you pick see these requests, as they would from any browser.
- **GitHub** (api.github.com and github.com), to check for a newer release and to download its APK. This happens a few seconds after the app opens and every 6 hours while it stays open. You can switch it off under Settings → About → Updates. GitHub sees your IP address and the app name "PacketGuard-Updater", as any website would.

The app does not encrypt your traffic or hide your IP address from the sites you visit or from your network provider.

## What is stored on your phone

- Your settings (mode, DNS, chosen apps, command line and so on)
- Saved strategies and their last test results
- Site lists, including any you add
- Benchmark results

This stays in the app's private storage and is removed when you uninstall the app. **Settings → About → Export settings** writes a file only where you choose to save it. **Diagnostic report** is shown for you to copy; it is not sent anywhere unless you paste it yourself, for example into a bug report. It contains your device model, Android version, app version and your settings, but not your browsing.

## Backups

Packet Guard allows Android's automatic backup and does not restrict it (its backup rules are empty), so on phones where Android backup is switched on, its settings and files may be included in your Google or device backup. That is handled by Android and your backup provider, not by Packet Guard.

## Permissions

- **Internet and network state**: to connect to the sites and GitHub as described above.
- **VPN** (Android's VPN permission, asked once): to send your phone's traffic, or only the apps you choose, through the bypass on the phone. It is not a VPN to a remote server.
- **Notifications**: to show that protection is running.
- **Foreground service**: to keep the bypass and tests running in the background.
- **Run at start-up**: to turn protection on after the phone restarts, only if you switch that on.
- **Ignore battery optimisations**: asked only if you choose it, so Android does not stop the bypass.
- **Install packages**: to install updates you download in the app. Android asks you to confirm every install.
- **See all installed apps**: for the list of apps in per-app mode. The list is used on the phone only.

## Contact

Questions or concerns: https://github.com/S1rHades/Packet-Gaurd-1.0/issues
