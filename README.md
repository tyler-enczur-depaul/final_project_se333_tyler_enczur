# Final Project: Java Test Tooling in MCP (FastMCP) Server

This repository contains a small Python server that exposes a set of MCP (FastMCP) tools for interacting with Java projects: running Maven tests, parsing reports (Surefire / JaCoCo), gathering coverage data, and git/GitHub helper operations.

Important notes:

- The Java parsers in `parsers/` were built with a Java 17 JDK. If parser execution fails,
  ensure your project (or system) uses a Java 17 JDK.
- You MUST use a Python virtual environment and install the packages in `requirements.txt`.
- Start the server (`python server.py`) before invoking tools from the LLM.

Contents

- [Project overview](#project-overview)
- [Installation & Configuration](#installation--configuration)
- [MCP tool API reference](#mcp-tool-api-reference)
- [Usage examples](#usage-examples)
- [Troubleshooting & FAQ](#troubleshooting--faq)
- [Parsers note](#parsers)

---

## Installation & Configuration

Prerequisites

- Linux terminal environment (likely still runs on other operating systems, but `mvn` commands were written for Linux so there may be issues in other operating systems)
- Python 3.11+ (or whichever version on your system that works with `requirements.txt`)
- `mvn` (Maven) available on PATH
- Java 17 JDK installed and available on PATH for running the parser JARs

1) Create and activate a virtual environment

```bash
uv venv
source .venv/bin/activate
```

2) Install Python dependencies

```bash
uv pip install -r requirements.txt
```

3) Configure GitHub token or alternative authentication (optional, required for PR creation)

The `git_pull_request` tool requires authentication to be previously set, otherwise manual password entry is required.

4) Ensure Java 17 JDK is used for the parsers

If you have multiple JDKs installed, make sure `java` and `javac` point to Java 17, or set
`JAVA_HOME` appropriately before launching the server.

If `java -version` doesn't report a 17 version and the parsers fail, switch to the Java 17
JDK and retry.

5) Start the MCP server

```bash
source .venv/bin/activate
python server.py
```

After starting, `server.py` calls `mcp.run(transport="http")` and will expose the tools. Keep this process running while using the tools from an LLM client such as VSCode Chat.

---

## MCP Tool API Reference

The server exposes the following tools. Each entry shows the function name, expected inputs, what it does, and the typical return type.

- initializeSourceDir(sourceDir: str)
  - Description: Initialize the MCP tooling with the source directory of the Java project.
  - Args: `sourceDir` - path to the Java project root.
  - Returns: `{"success": "..."}` on success, or `{"error": "..."}` on failure.

- runMavenTests()
  - Description: Runs `mvn test` in the configured `SOURCE_DIR`, then executes the `parsers/surefireParser.jar` to parse Surefire reports. The parser output is returned in JSON format. Note: only failures/errors are shown in the returned structure.
  - Returns: Parsed JSON or `{"error": "..."}`.

- runJacocoTestReport()
  - Description: Runs `mvn clean test jacoco:report`, then parses `target/site/jacoco/jacoco.xml`
    using `parsers/jacocoParser.jar`. Stores per-file coverage in server state variable.
  - Returns: `{"success": "..."}` on success, `{"issue": "..."}` if report missing, or
    `{"error": "..."}`.

- getCoverageForClass(name: str)
  - Description: Look up coverage percentages cached by `runJacocoTestReport()` for a given
    fully-qualified class name (for example: `org.example.ExampleClass`).
  - Returns: `{"class_name": name, "instruction_coverage": 0.0..1.0, "branch_coverage": 0.0..1.0}`
    or an `{"error": "..."}`.

- git_status()
  - Description: Return the repository status for `SOURCE_DIR` (clean, staged changes,
    unresolved conflicts).
  - Returns: `{"clean": bool, "staged_changes": [...], "conflicts": [...]} or {"error":"..."}`

- git_add_all()
  - Description: Stage all changes in the repository. If `.gitignore` is missing the function
    creates a default `.gitignore` tailored for a Maven project.
  - Returns: `{"added": bool, "files_staged": [...], "gitignore_created": bool}` or
    `{"error": "..."}`.

- git_commit(message: str)
  - Description: Commit staged changes with the supplied message. If coverage data is present
    in the server it will append a coverage summary to the commit message.
  - Returns: `{"committed": bool, "message": "full commit text", "hash": "..."}` or
    `{"error": "..."}`.

- git_push(remote: str = "origin")
  - Description: Push the current branch to the specified remote, and set upstream if needed.
  - Returns: `{"pushed": bool, "remote": "origin", "branch": "...", "error": null|"..."}`

- git_pull_request(title: str, body: str, base: str = "main")
  - Description: Create a GitHub pull request for the current branch (requires authentication).
    The function prepends a conventional prefix (feat|fix|refactor|docs|style|test) to the title
    if missing.
  - Returns: `{"pr_url": "https://..."}` on success or `{"error":"..."}`.

- runReviewParser()
  - Description: Run `parsers/reviewParser.jar` against the source directory and return static
    analysis issues (PMD/Checkstyle findings) as JSON.
  - Returns: `{"issues": [...]}` or `{"error":"..."}`.

Notes:

- Many tools return `{"error": "..."}` describing the failure. The server tries to
  include `stderr` from parsers when available.

---

## Troubleshooting & FAQ

- Q: The parsers fail with `FileNotFoundError` or `Parser failed`.
  - A: Confirm that `java` on PATH points to a Java 17 JDK (parsers were compiled with Java 17). Check `java -version`. If you have multiple JDKs, set `JAVA_HOME` to the Java 17 JDK and ensure `java` is the correct binary.

- Q: `mvn` not found or Maven fails.
  - A: Install Maven and ensure `mvn` is on PATH. The MCP tools call Maven inside the
    configured `SOURCE_DIR` so you must have a valid `pom.xml` there.

- Q: My LLM says the tools are unavailable or returns a transport error.
  - A: Make sure `python server.py` is running and reachable by the LLM client. The server must
    be started before the LLM uses the tools.

- Q: Virtual environment / dependencies issues.
  - A: Activate the virtual environment created earlier and run `uv pip install -r requirements.txt`.

- Q: Coverage values look wrong or are missing.
  - A: Ensure `runJacocoTestReport()` completed successfully. If `jacoco.xml` is missing, the function will return an `issue` mentioning the missing file. Also ensure that your tests actually exercise the code and that JaCoCo is producing the report.

---
