# Pushing this project to GitHub (from your computer)

The repository `iNAYATechLab/File-Manager-Pro` already exists on GitHub and is
currently **empty**. Push this project folder from your PC:

```bash
# 1) open a terminal in this project folder (the folder that contains README.md)
cd path/to/File-Manager-Pro

# 2) make sure the GitHub repo is the remote (it may already be set)
git remote -v
git remote add origin https://github.com/iNAYATechLab/File-Manager-Pro.git
git remote set-url origin https://github.com/iNAYATechLab/File-Manager-Pro.git

# 3) create the first commit
git add .
git commit -m "Initial commit: Android file manager (Kotlin + Material 3)"

# 4) push to the default branch
git branch -M main
git push -u origin main
```

> If asked for credentials, use a **Personal Access Token (PAT)** instead of
> your account password: GitHub → Settings → Developer settings → Personal
> access tokens → Generate new token (classic) with `repo` scope.

## Alternative: let me push it for you

If you prefer, reply with a fine-grained or classic **Personal Access Token**
(scoped to *this repository only*, contents read/write). I can commit and push
everything from here — the token is used only for that push and is never
stored. Note: anyone with your token gets the same access, so revoke it right
afterwards if you like.
