---
title: Engagement phases
---

# Engagement phases

Every engagement runs through four phases. The phase is stored in `engagement.json`, shown as chips in the menu bar, and critically it decides **which tools the model can see**. Before the engagement reaches a phase, that phase's tool groups are withheld from the model entirely.

## The four phases

| Phase | What happens | Tool groups unlocked |
| --- | --- | --- |
| **Planning & Pre-Engagement** | No active testing. castiel discusses the target, scope, objectives and rules with you; passive OSINT from third-party sources only, no traffic reaches the target. | `Shell` · `Files` · `Workspace layout` · `Web` · `Workspace search` · `Ask the user` |
| **Reconnaissance** | Information gathering is open. castiel interacts with in-scope targets freely and fills in `network.json`, `web.json` and `identity.json`. | `Sub-agents` · `Kali discovery` · `Kali launch` · `Background processes` plus every connected MCP tool |
| **Scanning & Vulnerability Assessment** | castiel probes in-scope targets for weaknesses using the recon data and records each issue in `vulnerabilities.json`. It never writes a CVSS score by hand: after recording a finding it calls `cvss_score` with a CVSS v4.0 and a CVSS v3.1 vector. | `Vulnerability intel` · `CVSS scoring` · `MITRE ATT&CK` |
| **Exploitation** | castiel attempts to exploit confirmed vulnerabilities and reports every attempt and result to you, stopping when told to. | Nothing new, everything is already unlocked; exploitation itself is what you authorize |

## You move the phases

castiel can suggest moving to the next phase when it believes the current one is complete, but the decision is always yours: you transition between phases using the phase chips in the menu bar. Nothing scans or exploits anything until you have moved the engagement there.

The full tool-by-tool breakdown, including which groups cannot be switched off, is on the [Tools](./tools) page.
