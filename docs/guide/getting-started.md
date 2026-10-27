---
title: Getting started
---

# Getting started

castiel ships as self-contained release bundles per operating system. No runtime dependencies, nothing to install besides the app itself.

## Install

Grab the archive for your OS from the [GitHub Releases](https://github.com/ISO53/castiel/releases) page, extract it, and run the launcher inside:

- **Windows:** `castiel\castiel.exe`
- **Linux:** `castiel/castiel`
- **macOS:** `castiel/castiel.app`

Open `http://localhost:8081` and start working.

Prefer a different port? Run `castiel --port 3000` (or `-p 3000`). The full set of command line flags is covered on the [CLI reference](./cli) page.

## First run

On first launch, an onboarding page prepares the workspace:

1. **Connect an LLM provider.** castiel supports `llama.cpp`, `Ollama`, `OpenRouter`, `Cline` and `Z.ai`. For remote models, OpenRouter is the simplest choice. See [LLM providers](./providers) for each option.
2. **Connect MCP servers (optional).** MCP servers give the agent extra tools such as a headless browser. castiel highly recommends [`obscura`](https://github.com/h4ckf0r0day/obscura) for headless browser tooling. See [MCP servers](./mcp).
3. **Select models for subagents.** In the settings dialog choose models for different subagents. Subagents won't work if the models are not selected. 
4. **Create a workspace.** castiel generates the necessary template files and folders automatically.

As soon as you start chatting, the engagement begins. During the first phase, castiel asks questions to understand your target and the boundaries of the session; follow-on phases run as standard pentesting sessions. You transition between phases yourself using the phase chips in the menu bar.

## Running the dev server

Requirements: Java 25+ and Maven for the backend, plus Node.js 22+ (with npm) for the frontend.

Run the two parts in separate terminals, both with hot reload:

```sh
cd harness && mvn spring-boot:run     # terminal 1: backend (API only) on 8081
cd frontend && npm run dev            # terminal 2: UI on 5173, /api proxied to 8081
```

Open `http://localhost:5173` and start working. The UI talks to the backend through the Vite proxy. Nothing is served at `http://localhost:8081` in dev mode; that port is API only.

## Building

Builds the app from source and turns it into the release bundles. castiel uses [jpackage](https://dev.java/learn/jvm/other-tools/jpackage/) for creating an app bundle. The packaging scripts builds the frontend, move the dist folders to backend so the backend can serve them. At this point the whole app can be runned with one .jar. jpackage goes one step ahead and bundles JRE with the app and turns it into a OS specific executable.

```sh
cd packaging
./package-windows.ps1     # Windows: needs a JDK with jpackage (JAVA_HOME or C:\Program Files\Java\*)
./package-linux.sh        # Linux
./package-macos.sh        # macOS
```

## Updating

castiel can check the latest GitHub release and install it for you: run `castiel --check-update`, or use **Help → Settings → Updates** to check manually, install in one click, or let castiel check on its own every time it opens.
