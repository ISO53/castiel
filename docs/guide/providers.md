---
title: LLM providers
---

# LLM providers

castiel works with any language model you can reach through one of five built-in providers, configured in **Help → Settings → LLM Providers**. None of them are enabled by default: the chat, sub-agents, the Kali dispatcher and the looper each need a model before they run.

| Provider | What it is |
| --- | --- |
| **llama.cpp** | Run open models locally with llama.cpp's built-in server, or point castiel at a remote one. |
| **Ollama** | Run open models locally with Ollama, or point castiel at a remote Ollama server. |
| **OpenRouter** | One OpenRouter key unlocks models from many upstream providers. castiel only ever needs this single key. |
| **Cline** | Reach Anthropic, OpenAI, Google and more through Cline's OpenAI-compatible endpoint, with an API key or an account sign-in. ClinePass subscription models show up alongside the regular catalog. |
| **Z.ai** | Zhipu's GLM model family straight from z.ai — pay-as-you-go with a Standard API key, or on the GLM Coding Plan subscription. The key type decides billing. |


::: warning
Most models will flag a penetration-testing session as cyber security and refuse to work. Models with a very low refusal rate for security work perform dramatically better in castiel. The GLM 5.x family, for example, has performed shockingly well in my testings.
:::
