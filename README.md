# Java 17 AI Harness Skeleton

A minimal, extensible Java 17 console application for dispatching text prompts to AI model providers and receiving responses back. It uses [LangChain4j](https://github.com/langchain4j/langchain4j) to communicate with an OpenAI-compatible endpoint (such as a local `llama.cpp` server) while keeping provider-specific code completely isolated behind a clean interface.

---

## Prerequisites

- **Java 17+** (JDK 17 or newer)
- **Maven 3.8+**
- (Optional for running against a real local model) **`llama.cpp`** (`llama-server`)

---

## Quickstart

### 1. Start `llama-server` Locally

If you have `llama.cpp` installed, start the server pointing to any GGUF model:

```bash
llama-server -m /path/to/your-model.gguf --port 8080 -a gpt-3.5-turbo
```

By default, the server runs an OpenAI-compatible endpoint at `http://localhost:8080/v1`.

### 2. Build the Project

Compile the application and run unit tests:

```bash
mvn clean compile
```

To run all unit tests:

```bash
mvn test
```

### 3. Run the Application

Execute the console app using the Maven Exec plugin:

```bash
mvn exec:java
```

You can optionally pass custom arguments or override the base URL / model name via JVM system properties:

```bash
# Custom question as arguments
mvn exec:java -Dexec.args="What are the three laws of robotics?"

# Custom endpoint or model alias
mvn exec:java -Dexec.args="Hello model" -Dharness.baseUrl="http://localhost:8080/v1" -Dharness.model="gpt-3.5-turbo"
```

---

## Architecture & Project Structure

```
src/
├── main/java/com/harness/core/
│   ├── AiProvider.java          # Core interface: AiResponse send(AiRequest request)
│   ├── AiRequest.java           # Record: systemPrompt (nullable), userMessage
│   ├── AiResponse.java          # Record: text, rawModelId
│   ├── AiProviderException.java # Exception wrapper for provider failures
│   ├── Harness.java             # High-level orchestrator delegating to an AiProvider
│   ├── LlamaCppProvider.java    # AiProvider implementation using LangChain4j OpenAiChatModel
│   └── Main.java                # Console entry point
└── test/java/com/harness/core/
    └── HarnessTest.java         # Unit tests validating seam, records, and delegation
```

---

## Adding a New Provider

To integrate a new provider (such as Anthropic Claude, OpenAI direct, Mistral, or a custom internal backend), create a new class that implements [`AiProvider`](file:///C:/Users/termi/Documents/Projects/PersonalProjects/castiel/src/main/java/com/harness/core/AiProvider.java) and maps the incoming [`AiRequest`](file:///C:/Users/termi/Documents/Projects/PersonalProjects/castiel/src/main/java/com/harness/core/AiRequest.java) to that provider's client, returning a populated [`AiResponse`](file:///C:/Users/termi/Documents/Projects/PersonalProjects/castiel/src/main/java/com/harness/core/AiResponse.java). Because [`Harness`](file:///C:/Users/termi/Documents/Projects/PersonalProjects/castiel/src/main/java/com/harness/core/Harness.java) only depends on the [`AiProvider`](file:///C:/Users/termi/Documents/Projects/PersonalProjects/castiel/src/main/java/com/harness/core/AiProvider.java) interface, you can pass your new provider directly into `new Harness(new AnthropicProvider(...))` without modifying any existing core classes.
