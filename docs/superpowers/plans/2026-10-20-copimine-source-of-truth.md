# CopiMine Source of Truth Consolidation Plan

> **For agentic workers:** Execute this plan task by task and keep the named source and destination boundaries intact.

**Goal:** Establish one reviewable CopiMine source checkout and one GitHub integration branch, preserve current game/server functionality, exclude the standalone public site and launcher, and reclaim only verified generated launcher/site output.

**Architecture:** Use `codex/end-rift-26-3-integrated-final` as the integration base and build the canonical line in `codex/copimine-source-of-truth-20261020`. Publish reviewable source changes directly on the canonical branch in small, reviewed commits before deleting any local output. Preserve every existing worktree and branch as a separate rollback source. Keep generated binaries out unless they are intentional release output. The clean local checkout is `D:\Desktop\Copimine\copimine-source-of-truth`.

**Tech Stack:** Git worktrees and GitHub; Java/Paper plugins; Python admin backend and contract tests; PowerShell build and validation scripts; Minecraft 26.3 pinned toolchain.

**Spec:** User request in this conversation; previously approved End Rift 26.3 and client bridge V4 requirements remain constraints.

## Global Constraints

- Never reset, clean, overwrite, or mass-commit any existing dirty worktree.
- Before cleanup, publish reviewable source/config/docs changes from in-scope dirty worktrees to GitHub recovery refs so the original state can be restored.
- Preserve the active recovered arena world, server runtime, secrets, unique release data, and all unrelated plugin sources.
- Keep operational `admin-web/backend`, cabinet, shop, authentication, event, election, and server-management code unless a replacement is proven.
- Exclude standalone `site-v6` and `CopiMineLauncher` features from the canonical branch because the user said they are not needed.
- Do not force-push, overwrite `main`, or delete remote branches without a separately reviewed exact list.
- Push only verified, source-scoped commits to `codex/copimine-source-of-truth-20261020`; preserve `main` and all existing development branches.
- Keep private keys and runtime secrets outside Git.

## Review Focus

- Dirty local edits overlap candidate plugin branches; compare file-by-file before integration and retain unrelated changes.
- Candidate branches contain generated JARs, resource packs, and modpack ZIPs; rebuild from reviewed source and verify hashes before treating them as current.
- The current active branch worktree has many ignored pytest outputs and diagnostic evidence; remove only inventoried generated output.
- The launcher/site worktrees contain Minecraft runtime and instance folders; preserve those while pruning only verified site/launcher output.
- `admin-web` mixes public routes with core account, shop, and operations functionality; never remove the tree wholesale.

---

### Task 1: Publish recoverable source checkpoints

**Files:**
- Dirty in-scope worktrees identified by `git status`.
- GitHub canonical ref `codex/copimine-source-of-truth-20261020`.

- [ ] Inventory tracked and untracked changes by worktree, distinguishing source/config/docs from generated output, runtime, secrets, and worlds.
- [ ] Create small backup commits only for reviewable source/config/docs and tests that are not secrets or runtime/world data.
- [ ] Push each reviewed source checkpoint to the canonical branch and verify its remote SHA before any cleanup.

### Task 2: Establish the clean integration checkout

**Files:**
- Create a clean local worktree under `D:\Desktop\Copimine\copimine-source-of-truth` from the current integration tip.
- Do not modify the existing `copimine-main` checkout or active End Rift worktree.

- [x] Verify the integration base commit and create the new local checkout from it.
- [ ] Record branch, commit, remote, and initial clean status in a short audit note.
- [ ] Confirm the new checkout has no local runtime world or secret files.

### Task 3: Review and integrate non-site plugin/core deltas

**Files:**
- Candidate source areas: `copimine-artifacts`, `copimine-admin-plugin`, `copimine-narcotics`, `copimine-world-core`, `minecraft/server/plugins/AuthEffects`, `admin-web/backend`, `deploy`, `resourcepacks`, `tools/minecraft-26.3`, and their tests.
- Candidate refs: `origin/codex/artifact-runtime-release-20260813`, `origin/codex/bed-captcha-fix`, `origin/codex/current-release-shield-armor`, and local `codex/bridge-deps-fix`.

- [ ] Compare each candidate source diff to the integration tip and current dirty checkout; classify duplicate, superseded, and still-needed changes.
- [ ] Integrate only still-needed source and tests, in small reviewable commits.
- [ ] Regenerate JARs/resource-pack/modpack outputs from accepted source and verify the release manifest against the build.
- [ ] Run focused plugin, resource-pack, security-contract, and migration-profile checks after each feature group.

### Task 4: Keep launcher and standalone public site out of canonical source

**Files:**
- Exclude `CopiMineLauncher/**` and standalone `site-v6/**` from the canonical integration branch.
- Preserve core `admin-web/backend/**` and required account, shop, cabinet, event, election, and server operations.

- [ ] Confirm no required game/runtime/plugin module depends on the standalone launcher or site.
- [ ] Do not delete remote site history or dirty local site worktrees during code integration.

### Task 5: Verify the integrated branch

**Files:**
- Existing repository verification scripts and project CI configuration.

- [ ] Run focused tests for every merged feature group.
- [ ] Run the full project build and required repository gates on the final candidate commit.
- [ ] Verify exact Java/profile pins, generated artifact hashes, and the integration branch's complete source tree.
- [ ] Review the final diff for unrelated changes, secrets, runtime data, and generated-output drift.

### Task 6: Publish the verified source of truth

**Files:**
- GitHub branch `codex/copimine-source-of-truth-20261020`.

- [ ] Push only verified commits to the new canonical branch.
- [ ] Confirm GitHub's branch head equals the tested local commit and record commit links.
- [ ] Leave `main` and unrelated development branches unchanged.

### Task 7: Reclaim verified launcher/site artifacts and retain user data

**Files:**
- Candidate output: `D:\Desktop\Copimine\copimine-main\.worktrees\site-launcher-audit\artifacts\launcher`.
- Preserve `admin-web`, launcher source/branch metadata until separately retired, all Minecraft `instance*` and runtime roots, recovered arena data, release-stage worlds, and test snapshots that contain plugin/runtime evidence.

- [ ] Recheck the candidate path is ignored/untracked and contains no Minecraft world markers immediately before deletion.
- [ ] Delete only the exact reviewed generated launcher/site artifact tree; report its measured reclaimed size.
- [ ] Re-inventory the root and list remaining cleanup candidates without deleting any registered worktree or world-bearing archive.
