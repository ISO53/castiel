# Castiel

Castiel is an AI-assisted penetration testing harness. You open a project folder (the *workspace*), connect it to a language model of your choice, and work together through a chat: the model can run commands, read and edit files, search the web, and organize everything it finds into structured documents you can view as graphs, boards, and tables.

The human stays in charge. You define the target and scope, decide when to move between engagement phases, and approve how far things go. The model does the hands-on technical work and keeps its findings recorded in the workspace:

- `engagement.json` — target, scope, objectives, and current phase
- `network.json` / `web.json` — discovered hosts, services, and web content
- `findings.json` — confirmed vulnerabilities with severity and evidence
- `evidence.json` — index of saved artifacts (scan output, screenshots, loot)

## How it's built

| Part | What it is | Tech |
| --- | --- | --- |
| `harness/` | The backend: connects to the model, provides its tools, stores chats and settings | Java 25, Spring Boot, LangChain4j |
| `frontend/` | The desktop-style UI: chat, file explorer, document views | Vue 3, Vite, Tailwind CSS |

Works with any OpenAI-compatible provider (OpenAI, llama.cpp server, LM Studio, vLLM, OpenRouter) and with Ollama for fully local models.

## Getting started

Requirements: Java 25+, Maven, Node.js 22+.

**Backend** (listens on port 8081):

```sh
cd harness
mvn spring-boot:run
```

**Frontend:**

```sh
cd frontend
npm install
npm run dev
```

Then open the app, create or open a workspace, pick your provider and model in the settings, and start a chat.

## License

[GPL-3.0](LICENSE)

