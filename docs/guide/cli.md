---
title: CLI reference
---

# CLI reference

The castiel launcher accepts the following flags. Unknown options and stray arguments fail fast with an error rather than being ignored.

| Flag | Description |
| --- | --- |
| `--port`, `-p` | HTTP port to listen on (default: `8081`). |
| `--bind` | Network address to bind (default: `127.0.0.1`). |
| `--auth` | Require token authentication for every API call. |
| `--show-token` | Print the `--auth` access token and exit. |
| `--reset-token` | Replace the `--auth` access token; takes effect on the next start. |
| `--debug` | Enable debug logging for castiel and LLM/MCP traffic. |
| `--log-file [FILE]` | Also write logs to a file, rotated automatically. Without a value, uses `castiel.log` in the current directory. |
| `--check-update` | Compare this build with the latest GitHub release and install a newer one if available. |
| `--help`, `-h` | Show all flags. |
| `--version`, `-V` | Print the version and exit. |

## Exposing castiel beyond localhost

By default castiel binds to `127.0.0.1` and is reachable only from your own machine. Binding beyond localhost — putting the UI on your LAN or a tunnel — **requires** `--auth`: without a token, anyone who can reach the port could drive your harness. Together:

```sh
castiel --bind 0.0.0.0 --auth
```

`--show-token` prints the generated access token (for logging in from another device); `--reset-token` replaces it and takes effect on the next start.

## Logging

`--debug` turns on debug logging for castiel itself and for all LLM and MCP traffic — useful when a model misbehaves or a server misconfigures. `--log-file` additionally mirrors logs into a rotating file:

```sh
castiel --debug
castiel --log-file castiel.log
castiel --log-file           # same, using castiel.log in the current directory
```
