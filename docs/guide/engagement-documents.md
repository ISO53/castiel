---
title: Engagement documents
---

# Engagement documents

The engagement documents are plain JSON files in the workspace root. Agents fill them in as the engagement progresses; the UI renders them live as graphs, boards and tables. You can always open them in the built-in editor and read or correct them yourself.

## engagement.json

The contract of the engagement: what is in scope, what is forbidden, and how far along the engagement is. The phase number here is the source of truth for the phase chips and the tool gating.

```json
{
  "name": "",
  "phase": 1,
  "target": {
    "scope": [],
    "out_of_scope": [],
    "objectives": ""
  },
  "rules_of_engagement": {
    "allowed_tools": [],
    "forbidden_actions": [],
    "testing_window": "",
    "contacts": []
  },
  "osint": {
    "organizations": [],
    "people": [],
    "emails": [],
    "social_profiles": []
  }
}
```

## network.json

The attack surface as the agent sees it. Segments, hosts, services and DNS. The Network view renders the `relationships` as an interactive topology graph, with ports and services alongside.

```json
{
  "summary": "",
  "segments": [],
  "hosts": [],
  "domains": [],
  "relationships": []
}
```

## web.json

Everything discovered about the web presence of the targets: sites, pages, endpoints, parameters, directories, entry points, technologies and cookies. The Web App view renders this as a sitemap and a tech-stack breakdown.

```json
{
  "summary": "",
  "sites": [],
  "pages": [],
  "endpoints": [],
  "parameters": [],
  "directories": [],
  "entry_points": [],
  "technologies": [],
  "cookies": [],
  "relationships": []
}
```

## vulnerabilities.json

Every confirmed finding, one entry per vulnerability. Scores are not typed by the agent: after recording a finding it must call the `cvss_score` tool, which computes official CVSS v4.0 and v3.1 vectors and attaches them. The Vulnerabilities board shows the version you picked in **Settings → Vulnerability scoring**.

```json
{
  "vulnerabilities": []
}
```

## evidence.json

The index of captured artifacts, commands, files, screenshots, keys, requests... With the actual files stored in the `evidence/` folder next to it. The Evidence view is a searchable gallery over this index.

```json
{
  "artifacts": []
}
```

## Editing by hand

Hand-editing is safe, the documents are yours. Two things to keep in mind:

- Keep the JSON valid; the rendered views read these files directly.
- Don't hand-write CVSS scores into `vulnerabilities.json`; let the `cvss_score` tool attach them so both vectors stay consistent.
