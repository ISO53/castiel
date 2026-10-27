---
title: MCP servers
---

# MCP servers

castiel connects to [Model Context Protocol](https://modelcontextprotocol.io/) servers to give the agent tools beyond the built-in set. A headless browser, proxy access, anything an MCP server speaks.

## Declaring servers

MCP servers are declared in the `mcp.json` configuration file. castiel **watches the file and reloads it automatically**. Edit it while castiel is running and the servers come up or drop without a restart.

## How MCP tools behave

- Each connected server appears in **Settings → MCP Servers** with its own on/off switch; its tools are not part of the built-in tool catalog.
- Every MCP tool unlocks with **Reconnaissance** (phase 2), because they are arbitrary target-interactive tools. See [Engagement phases](./engagement-phases).
- MCP tools stack on top of the built-in toolset; the agent sees them alongside the harness tools.

## Recommended: obscura

castiel highly recommends [`obscura`](https://github.com/h4ckf0r0day/obscura) for headless browser tooling, but the choice is yours.
