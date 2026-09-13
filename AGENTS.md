# Agent instructions

## Pull requests — do not merge without explicit approval

Cloud agents working in this repo must **not merge pull requests** unless the user explicitly says to merge (e.g. "merge the PR").

1. Create branches and **draft** PRs.
2. Push code and describe what changed.
3. Stop and let the user review and merge.

Do not use `gh pr merge` or equivalent unless the user clearly requests it. Having merge access in the environment does not mean you should use it.

See also: `.cursor/rules/pr-merge-policy.mdc`
