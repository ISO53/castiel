---
title: What is castiel
---

# What is castiel

**castiel** is an AI-assisted penetration testing harness. You open a project folder, the *workspace*, connect it to a language model of your choice, and work together through a chat: the model runs commands, reads and edits files, searches the web, looks up CVEs and MITRE ATT&CK techniques, finds Kali tools, spawns sub-agents, manages background processes, connects to MCP servers and keeps its findings in structured documents you can view as graphs, boards, and tables.

## How an engagement runs

Every engagement moves through four phases, and each phase unlocks more of the toolset:

1. **Planning & Pre-Engagement**: castiel will ask questions about target, scope, objectives and rules of engagement. It can gather passive OSINT at this sate but there will be no traffic to the target.
2. **Reconnaissance**: castiel will interact with in-scope targets freely and map the attack surface into the engagement documents.
3. **Scanning & Vulnerability Assessment**: castiel will actively search for attack vectors and weak points in targets and will score every finding with dual CVSS vectors before it counts.
4. **Exploitation**: castiel will attempt to exploit the vulnerabilities confirmed so far and will report every attempt and its result back to you.

You are in charge. You define the target and scope, decide when to move between phases using the phase chips in the menu bar, and approve how far things go. castiel can suggest moving to the next phase when it believes the current one is complete, but the decision is always yours.

## The workspace is the record

Everything castiel learns is written into plain JSON documents in the workspace. `engagement.json`, `network.json`, `web.json`, `vulnerabilities.json` and `evidence.json`. Plus an `evidence/` folder for captured artifacts. The UI renders these documents live as graphs, boards and tables, and you can always read or edit the raw files yourself.

## Where to go next

- [Getting started](./getting-started) - install castiel and run your first engagement.
- [Engagement phases](./engagement-phases) - what each phase allows and unlocks.
- [Tools](./tools) - the full built-in toolset and its phase gating.
- [LLM providers](./providers) - connecting llama.cpp, Ollama, OpenRouter, Cline or Z.ai.

::: warning
castiel depends on several third-party services and transmits user agents in those requests; as a result, your activity may be identified to well-resourced actors such as state agencies or large corporations. Also like most AI harnesses, castiel is vulnerable to prompt injection attacks. Running it in a Kali Linux VM is the recommended setup.
:::
