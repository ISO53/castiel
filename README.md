<p align="center">
  <img src="castiel-logo.svg" alt="castiel. AI-assisted penetration testing harness">
</p>

<p align="center">
    <b>castiel</b> is an AI-assisted penetration testing harness.
</p>

<br>

<p align="center">
  <a href="https://github.com/ISO53/castiel/releases/latest">
    <img src="https://img.shields.io/github/v/release/ISO53/castiel?label=GitHub%20Release&style=round-square&color=black" alt="GitHub Release">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/license-GNU-black.svg?style=round-square" alt="License">
  </a>
  <img src="https://img.shields.io/github/languages/code-size/ISO53/castiel?style=round-square&color=black" alt="GitHub code size in bytes">
</p>

# castiel

castiel is an AI-assisted penetration testing harness. You open a project folder (the *workspace*), connect it to a language model of your choice, and work together through a chat: the model can run commands, read and edit files, search the web, run background processes, connect to MCP servers and organize everything it finds into structured documents you can view as graphs, boards, and tables.

The human stays in charge. You define the target and scope, decide when to move between engagement phases, and approve how far things go. The model does the hands-on technical work and keeps its findings recorded in the workspace.

> [!WARNING]
> This app is still under heavy development. Expect bugs, incomplete features and breaking changes.

## Getting started

Requirements: Java 25+ and Maven, plus Node.js 22+ (with npm) for the frontend.

### Install

Grab the latest release JAR from [GitHub Releases](https://github.com/ISO53/castiel/releases). Only Java 25+ is needed, no Maven or npm.

```sh
# Windows PowerShell
curl.exe -L -o castiel.jar https://github.com/ISO53/castiel/releases/latest/download/castiel.jar

# Linux/macOS
wget https://github.com/ISO53/castiel/releases/latest/download/castiel.jar

# Run
java -jar castiel.jar
```

Open http://localhost:8081 and start working.

### Development

Run the two parts in separate terminals, both with hot reload:

```sh
cd harness && mvn spring-boot:run     # terminal 1: backend (API only) on 8081
cd frontend && npm run dev            # terminal 2: UI on 5173, /api proxied to 8081
```

Open http://localhost:5173 and start working. The UI talks to the backend through the Vite proxy. Nothing is served at http://localhost:8081 in dev mode; that port is API only.

### Build & run

Build the UI, package the harness, and run the result: a single self-contained JAR serving both the UI and the API on one port.

```sh
cd frontend
npm install
npm run build

cd ../harness
mvn package
java -jar target/castiel-{version}.jar
```

Settings are overridden the standard Spring Boot way:

```sh
java -jar target/castiel-{version}.jar --server.port=9090   # different port
java -Dlogging.level.root=DEBUG -jar target/castiel-{version}.jar   # verbose logs
```

## Usage

On first launch, an onboarding page prepares the workspace. Start by connecting an LLM provider. The supported options are `llama.cpp`, `Ollama`, and `OpenRouter`. For remote models, `OpenRouter` is the simplest choice. If you already have a subscription with another provider, you can bring it through OpenRouter's BYOK integration, which uses your existing key against its OpenAI-compatible `v1/chat/completions` API and provides structured responses with little setup.

> [!TIP]
> While the large, smart reasoning models are better suited for critical findings in a pentesting environment they are less likely to follow the system prompt or the user's instructions for small things. They tend to use `bash` tool and custom scripts for everything instead of leveraging built in tools or MCP servers. Small models on the other hand are very good at following instructions and tool using but they are less capable and less reliable.

Next, connect any MCP servers you'd like. castiel highly recommends `obscura` for headless browser tooling and `Caido` for web security auditing, but the choice is yours.

On first use, create a workspace. castiel automatically generates the necessary template files and folders, and as soon as you start chatting, the engagement begins. During the first phase, castiel asks questions to understand your target and the boundaries of the session; follow-on phases run as standard pentesting sessions. You stay in control of when sessions transition. castiel can suggest moving to the next phase when it believes the current one is complete, but the decision is always yours.

The left panel shows the file tree with two toggleable view modes: raw files or rendered views, so you can review castiel's findings either as files or as graphs, boards, and tables. Some views are also interactive.

The bottom panel handles background processes. The agent can run long-running tasks (an nmap scan, for example) in the background while it continues other work, checking on them periodically. You can also launch your own background processes and let castiel track their progress and report results back to the workspace. The right panel is where the chat takes place.

## License

This project is licensed under the [GPL-3.0](LICENSE) license.
