---
title: Sub-agents & background work
---

# Sub-agents & background work

Long-running work shouldn't block the chat. castiel has two mechanisms for it. Sub-agents for delegating *work* or parrallelism, and background processes for running *commands*. Plus a looper that keeps a session moving while you are away.

## Sub-agents

The main agent can delegate narrow mechanical work. Recon, scanning, OSINT runs, document formatting... to a sub-agent that runs its own model in the background and returns a result. The relevant tools are `sub_agent`, `agent_list`, `agent_read` and `agent_kill`.

Sub-agent models are configured in **Settings → Sub-agents** and **never inherit the chat model**; each role stays idle until you pick one:

- **Worker**: this is the main subagent that the orchestrator agent calls. The orchestrator can delegate some serious scans/jobs to this agent. Prefer to use a smart and fast model for this one.
- **Kali dispatcher** — starts the tools you pick in the Kali dialog and terminates. It only ever gets the Kali-launch toolset, no raw shell. Prefer a small and fast model for this one.
- **Looper** — see below.

Finished and running sub-agents appear in the **Agents** tab of the bottom panel, with each run's task, live activity phase and final summary.

## Background processes

Both you and the agent can start long-running processes, an nmap scan, a listener, a brute-force run... and keep chatting while they run. The agent uses `bg_start`, `bg_list`, `bg_read`, `bg_send` and `bg_kill` to start them, check on them periodically and feed or terminate them; you can launch your own process from the **Processes** tab of the bottom panel and let castiel track progress and report results back to the workspace.

## The looper

The **Loop** button next to the composer keeps a session working while you are away: when the orchestrator agent goes idle, the looper reads the workspace, last orchestrator message and it's own last message, thinks about a next attack plan, and sends the instruction itself, until you stop it. Its budgets (how far it may run on its own) are part of the Limits settings.
