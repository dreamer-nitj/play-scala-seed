# implementing the following things

1. basic users service to list/insert/update/delete users
2. persist the users in in-memory database H2
3. Use Actor system for getting the users
4. Using play framework organised as controllers for hosting endpoints
5. Using slick ORM from scala for database interaction in scala
6. Using JWT for token generation/ validation and session validations

## 🚀 Running the App

Run the app using:
```bash
sbt "api/run"
```

## 🛠 Development Workflow

This project uses `pre-commit` to ensure code quality and security.

### 1. Prerequisites
Install `pre-commit` on your machine:
```bash
brew install pre-commit
```

### 2. Setup
Install the git hooks in this repository:
```bash
pre-commit install
```

### 3. Local Verification
The hooks run automatically on every `git commit`. You can also run them manually at any time:
```bash
# Run all checks on all files
pre-commit run --all-files

# Run a specific check (e.g., scalafmt)
pre-commit run scalafmt --all-files
```

### 💡 Included Checks
- **Security:** Scans for private keys and hardcoded secrets.
- **Hygiene:** Blocks `println` (use `Logger` instead) and `TODO`/`FIXME` markers.
- **Quality:** Runs `sbt scalafmtCheckAll` and `sbt compile`.
- **Safety:** Prevents direct commits to the `main` branch.
