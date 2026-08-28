# Castiel

Castiel is an AI-assisted penetration testing harness. You open a project folder (the *workspace*), connect it to a language model of your choice, and work together through a chat: the model can run commands, read and edit files, search the web, and organize everything it finds into structured documents you can view as graphs, boards, and tables.

The human stays in charge. You define the target and scope, decide when to move between engagement phases, and approve how far things go. The model does the hands-on technical work and keeps its findings recorded in the workspace.

## How it's built

| Part | What it is | Tech |
| --- | --- | --- |
| `harness/` | The backend: connects to the model, provides its tools, stores chats and settings | Java 25, Spring Boot, LangChain4j |
| `frontend/` | The desktop-style UI: chat, file explorer, document views | Vue 3, Vite, Tailwind CSS |

Works with any OpenAI-compatible provider (OpenAI, llama.cpp server, LM Studio, vLLM, OpenRouter) and with Ollama for fully local models.

## Getting started

Requirements: Java 25+ and Maven, plus Node.js 22+ (with npm) for the frontend.

### Development

Run the two parts in separate terminals, both with hot reload:

```sh
cd harness && mvn spring-boot:run     # terminal 1: backend (API only) on 8081
cd frontend && npm run dev            # terminal 2: UI on 5173, /api proxied to 8081
```

Open http://localhost:5173 and start working — the UI talks to the backend through the Vite proxy. Nothing is served at http://localhost:8081 in dev mode; that port is API only.

### Build & run

Build the UI, package the harness, and run the result: a single self-contained JAR serving both the UI and the API on one port.

```sh
cd frontend
npm install
npm run build

cd ../harness
mvn package
java -jar target/castiel-0.0.1-SNAPSHOT.jar
```

The terminal stays quiet: all you see is the banner and where to open the app.

```
UI accessible at http://localhost:8081
```

Settings are overridden the standard Spring Boot way:

```sh
java -jar target/castiel-0.0.1-SNAPSHOT.jar --server.port=9090   # different port
java -Dlogging.level.root=DEBUG -jar target/castiel-0.0.1-SNAPSHOT.jar   # verbose logs
```

Then open the app, create or open a workspace, pick your provider and model in the settings, and start a chat.

## Where Castiel writes

Castiel installs nothing and touches nothing outside two places.

| Data | Location |
| --- | --- |
| Engagement documents, chats, saved evidence | Inside the workspace folder you open (`engagement.json`, `.chats/`, ...) |
| Application settings (`settings.json`) | OS application-data directory: `%APPDATA%\castiel` on Windows, `~/Library/Application Support/castiel` on macOS, `~/.config/castiel` on Linux |

Delete both and Castiel is gone without a trace — though as with any AI tool, the agent itself can make changes on your computer.

## License

[GPL-3.0](LICENSE)
