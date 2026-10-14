package org.librepavlok.offline;

/** Versioned, offline notice shown before the companion or Bluetooth controller starts. */
public final class AppDisclaimer {
    public static final int VERSION=1;
    public static final String TEXT=
            "LibrePavlok is experimental software that controls a Bluetooth wearable, including electric shocks. "
            +"Use can cause pain, injury, device or property damage, missed alarms, or other loss. "
            +"You choose whether to use it and are responsible for your settings and use.\n\n"
            +"The app is provided AS IS, without warranties of any kind, including fitness for a particular purpose. "
            +"Correct operation, alarm delivery and compatibility are not guaranteed.\n\n"
            +"To the maximum extent permitted by applicable law, the author and contributors disclaim liability "
            +"for any injury, damage or loss arising from use of the app. This does not exclude liability "
            +"that cannot legally be excluded, including liability for intent, gross negligence, or "
            +"negligent injury to life, body or health where such exclusion is prohibited.";
    private AppDisclaimer() { }
}
