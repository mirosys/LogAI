# LogAI

LogAI is a tool that - as the name suggests - uses an AI of your choice to analyse your
crash details and tells you exactly what you need to do in order to avoid this exact problem.

No API key. No backend. No account inside the mod. When Minecraft dies, LogAI hands you the
log as a file on your clipboard and opens the AI you picked. One Ctrl+V, one Enter, an answer.

> **This is an early beta.** The core works and has been tested, but not every corner has been
> through many hands yet. If something misbehaves, please open an issue.

## What it does

- Watches the game from **outside**, so it survives crashes, freezes, native faults and hard kills
- Tells you **why** it thinks the game died, instead of leaving the AI to guess
- Puts the report on your clipboard **as a file** - paste it, send it, done
- Optionally restarts Minecraft afterwards
- Client side only, and it never uploads anything itself

## Setup

Three screens on first launch: pick your AI, make sure you are signed in to it, and choose
which kinds of shutdown should get you a report. Everything is changeable later under
**Mods → LogAI → settings** (needs Mod Menu).

## How it works, for the curious

### The watcher

At preLaunch, before Minecraft itself starts, LogAI spawns a small companion process in its
own JVM and hands it this game's process id. Because that process is not part of Minecraft,
it survives every kind of death: an exception, a freeze, a native JVM crash, or a hard kill
from the task manager. It sleeps until the game process disappears.

### Crash or not?

The exit code of a foreign process cannot be read from Java, so LogAI turns the question
around: on shutdown the mod writes down **how** the game ended, and it separates four cases.

| | How it is recognised |
|---|---|
| **Crash** | No marker at all - the process died outright |
| **Alt+F4** | The key press itself, seen a moment earlier |
| **Window closed** | The close flag is set, but no key press. The X button and the task manager send the same message, so they stay one case |
| **Normal quit** | The quit button inside the game - the only truly voluntary one |

Each of the four is a separate on/off setting. And if the marker says quit but the log
contains crash report text anyway, the log wins.

### The report

LogAI writes `logai/reports/LogAI-crash-<timestamp>.txt`: an English prompt, then what the
watcher observed first-hand - orderly shutdown or not, whether the log holds a crash report,
whether the JVM left an `hs_err_pid` file this session, and how long the log had been silent
before the process died. Then the log itself, trimmed to the first 300 and last 1800 lines so
the middle of a huge modpack log does not drown the interesting parts.

Without that, a log that simply stops looks identical whether the game froze, ran out of
memory, or was closed on purpose - and the AI has to guess.

### The clipboard

The report goes on the clipboard **as a file**, not as text, so a single Ctrl+V in the chat
attaches it. On Linux the content belongs to the process that put it there, so the watcher
outlives the copy: it stays alive while the dialog is open, and waits quietly in the
background in automatic mode.

### Restarting

LogAI can restart Minecraft after a crash. Some launchers do not start the game directly but
through a helper of their own that is gone once the game ends, so LogAI strips that helper out
and rebuilds a command line that stands on its own.

To do that it has to remember how the game was started, and that command line contains your
Minecraft access token. It is written to `logai/restart-command`, kept owner-only where the
file system supports it, and deleted as soon as the watcher is done. On Windows that same
token is already visible in the process list to any local program.

Restarting is skipped when the game ran for less than a minute, so a crash during loading
cannot turn into a restart loop.

### What it does not do

No API key, no backend, no account inside the mod, nothing uploaded by the mod itself. You
paste the log yourself, into your own browser session. Auto-paste is deliberately not
attempted: no provider supports it reliably, and the workarounds are worse than one keypress.
Client side only - a dedicated server has no screen for a dialog.

## Supported AIs

Claude, ChatGPT, Copilot, DeepSeek, Gemini. You need to be signed in to your choice in your
browser - LogAI never handles your account.

## Requirements

- Minecraft 26.2, Fabric
- Fabric API
- Mod Menu (optional, but it is the way into the settings)
