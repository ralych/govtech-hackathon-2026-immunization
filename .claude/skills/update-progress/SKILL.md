---
name: update-progress
description: Create, read, or append to the branch-scoped progress file under progress/. Use when the user says "update the progress file", "write progress to file", "read the progress", or similar, and at the end of any completed task. Handles per-branch file naming to avoid merge conflicts.
---

# /update-progress — Manage the Branch-Scoped Progress File

Progress notes are kept in **`progress/<branch-name>-progress.txt`** so each branch owns its own file and merges don't conflict. On `main`, the file is `progress/main-progress.txt`.

## 1. Verify the current branch FIRST

Do not trust cached context. Before reading or writing, run:

```bash
git -C /workspaces/govtech-hackathon-2026-immunization rev-parse --abbrev-ref HEAD
```

Take that exact output as `<branch-name>`. If the branch name contains a `/` (e.g. `chore/AI-72-foo`), replace every `/` with `-` to keep the progress file flat (→ `chore-AI-72-foo`). This is the only transformation — no other characters are changed.

The target file path is:

```
/workspaces/govtech-hackathon-2026-immunization/progress/<sanitized-branch-name>-progress.txt
```

Ensure the `progress/` directory exists before writing:

```bash
mkdir -p /workspaces/govtech-hackathon-2026-immunization/progress
```

## 2. Reading the progress file

- On a feature branch: read the whole file (usually small).
- On `main` (`progress/main-progress.txt`): it may be large — read only the tail (e.g. last ~200 lines) unless the user asks for more.
- If the file does not exist yet, report that no progress has been recorded for this branch and offer to create it on the next update.

## 3. Appending an entry (default behavior)

**Always append** to the file unless the user explicitly asks to edit a specific earlier entry. Never rewrite or truncate existing content.

Get the current timestamp:

```bash
date '+%Y-%m-%d %H:%M'
```

Append a new entry using exactly this structure:

```
---
## YYYY-MM-DD HH:MM - Brief Title

**What:** Description of what was done
**Why:** Reason for the change
**Files:** List of files modified (if applicable)
**Status:** Current state / what to do next
```

Rules for the entry:
- Start with a `---` separator line.
- Use the verified timestamp and a short, specific title (not "updates" or "fixes").
- Keep `What` / `Why` / `Status` to one or two lines each.
- Omit `Files:` only if the change truly touched no files. You can list the files on separate lines for readability
- If creating the file for the first time on this branch, the first entry is still a regular appended entry — no special header is needed.

## 4. Updating a specific older entry

Only when the user explicitly asks to fix or amend an earlier entry:
- Locate the entry by its title/timestamp.
- Edit that entry in place, preserving all surrounding entries.
- Do not reorder entries.

## 5. Do NOT

- Do not touch the legacy root-level `claude-progress.txt` — the branch-scoped file under `progress/` replaces it.
- Do not write to a different branch's progress file.
- Do not summarize or compress older entries unprompted.
