# Issue tracker: Local Markdown

Issues and specs for this repo live as markdown files in `.scratch/`.

`.scratch/` is git-ignored, so `rg` skips it by default — and because it is also
a hidden directory, `--no-ignore` alone is not enough: search it with
`rg --hidden --no-ignore` (or `rg -uu`). `grep -r` does **not** honour
`.gitignore`; it does search `.scratch/`, but from the repo root it also walks
`build/` and `.worktrees/`. When ripgrep is absent, scope the search to the
tracker instead:

```sh
grep -rn "needle" .scratch/<feature-slug>/   # only the tracker
find .scratch -name '*.md'                   # list specs and tickets
```

`fd` has the same hidden-directory caveat: `-I` is not enough, use `fd -HI …`
(or pass `.scratch/` explicitly). Never run a bare `grep -r .` from the repo
root.

## Worktrees

`.scratch/` exists only in the primary checkout: it is git-ignored, so
`git worktree add` does not copy it, and a worktree has no tracker files. Read
specs and tickets by absolute path into the primary checkout's `.scratch/`
(`git worktree list` shows which checkout that is). Never copy `.scratch/` into
a worktree — the two copies would diverge.

## Conventions

- One feature per directory: `.scratch/<feature-slug>/`
- The spec is `.scratch/<feature-slug>/spec.md`
- Implementation issues are one file per ticket at `.scratch/<feature-slug>/issues/<NN>-<slug>.md`, numbered from `01`, never a single combined tickets file
- Triage state is recorded as a `Status:` line near the top of each issue file (see `triage-labels.md` for the role strings)
- Comments and conversation history append to the bottom of the file under a `## Comments` heading

## When a skill says "publish to the issue tracker"

Create a new file under `.scratch/<feature-slug>/` (creating the directory if needed).

## When a skill says "fetch the relevant ticket"

Read the file at the referenced path. The user will normally pass the path or the issue number directly.

## Wayfinding operations

Used by `/wayfinder`. The **map** is a file with one **child** file per ticket.

- **Map**: `.scratch/<effort>/map.md` (the Notes / Decisions-so-far / Fog body).
- **Child ticket**: `.scratch/<effort>/issues/NN-<slug>.md`, numbered from `01`, with the question in the body. A `Type:` line records the ticket type (`research`/`prototype`/`grilling`/`task`); a `Status:` line records `claimed`/`resolved`.
- **Blocking**: a `Blocked by: NN, NN` line near the top. A ticket is unblocked when every file it lists is `resolved`.
- **Frontier**: scan `.scratch/<effort>/issues/` for files that are open, unblocked, and unclaimed; first by number wins.
- **Claim**: set `Status: claimed` and save before any work.
- **Resolve**: append the answer under an `## Answer` heading, set `Status: resolved`, then append a context pointer (gist + link) to the map's Decisions-so-far in `map.md`.
