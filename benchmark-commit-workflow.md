# Commit on parent branch, then rebase feature branch on top

Goal: working changes land as a new commit on `PARENT` (the feature branch's parent), and `FEATURE` is replayed on top of it. No stash — `checkout` carries uncommitted changes across when there's no conflict.

```sh
git checkout PARENT && git commit -am "msg"          # commit lands on PARENT; PARENT@{1} = old tip
git rebase --onto PARENT PARENT@{1} FEATURE          # replay FEATURE's commits onto the new tip
```

Here: `PARENT=5.0.x-nitrite-rebased`, `FEATURE=benchmark-5.0.x-new`.
