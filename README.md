<p align="center">
  <img width="100%" src="castiel-logo.svg" alt="castiel. AI-assisted penetration testing harness">
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
  <img src="https://img.shields.io/endpoint?url=https://ghloc.dev/api/ISO53/castiel/badge&color=black&style=round-square" alt="lines count">
</p>

# castiel

castiel is an AI-assisted penetration testing harness. You open a project folder (the *workspace*), connect it to a language model of your choice, and work together through a chat: the model runs commands, reads and edits files, searches the web, looks up CVEs and ATT&CK techniques, finds Kali tools, spawns sub-agents, manages background processes, connects to MCP servers and keeps its findings in structured documents you can view as graphs, boards, and tables. The engagement runs in four phases: Planning, Reconnaissance, Scanning & Vulnerability Assessment, and Exploitation. Each phase unlocks more of the toolset.

The human stays in charge. You define the target and scope, decide when to move between engagement phases, and approve how far things go. The model does the hands-on technical work and keeps its findings recorded in the workspace.

> [!CAUTION]
> I could say that 'This app is for academical purposes only, do not use it on systems you don't have permission on' but i couldn't care less. Use it however you want. I don't take any responsibility on how you use it. But keep in mind that castiel depends on several third-party services and transmits user agents in those requests; as a result, your activity may be identified to well-resourced actors such as state agencies or large corporations.
> 
> Try to use it with models that has a very low refusal rate for Cyber Security. The GLM 5.x models are performing shockingly good for instance. Most models will probably flag your session as cyber security and won't let you use this harness.
>
> Also keep in mind that, like for all AI harnesses, you are vulnerable to prompt injection attacks. Recommended usage is running castiel in a Kali Linux VM. Don't worry castiel won't escape from the sandbox if you carefully set it. Those happens only in multi trillion dollar AI companies that have the best engineers and strongest AI models in the world. Kinda ironic isn't it?

## Getting started

Requirements: only for building from source. Java 25+ and Maven, plus Node.js 22+ (with npm) for the frontend. The released packages need nothing but the OS.

### Install

Grab the archive for your OS from the [GitHub Releases](https://github.com/ISO53/castiel/releases) page, extract it, and run the launcher inside.

- **Windows:** `castiel\castiel.exe`
- **Linux:** `castiel/castiel`
- **macOS:** `castiel/castiel.app`

Open http://localhost:8081 and start working.

Prefer a different port? Run `castiel --port 3000` (or `-p 3000`).

### Development

Run the two parts in separate terminals, both with hot reload:

```sh
cd harness && mvn spring-boot:run     # terminal 1: backend (API only) on 8081
cd frontend && npm run dev            # terminal 2: UI on 5173, /api proxied to 8081
```

Open http://localhost:5173 and start working. The UI talks to the backend through the Vite proxy. Nothing is served at http://localhost:8081 in dev mode; that port is API only.

### Package

Builds the app from source and turns it into the release bundles. Self-contained app image (launcher + private runtime) plus a zip/tar.gz per OS, from `packaging/dist/`:

```sh
cd packaging
./package-windows.ps1     # Windows: needs a JDK with jpackage (JAVA_HOME or C:\Program Files\Java\*)
./package-linux.sh        # Linux
./package-macos.sh        # macOS
```

Settings are overridden with castiel's own command line flags:

```sh
castiel --debug                 # debug logging for castiel and LLM/MCP traffic
castiel --log-file castiel.log  # also write logs to a file (rotated automatically)
castiel --log-file              # same, using castiel.log in the current directory
castiel --version               # print version and exit
castiel --help                  # all flags
castiel --check-update          # checks the latest version from github releases
```

## Usage

On first launch, an onboarding page prepares the workspace. Start by connecting an LLM provider. The supported options are `llama.cpp`, `Ollama`, `OpenRouter`, and `Cline`. Cline takes an account sign-in too, with ClinePass models in the catalog. For remote models, `OpenRouter` is the simplest choice. If you already have a subscription with another provider, you can bring it through OpenRouter's BYOK integration, which uses your existing key against its OpenAI-compatible `v1/chat/completions` API and provides structured responses with little setup.

Next, connect any MCP servers you'd like. castiel highly recommends [`obscura`](https://github.com/h4ckf0r0day/obscura) for headless browser tooling, but the choice is yours.

On first use, create a workspace. castiel automatically generates the necessary template files and folders, and as soon as you start chatting, the engagement begins. During the first phase, castiel asks questions to understand your target and the boundaries of the session; follow-on phases run as standard pentesting sessions. You stay in control of when sessions transition, using the phase chips in the menu bar. castiel can suggest moving to the next phase when it believes the current one is complete, but the decision is always yours.

The left panel shows the file tree with two toggleable view modes: raw files or rendered views, so you can review castiel's findings either as files or as graphs, boards, and tables. Some views are also interactive.

The bottom panel handles background processes and sub-agent runs. The agent can run long-running tasks (an nmap scan, for example) in the background while it continues other work, checking on them periodically. You can also launch your own background processes and let castiel track their progress and report results back to the workspace. The right panel is where the chat takes place.

The **Loop** button next to the composer keeps a session working while you are away: when the agent goes idle, the looper reads the workspace and sends the next instruction itself, until you stop it.

## Tools

The harness's built-in tools:

| Tool | What it does |
| --- | --- |
| `bash` | Runs shell commands in the workspace. PowerShell on Windows, bash elsewhere. |
| `read_file` / `write_file` / `edit_file` | Reads, writes and edits workspace files. |
| `workspace_info` | Describes the workspace layout, the current phase and which documents are missing. |
| `workspace_search` | Searches workspace files and their contents, literal or regex. |
| `web_search` / `web_fetch` | Searches the web and fetches pages for background research. |
| `vuln_search` / `vuln_detail` / `exploit_search` | CVE lookup with CVSS, KEV flags and public PoC repos. |
| `search_attack` / `attack_detail` | MITRE ATT&CK technique lookup with real-world procedures. |
| `search_kali_tools` / `kali_launch` | Finds a Kali tool for the job, and launches the ones you pick in the Kali dialog as background scans. |
| `sub_agent` / `agent_list` / `agent_read` / `agent_kill` | Delegates work to a background sub-agent and collects the result. |
| `cvss_score` | Scores a finding with exact CVSS v4.0 and v3.1 vectors. |
| `ask_user_question` | Asks you a multiple-choice question and waits for the answer. |
| `bg_start` / `bg_list` / `bg_read` / `bg_send` / `bg_kill` | Starts long-running processes and reads, feeds or kills them. |

Shell, files and workspace layout cannot be switched off. Everything else is toggleable in Settings → Tools, and MCP servers add their own tools on top.

## Settings

Everything else lives in **Help → Settings**. The defaults work, but a few settings decide how much castiel gets out of a session:

- **Sub-agents:** pick the provider and model for the worker, the Kali dispatcher and the looper. None of them inherit the chat model, and none of them run until you pick one.
- **Limits:** tool rounds, checkpoint reminders, how much tool history stays verbatim, the PoC star threshold and the looper budgets.
- **Tools:** switch tool groups off. Phase gating still applies.

The other sections cover providers, MCP servers, the CVSS version and updates.

## Acknowledgments

The bundled MITRE ATT&CK technique catalog (generated by `scripts/extract_attack_techniques.py`) uses [MITRE ATT&CK®](https://attack.mitre.org/) data from [attack-stix-data](https://github.com/mitre-attack/attack-stix-data). ATT&CK® is a registered trademark of The MITRE Corporation. The Kali tool catalog (`scripts/extract_kali_tools.py`) is built from the [Kali tools documentation](https://gitlab.com/kalilinux/documentation/kali-tools).

Vulnerability intel comes from the [CVE Program](https://www.cve.org/) search and CVE Services APIs, with known-exploited flags from [CISA Vulnrichment](https://github.com/cisagov/vulnrichment) and public PoCs from [PoC-in-GitHub](https://github.com/nomi-sec/PoC-in-GitHub).

Web search uses [FreeSerp](https://freeserp.ai/).

## License

This project is licensed under the [GPL-3.0](LICENSE) license.
