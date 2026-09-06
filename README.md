# LogAI

LogAI is a tool that - as the name suggests - uses an AI of your choice to analyse your
crash details and tells you exactly what you need to do in order to avoid this exact problem.

Client-side Fabric mod. No API key, no backend, no account inside the mod - it uses the AI
account you already have, in your own browser.

## How it works

1. On startup the mod launches a small **companion process** in its own JVM and hands it the
   Minecraft process id. Because that process is not part of Minecraft, it survives every kind
   of crash - exception, freeze, native JVM crash, even a kill from the task manager.
2. On shutdown the mod records HOW the game ended: `QUIT` for the in-game quit button,
   `ALT_F4` when the key press was seen, `WINDOW_CLOSE` for the window X or the task manager
   (those two are indistinguishable to any program), and no marker at all when the process died
   outright. Minecraft.stop() never touches the GLFW close flag, so reading `Window.shouldClose()`
   separates the quit button from a force-close. Each of the four is a separate on/off setting.
3. On a crash it writes `logai/reports/LogAI-crash-<timestamp>.txt`: the prompt first, then the
   log, plus the `hs_err_pid*.log` if the JVM produced one.
4. That file goes on the clipboard **as a file**, so a single Ctrl+V in the chat attaches it.
5. A dialog appears - it stays open until closed, which also keeps the clipboard valid on Linux.

## Building

Needs a JDK 25: Minecraft 26.2 requires it, and Fabric Loom needs Gradle itself to run on it.
Point `JAVA_HOME` at one, or set `org.gradle.java.home` in your own `~/.gradle/gradle.properties`.

```
./gradlew build
```

The finished jar lands in `build/libs/`. To have it copied straight into a Minecraft instance
after every build, set `mods_dir` in `~/.gradle/gradle.properties`:

```
mods_dir=/path/to/instance/mods
org.gradle.java.home=/path/to/jdk-25
```

Those belong in the user-wide file on purpose - the project itself contains no paths from any
particular machine.

## Setup flow

Step 1 explains the mod and asks which AI to use. Step 2 insists on being signed in - either
open the login page, or explicitly confirm you already are. Step 3 picks which of the three
endings should produce a report, and points at the mod settings for everything else. The setup
runs again after every version change, but then only as a short "keep your settings or redo it"
question.

## Testing a crash

Mod Menu -> LogAI -> "Test: crash this game now". That calls `Runtime.halt()`, which skips
the orderly shutdown and therefore looks exactly like a hard crash to the watcher.

## Notes

- Client only. A dedicated server has no screen for the dialog.
- Do not run this together with Crash Assistant, both will open a window on the same crash.
- The mod never uploads anything itself and never asks for a password or an API key.

## License

MIT - see [LICENSE](LICENSE). Do what you like with it, just keep the copyright notice.

The concept of a separate watcher process is borrowed from Crash Assistant, but no code
from it was used; that project is under its own license.
