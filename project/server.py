# server.py
import os
import json
import subprocess
import re
import textwrap

from fastmcp import FastMCP
from git import (
    Repo,
    GitCommandError,
    InvalidGitRepositoryError,
)
from github import Github, GithubException
from dotenv import load_dotenv
from pathlib import Path

mcp = FastMCP("Java Test Generator")

SOURCE_DIR: str | None = None
COVERAGE_PERCENTAGE: dict[str, tuple[float, float]] | None = None
COVERAGE_STATS: dict[str, list[dict[str, int]]] | None = None

def _require_source_dir() -> None:
    if SOURCE_DIR is None:
        raise RuntimeError("Source directory not set. Run initializeSourceDir first.")

# --------------------------- Main Testing Tools ---------------------------

@mcp.tool
def initializeSourceDir(sourceDir: str):
    """Initialize the MCP tools with the source directory of the Java project

    Args:
        sourceDir (str): The source directory path of the java project.

    Returns:
        dict: A dictionary with either a 'success' key on success or an 'error' key on failure.
    """
    global SOURCE_DIR, COVERAGE_PERCENTAGE, COVERAGE_STATS

    if not isinstance(sourceDir, str):
        return {"error": "sourceDir must be a string."}

    source_dir = os.path.abspath(sourceDir)
    if not os.path.isdir(source_dir):
        return {"error": f"Directory not found: {source_dir}"}

    SOURCE_DIR = source_dir
    COVERAGE_PERCENTAGE = {}
    COVERAGE_STATS = {}

    return {"success": f"Initialized {SOURCE_DIR}"}


@mcp.tool
def runMavenTests():
    """Run mvn test in the SOURCE_DIR and return parsed JSON of the test results.
    Successes are not shown in the results, only failures or errors.

    Returns:
        dict: A dictionary with 'summary' and 'tests' on success, or 'error' on failure.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    cmd = ["mvn", "clean", "test"]
    try:
        proc = subprocess.run(cmd, cwd=SOURCE_DIR, capture_output=True, text=True)
    except FileNotFoundError as exc:
        return {"error": f"mvn not found: {exc}"}
    except Exception as exc:
        return {"error": f"Failed to run mvn: {exc}"}

    # parse surefire reports using Java parser
    surefire_dir = os.path.join(SOURCE_DIR, "target", "surefire-reports")
    if not os.path.isdir(surefire_dir):
        return {"error": f"Surefire reports directory not found: {surefire_dir}"}

    try:
        proc = subprocess.run(
            ["java", "-jar", "parsers/surefireParser.jar", surefire_dir],
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        return {"error": f"java or parser jar not found: {exc}"}
    except Exception as exc:
        return {"error": f"Failed to run parser: {exc}"}
    if proc.returncode != 0:
        return {"error": "Parser failed", "stderr": proc.stderr.strip()}

    try:
        parsed = json.loads(proc.stdout)
    except Exception as exc:
        return {"error": f"Failed to parse parser output: {exc}"}

    return parsed

@mcp.tool
def runJacocoTestReport():
    """Run mvn clean test jacoco:report in the source directory and parse the XML report.

    Returns:
        dict: A dictionary with 'success' on success, or 'error'/'issue' on failure.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    # shouldn't be necessary but errors were being thrown otherwise
    global COVERAGE_STATS, COVERAGE_PERCENTAGE

    cmd = ["mvn", "clean", "test", "jacoco:report"]
    try:
        proc = subprocess.run(cmd, cwd=SOURCE_DIR, capture_output=True, text=True)
    except FileNotFoundError as exc:
        return {"error": f"mvn not found: {exc}"}
    except Exception as exc:
        return {"error": f"Failed to run mvn: {exc}"}
    if proc.returncode != 0:
        return {"error": "Maven Failed", "stderr": proc.stderr.strip()}


    xml_path = os.path.join(SOURCE_DIR, "target", "site", "jacoco", "jacoco.xml")
    if not os.path.isfile(xml_path):
        return {"issue": f"jacoco.xml not found at {xml_path}, tests likely not written yet"}

    try:
        proc = subprocess.run(
            ["java", "-jar", "parsers/jacocoParser.jar", xml_path],
            capture_output=True,
            text=True,
        )
    except FileNotFoundError as exc:
        return {"error": f"java or parser jar not found: {exc}"}
    except Exception as exc:
        return {"error": f"Failed to run parser: {exc}"}
    if proc.returncode != 0:
        return {"error": proc.stderr.strip()}

    try:
        parsed = json.loads(proc.stdout)
    except Exception as exc:
        return {"error": f"Failed to parse parser output: {exc}"}

    COVERAGE_STATS = parsed["fileCoverages"]

    for key, val in COVERAGE_STATS.items():
        tot_ins = 0
        tot_branches = 0
        covered_ins = 0
        covered_branches = 0
        for line in val:
            tot_ins += line["coveredInstructions"] + line["missedInstructions"]
            covered_ins += line["coveredInstructions"]
            tot_branches += line["coveredBranches"] + line["missedBranches"]
            covered_branches += line["coveredBranches"]

        COVERAGE_PERCENTAGE[key] = (covered_ins / tot_ins if tot_ins > 0 else 1,
                                    covered_branches / tot_branches if tot_branches > 0 else 1)

    return {"success": "Jacoco report successfully ran"}


@mcp.tool
def getCoverageForClass(name: str):
    """Return coverage for a given class.

    Args:
        name (str): The fully qualified class name, e.g., "org.example.ExampleClass".

    Returns:
        dict: A dictionary with class_name, instruction_coverage, branch_coverage, and detailed stats.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    if not isinstance(name, str):
        return {"error": "name must be a string."}

    if name not in COVERAGE_PERCENTAGE or name not in COVERAGE_STATS:
        return {
            "class_name": name,
            "instruction_coverage": 1,
            "branch_coverage": 1
        }

    coverage_percentage = COVERAGE_PERCENTAGE[name]
    coverage_stats = COVERAGE_STATS[name]
    return {
        "class_name": name,
        "instruction_coverage": coverage_percentage[0],
        "branch_coverage": coverage_percentage[1],
        "coverage_stats": coverage_stats
    }



# --------------------------------- Git helpers -----------------------------------

@mcp.tool
def git_status():
    """Return clean status, staged changes, and conflicts in SOURCE_DIR.

    Returns:
        dict: A dictionary with the following keys:
            clean (bool): True if no work-tree changes exist.
            staged_changes (list): List of file paths that are staged (index != HEAD).
            conflicts (list): List of file paths currently in an unmerged state.
            error (str): Only present if an error occurred.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    repo = Repo(SOURCE_DIR)

    try:
        staged_changes = [diff.a_path for diff in repo.index.diff("HEAD")]
    # DO NOT REMOVE, if no current changes error thrown
    except GitCommandError:
        staged_changes = []

    conflicts = list(repo.index.unmerged_blobs().keys())
    clean = not repo.is_dirty(untracked_files=True)

    return {
        "clean": clean,
        "staged_changes": staged_changes,
        "conflicts": conflicts
    }

@mcp.tool
def git_add_all():
    """Stage all changes in SOURCE_DIR, excluding build artifacts and temp files.
    Automatically creates a .gitignore if none exists.

    Returns:
        dict: A dictionary containing:
            added (bool): True if any files were staged.
            files_staged (list): List of file paths that were staged.
            gitignore_created (bool): Whether a new .gitignore was created.
            error (str): Present if an error occurred.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    repo = Repo(SOURCE_DIR)

    #  check if .gitignore exists; if not, create it with default patterns
    gitignore_path = Path(SOURCE_DIR) / ".gitignore"
    # the dedent was needed since the gitignore was keeping the indents
    default_patterns = textwrap.dedent("""
        # Created by https://www.toptal.com/developers/gitignore/api/maven,java
        # Edit at https://www.toptal.com/developers/gitignore?templates=maven,java

        ### Java ###
        # Compiled class file
        *.class

        # Log file
        *.log

        # BlueJ files
        *.ctxt

        # Mobile Tools for Java (J2ME)
        .mtj.tmp/

        # Package Files #
        *.jar
        *.war
        *.nar
        *.ear
        *.zip
        *.tar.gz
        *.rar

        # virtual machine crash logs, see http://www.java.com/en/download/help/error_hotspot.xml
        hs_err_pid*
        replay_pid*

        ### Maven ###
        target/
        pom.xml.tag
        pom.xml.releaseBackup
        pom.xml.versionsBackup
        pom.xml.next
        release.properties
        dependency-reduced-pom.xml
        buildNumber.properties
        .mvn/timing.properties
        # https://github.com/takari/maven-wrapper#usage-without-binary-jar
        .mvn/wrapper/maven-wrapper.jar

        # Eclipse m2e generated files
        # Eclipse Core
        .project
        # JDT-specific (Eclipse Java Development Tools)
        .classpath

        ### VisualStudioCode ###
        .vscode/*
        !.vscode/settings.json
        !.vscode/tasks.json
        !.vscode/launch.json
        !.vscode/extensions.json
        !.vscode/*.code-snippets
        
        # Local History for Visual Studio Code
        .history/
        
        # Built Visual Studio Code Extensions
        *.vsix
        
        ### VisualStudioCode Patch ###
        # Ignore all local history of files
        .history
        .ionide
        # End of https://www.toptal.com/developers/gitignore/api/maven,java
    """)

    gitignore_created = False
    if not gitignore_path.exists():
        gitignore_path.write_text(default_patterns)
        gitignore_created = True

    # add the files
    try:
        repo.git.add(".")
        # get list of files that were staged
        proc = repo.git.diff("--cached", "--name-only", text=True)
        files = [f.strip() for f in proc.splitlines() if f.strip()]

    except Exception as exc:
        return {
            "added": False,
            "files_staged": [],
            "gitignore_created": gitignore_created,
            "error": str(exc)
        }

    return {
        "added": len(files) > 0,
        "files_staged": files,
        "gitignore_created": gitignore_created
    }

@mcp.tool
def git_commit(message: str):
    """Commit changes in SOURCE_DIR with message.

    Automatically appends coverage statistics (line and branch coverage per class)
    to the commit message if COVERAGE_PERCENTAGE is defined in scope.

    Args:
        message (str): The commit message prefix (e.g., "feat: add user auth").

    Returns:
        dict: A dictionary containing:
            committed (bool): True if commit succeeded.
            message (str): The final commit message (with coverage).
            hash (str): The Git commit hash (if successful).
            error (str): Present if an error occurred.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    repo = Repo(SOURCE_DIR)

    # Append coverage summary when available
    if COVERAGE_PERCENTAGE is not None:
        lines = [f"  {cls}: {line_cov:.1f} line, {branch_cov:.1f} branch"
                 for cls, (line_cov, branch_cov) in COVERAGE_PERCENTAGE.items()]
        coverage_message = "\n\nCoverage Summary:\n" + "\n".join(lines)
    else:
        coverage_message = "\n\n[No coverage data available]"

    full_message = message.strip() + coverage_message

    try:
        repo.git.commit(m=full_message)
    except Exception as exc:
        return {
            "committed": False,
            "message": message,
            "hash": "",
            "error": str(exc)
        }

    try:
        commit_hash = repo.head.commit.hexsha
    except Exception:
        commit_hash = ""

    return {
        "committed": True,
        "message": full_message,
        "hash": commit_hash,
    }


@mcp.tool
def git_push(remote: str = "origin"):
    """Push the current branch from SOURCE_DIR to the given remote.

    Args:
        remote (str): Name of the remote (default: 'origin').

    Returns:
        dict: A dictionary containing:
            pushed (bool): True if push succeeded.
            remote (str): Remote name.
            branch (str): Branch name.
            error (str): Error message if failed.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    repo = Repo(SOURCE_DIR)
    branch = repo.active_branch.name

    try:
        push_info = repo.git.push("--set-upstream", remote, branch)

        return {
            "pushed": True,
            "remote": remote,
            "branch": branch,
            "error": None
        }

    except Exception as exc:
        error_msg = str(exc).strip()
        return {
            "pushed": False,
            "remote": remote,
            "branch": branch,
            "error": error_msg
        }


def _get_remote_owner_repo(repo: Repo):
    remotes = list(repo.remotes)
    if not remotes:
        # only way I could give error without server bugging out here
        raise ValueError("No remote git found")

    url = str(remotes[0].config_reader.get_value("url"))

    # tried matching against these urls (http and ssh)
    # https://github.com/owner/repo.git
    # git@github.com:owner/repo.git
    patterns = [
        r"https?://[^/]+/(?P<owner>[^/]+)/(?P<repo>[^/.]+)(\.git)?",
        r"git@[^:]+:(?P<owner>[^/]+)/(?P<repo>[^/.]+)(\.git)?",
    ]
    for pat in patterns:
        m = re.match(pat, url)
        if m:
            return m.group("owner"), m.group("repo")

    raise ValueError(f"Could not parse remote URL: {url}")

@mcp.tool
def git_pull_request(
    title: str,
    body: str,
    base: str = "main",
):
    """Create a pull request on GitHub for the current branch.

    Args:
        title (str): The PR title. Must be prepended with one of the following:
            feat|fix|refactor|docs|style|test. The function will prepend a tag if missing.
        body (str): The PR description/body. Please include information such as
            test coverage improvements or bug fixes.
        base (str): Target branch of the pull request (default: "main").

    Returns:
        dict: A dictionary with "pr_url" on success, or "error" on failure.
    """

    if not title.strip():
        return {"error": "Title cannot be empty"}
    if not body.strip():
        return {"error": "Body cannot be empty"}

    # initialise local Git repo and get branch / remote info
    try:
        _require_source_dir()
        repo = Repo(SOURCE_DIR)
    except RuntimeError as exc:
        return {"error": str(exc)}
    except InvalidGitRepositoryError as exc:
        return {"error": f"Not a Git repository: {str(exc)}"}

    try:
        current_branch = repo.active_branch.name
    except Exception as exc:
        return {"error": f"Could not determine current branch: {str(exc)}"}

    # resolve remote owner/repo via URL parsing
    try:
        owner, repo_name = _get_remote_owner_repo(repo)
    except ValueError as exc:
        return {"error": str(exc)}

    # if the title does not start with a conventional prefix (feat:, fix:, refactor:)
    # prepend "feat:" by default.
    if not re.match(r"^(feat|fix|refactor|docs|style|test):", title, re.I):
        title = f"feat: {title}"

    # authenticate with GitHub via token
    token = os.getenv("GH_PAT")
    if not token:
        return {"error": "Missing GITHUB_TOKEN env variable"}

    try:
        gh = Github(token)
        gh_repo = gh.get_repo(f"{owner}/{repo_name}")
    except GithubException as exc:
        return {
            "error": f"GitHub authentication / repo lookup failed: {exc.data.get('message', str(exc))}"
        }

    # create the PR
    try:
        pr = gh_repo.create_pull(
            title=title,
            body=body,
            head=current_branch,
            base=base,
        )
    except GithubException as exc:
        return {"error": f"Failed to create pull request: {exc.data.get('message', str(exc))}"}

    return {"pr_url": pr.html_url}


# --- AI Code Review Parser (gives llm list of PMD and Checkstyle issues) --------

@mcp.tool
def runReviewParser():
    """Run reviewParser.jar on the given source directory and return the issues.

    Returns:
        dict: A dictionary with "issues" on success, or "error" on failure.
    """
    try:
        _require_source_dir()
    except RuntimeError as exc:
        return {"error": str(exc)}

    try:
        proc = subprocess.run(
            ["java", "-jar", "parsers/reviewParser.jar", SOURCE_DIR],
            capture_output=True,
            text=True,
        )

    except FileNotFoundError as exc:
        return {"error": f"java or parser jar not found: {exc}"}
    except Exception as exc:
        return {"error": f"Failed to run parser: {exc}"}

    if proc.returncode != 0:
        return {"error": proc.stderr.strip()}

    try:
        parsed = json.loads(proc.stdout)
    except Exception as exc:
        return {"error": f"Failed to parse parser output: {exc}"}

    return {"issues": parsed[:100]}


if __name__ == "__main__":
    load_dotenv()
    mcp.run(transport="http")


