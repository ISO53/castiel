---
title: Tools
---

# Tools

The harness ships a built-in toolset the model calls during an engagement. Tools are grouped for display, individually toggleable in **Settings → Tools**, and gated by the engagement phase.

## The tool groups

| Group | Tools | Notes |
| --- | --- | --- |
| Shell | `bash` | Runs commands in the workspace. PowerShell on Windows, bash elsewhere. **Locked.** |
| Files | `read_file`, `write_file`, `edit_file` | Reads, writes and edits workspace files. Every view is fed by the JSON the agent writes. **Locked.** |
| Workspace layout | `workspace_info` | Describes the workspace layout, the current phase and what is missing. **Locked.** |
| Web | `web_fetch`, `web_search` | Fetches pages and searches the web for background research. |
| Workspace search | `workspace_search` | Greps the workspace for files and content, literal or regex. **Locked.** |
| Ask the user | `ask_user_question` | Asks you a multiple-choice question and waits for the answer. |
| Sub-agents | `sub_agent`, `agent_list`, `agent_read`, `agent_kill` | Delegates narrow mechanical work to background sub-agents. |
| Kali discovery | `search_kali_tools` | Searches the Kali tool catalog to find a purpose-built binary for a job. |
| Kali launch | `kali_launch` | Starts the Kali tools you picked in the Kali dialog as background scans. |
| Background processes | `bg_start`, `bg_list`, `bg_read`, `bg_send`, `bg_kill` | Starts long-running commands and reads, feeds or kills them. |
| Vulnerability intel | `vuln_search`, `vuln_detail`, `exploit_search` | Searches the CVE registry for vulnerabilities and public exploit code, with CVSS, EPSS and known-exploitation flags. |
| CVSS scoring | `cvss_score` | Computes official CVSS v4.0 and v3.1 scores and attaches them to findings. |
| MITRE ATT&CK | `search_attack`, `attack_detail` | Looks up adversary techniques and real-world procedures in the bundled ATT&CK catalog. |

The **phase** column is the engagement phase at which the group unlocks. Before that, its tools are withheld from the model entirely. See [Engagement phases](./engagement-phases).

## Locked vs toggleable

Four groups: Shell, Files, Workspace layout and Workspace search are **locked**: they are the foundation the app runs on and cannot be switched off, not even by hand-editing the settings file.

Everything else can be toggled off in **Settings → Tools**. Switching a group off hides its tools from the model. Main agent and sub-agents alike. Disabling a group whose absence costs correctness (Workspace search, Background processes, CVSS scoring, Kali launch) triggers a warning.

## MCP tools

Tools contributed by connected MCP servers are not part of this catalog: each server has its own on/off switch, and the tools themselves unlock with Reconnaissance (phase 2), since they are arbitrary target-interactive tools. See [MCP servers](./mcp).
