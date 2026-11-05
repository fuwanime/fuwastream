<!-- skill-routing:start -->
## Skill routing

Invoke the matching skill yourself before starting the work. Project: mobile, Android app (Kotlin, Jetpack Compose, Gradle). Pipeline: superpowers. No mobile-specific skill is installed, so only the core rows apply.

| Situation | Invoke |
|---|---|
| New feature idea / "build X" | `superpowers:brainstorming` → `superpowers:writing-plans` → `superpowers:subagent-driven-development` (long plans: `superpowers:executing-plans`) |
| Writing feature or fix code | `superpowers:test-driven-development` |
| Normal bug, failing test | `superpowers:systematic-debugging` |
| Hard, flaky, intermittent or performance bug | `mattpocock-skills:diagnosing-bugs` |
| Several unrelated failures at once | `superpowers:dispatching-parallel-agents` |
| Review a diff before merge | `ponytail:ponytail-review` (correct, safe, lean) |
| Acting on review feedback | `superpowers:receiving-code-review` |
| About to claim done, commit or open a PR | `superpowers:verification-before-completion` |
| Work finished, merge or PR | `superpowers:finishing-a-development-branch` |
| PR description | `mattpocock-skills:pr` |
| Domain terms, GLOSSARY, ADR | `mattpocock-skills:domain-modeling` |
| Module design, where a seam goes | `mattpocock-skills:codebase-design` |
| Whole-repo quality audit | `ponytail:ponytail-audit` |
| Spec, RFC or design doc for humans | `writing:doc-coauthoring` |

User-only commands for this pipeline (tell the user the exact command to type): `/mattpocock-skills:handoff` (move to a new session), `/mattpocock-skills:grill-me` (stress-test an idea), `/mattpocock-skills:improve-codebase-architecture`, `/mattpocock-skills:wait-what` (explanation did not land).

Rules:
- One pipeline per feature. Its specs and plans live only in `docs/superpowers/`.
- Inside a superpowers plan, superpowers TDD wins over ponytail's "one small test".
- "Review" alone means `ponytail:ponytail-review`; name another review skill to use it instead.
- Brainstorming, specs, plans, grilling and research reports are written in full. If caveman mode is active, ask the user to type `/caveman off` first.
- `/caveman:caveman-init` pipes a downloaded script into node; leave it unrun.
- Commits and PRs carry no Claude co-author lines.

Full guide: `/home/sakamoto/reports/Panduan pakai skill Claude.md`.
<!-- skill-routing:end -->
