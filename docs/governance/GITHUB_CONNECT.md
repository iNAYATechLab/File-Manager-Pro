# Master Prompt — Connect GitHub in a Sandbox Workspace

Standard, reusable procedure for any agent, any user, any organization, any project.
Give the block below to the agent, replacing only the four placeholders.

| Placeholder | Meaning | Example |
|---|---|---|
| `<ORG>` | GitHub org or user that owns the repo | `iNWEB-Official` |
| `<REPO>` | Repository name | `iNAuthPass` |
| `<WORKDIR>` | Local project path | `/home/user/iNAuthPass` |
| `<PROFILE>` | Short id for this account, used to isolate sessions | `sami`, `work`, `bot1` |

---

```text
ROLE: Connect this workspace to GitHub (GitHub CLI, OAuth Device Flow).
TARGET: <ORG>/<REPO>   WORKDIR: <WORKDIR>   PROFILE: <PROFILE>

RULES
1. Binary -> /home/user/.local/bin (outside the snapshot quota).
2. Session -> /home/user/.config/gh-<PROFILE> (persists). Always export GH_CONFIG_DIR to it.
   One profile per account; multiple accounts coexist without conflict.
3. Never run `gh auth login --web` / bare `gh auth login` — no TTY, it hangs. Use the device flow.
4. Never print, log, or commit the token. Never ask for a PAT unless the device flow fails twice.
5. Device codes expire in 15 min. If expired, issue a NEW code; never reuse.
6. Create/delete repos only if explicitly instructed. Only Step 3 needs the user.

STEP 1 — INSTALL
  export GH_CONFIG_DIR=/home/user/.config/gh-<PROFILE>
  export PATH=/home/user/.local/bin:$PATH
  mkdir -p /home/user/.local/bin "$GH_CONFIG_DIR" /home/user/.cache
  cd /tmp
  V=$(curl -sL https://api.github.com/repos/cli/cli/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4)
  VN=${V#v}
  curl -sL -o gh.tgz "https://github.com/cli/cli/releases/download/$V/gh_${VN}_linux_amd64.tar.gz"
  tar xzf gh.tgz && cp "gh_${VN}_linux_amd64/bin/gh" /home/user/.local/bin/gh
  rm -rf gh.tgz "gh_${VN}_linux_amd64" && gh --version
  # Never hardcode the version in the URL — an unresolved tag returns HTTP 404.

STEP 2 — REQUEST DEVICE CODE   (178c6fc778ccc68e1d6a = official GitHub CLI app; keep it)
  curl -s -X POST https://github.com/login/device/code -H 'Accept: application/json' \
    -d 'client_id=178c6fc778ccc68e1d6a&scope=repo read:org workflow gist' \
    -o /home/user/.cache/dev-<PROFILE>.json
  cat /home/user/.cache/dev-<PROFILE>.json
  # Add admin:org only if the task must manage teams.

STEP 3 — SHOW THE USER (then wait)
  URL: https://github.com/login/device   CODE: <user_code>   Valid: 15 minutes
  Enter code -> Continue -> Authorize GitHub CLI.

STEP 4 — POLL IN BACKGROUND (background-process tool, not a blocking shell call)
  cat > "$GH_CONFIG_DIR/poll.sh" <<'EOF'
  #!/usr/bin/env bash
  P="__PROFILE__"
  export PATH=/home/user/.local/bin:$PATH
  export GH_CONFIG_DIR="/home/user/.config/gh-$P"
  DC=$(grep -o '"device_code":"[^"]*"' "/home/user/.cache/dev-$P.json" | cut -d'"' -f4)
  for i in $(seq 1 170); do
    R=$(curl -s -X POST https://github.com/login/oauth/access_token -H 'Accept: application/json' \
      -d "client_id=178c6fc778ccc68e1d6a&device_code=$DC&grant_type=urn:ietf:params:oauth:grant-type:device_code")
    if echo "$R" | grep -q access_token; then
      echo "$R" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4 \
        | gh auth login --hostname github.com --git-protocol https --with-token
      echo AUTH_OK; gh auth status; gh auth setup-git; exit 0
    fi
    echo "waiting $(echo "$R" | grep -o '"error":"[^"]*"')"; sleep 5
  done
  echo TIMEOUT
  EOF
  sed -i "s/__PROFILE__/<PROFILE>/" "$GH_CONFIG_DIR/poll.sh"; chmod +x "$GH_CONFIG_DIR/poll.sh"
  # Start it, then wait for the log line AUTH_OK or TIMEOUT. "authorization_pending" is normal.

STEP 5 — VERIFY (before any write)
  gh auth status
  gh api user --jq .login
  gh api user/orgs --jq '.[].login'      # <ORG> must appear
  gh repo list <ORG> --limit 50

STEP 6 — MAKE IT REUSABLE
  The gh binary and .git/config are NOT snapshotted; the gh config dir IS.
  cat > /home/user/gh-setup-<PROFILE>.sh <<'EOF'
  #!/usr/bin/env bash
  set -e
  export GH_CONFIG_DIR=/home/user/.config/gh-<PROFILE>
  export PATH=/home/user/.local/bin:$PATH
  if [ ! -x /home/user/.local/bin/gh ]; then
    mkdir -p /home/user/.local/bin; cd /tmp
    V=$(curl -sL https://api.github.com/repos/cli/cli/releases/latest | grep -m1 '"tag_name"' | cut -d'"' -f4); VN=${V#v}
    curl -sL -o gh.tgz "https://github.com/cli/cli/releases/download/$V/gh_${VN}_linux_amd64.tar.gz"
    tar xzf gh.tgz && cp "gh_${VN}_linux_amd64/bin/gh" /home/user/.local/bin/gh
    rm -rf gh.tgz "gh_${VN}_linux_amd64"
  fi
  gh auth setup-git
  cd <WORKDIR>
  git config user.name  "$(gh api user --jq .login)"
  git config user.email "$(gh api user --jq '.id')+$(gh api user --jq .login)@users.noreply.github.com"
  git remote get-url origin >/dev/null 2>&1 || git remote add origin https://github.com/<ORG>/<REPO>.git
  gh auth status
  EOF
  chmod +x /home/user/gh-setup-<PROFILE>.sh

EVERY FUTURE TURN
  Run `bash /home/user/gh-setup-<PROFILE>.sh` before any git/gh command.
  If it reports "Logged in", do NOT start a new device login. Re-run Steps 2-4 only if no valid token.
  Switching account/project = switch <PROFILE> (and export the matching GH_CONFIG_DIR).

TROUBLESHOOTING
  404 on download .......... version was guessed; resolve the latest tag from the API
  "code does not work" ..... expired; issue a fresh code, never reuse
  authorization_pending .... normal; user has not approved yet, keep polling
  gh: command not found .... binary wiped; run gh-setup-<PROFILE>.sh
  Author identity unknown .. .git/config wiped; gh-setup-<PROFILE>.sh restores it
  403 on push .............. missing scope or org access; re-auth with correct scopes

FINAL REPORT
  STATUS: / ACCOUNT: / ORG ACCESS: / GH VERSION: / CONFIG PATH: / SESSION PERSISTENCE:
```

## Security Notes
- Tokens live only in `/home/user/.config/gh-<PROFILE>/hosts.yml`; that path must never be committed
  (`.gitignore` already blocks `.env`, keys, and credential files — see `SECURITY.md`).
- Request the narrowest scope set the task needs.
- Revoke an unused session with `gh auth logout --hostname github.com`.
