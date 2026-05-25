# Alfred Backend — FastAPI Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a standalone FastAPI project (`alfred-backend`) with a JWT-protected `/login` + `/ask` endpoint that the Minecraft mod will call.

**Architecture:** Single `main.py` app. `/login` validates credentials from `.env` and issues a signed JWT. `/ask` verifies the bearer token and returns a mock speak action. All config comes from `.env` via `python-dotenv`.

**Tech Stack:** Python, FastAPI, uvicorn, python-jose, python-dotenv, pytest, httpx

---

> **Prerequisites:** Python 3.11+ installed. Run all commands from inside `alfred-backend/`.
> **Project location:** Create this project at `C:\Users\migue\Documents\Proyectos\alfred-backend\`

---

## File Map

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `alfred-backend/.env` | Credentials and JWT secret |
| Create | `alfred-backend/.gitignore` | Ignore .env and __pycache__ |
| Create | `alfred-backend/requirements.txt` | Production dependencies |
| Create | `alfred-backend/requirements-dev.txt` | Test dependencies |
| Create | `alfred-backend/conftest.py` | Set default env vars before test imports |
| Create | `alfred-backend/main.py` | Complete FastAPI app |
| Create | `alfred-backend/test_main.py` | Full test suite |

---

## Task 1 — Scaffold the project

**Files:**
- Create: `.env`
- Create: `.gitignore`
- Create: `requirements.txt`
- Create: `requirements-dev.txt`

- [ ] **Step 1: Create the project directory**

```powershell
New-Item -ItemType Directory -Path "C:\Users\migue\Documents\Proyectos\alfred-backend"
cd "C:\Users\migue\Documents\Proyectos\alfred-backend"
git init
```

- [ ] **Step 2: Create `.env`**

```
BUTLER_USERNAME=admin
BUTLER_PASSWORD=admin
SECRET_KEY=dev-secret-key-change-in-prod
ALGORITHM=HS256
ACCESS_TOKEN_EXPIRE_MINUTES=1440
```

- [ ] **Step 3: Create `.gitignore`**

```
.env
__pycache__/
*.pyc
.venv/
```

- [ ] **Step 4: Create `requirements.txt`**

```
fastapi
uvicorn[standard]
python-jose[cryptography]
python-dotenv
```

- [ ] **Step 5: Create `requirements-dev.txt`**

```
-r requirements.txt
pytest
httpx
```

- [ ] **Step 6: Create a virtual environment and install dependencies**

```powershell
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements-dev.txt
```

Expected: all packages install without errors.

- [ ] **Step 7: Commit scaffold**

```powershell
git add .gitignore requirements.txt requirements-dev.txt
git commit -m "chore: scaffold alfred-backend project"
```

---

## Task 2 — Write tests for `/login`

**Files:**
- Create: `conftest.py`
- Create: `test_main.py` (login section only)

- [ ] **Step 1: Create `conftest.py`**

```python
import os

os.environ.setdefault("BUTLER_USERNAME", "admin")
os.environ.setdefault("BUTLER_PASSWORD", "admin")
os.environ.setdefault("SECRET_KEY", "test-secret-key")
os.environ.setdefault("ALGORITHM", "HS256")
os.environ.setdefault("ACCESS_TOKEN_EXPIRE_MINUTES", "1440")
```

- [ ] **Step 2: Create `test_main.py` with login tests only**

```python
from fastapi.testclient import TestClient

from main import app

client = TestClient(app)


def test_login_success():
    response = client.post("/login", json={"username": "admin", "password": "admin"})
    assert response.status_code == 200
    body = response.json()
    assert "access_token" in body
    assert body["token_type"] == "bearer"
    assert isinstance(body["access_token"], str)
    assert len(body["access_token"]) > 0


def test_login_wrong_password():
    response = client.post("/login", json={"username": "admin", "password": "wrong"})
    assert response.status_code == 401


def test_login_wrong_username():
    response = client.post("/login", json={"username": "hacker", "password": "admin"})
    assert response.status_code == 401
```

- [ ] **Step 3: Run tests — should fail (main.py does not exist yet)**

```powershell
pytest test_main.py::test_login_success -v
```

Expected: `ModuleNotFoundError: No module named 'main'`

---

## Task 3 — Implement `main.py` — login only

**Files:**
- Create: `main.py`

- [ ] **Step 1: Create `main.py`**

```python
from datetime import datetime, timedelta, timezone

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError, jwt
from pydantic import BaseModel
import os

load_dotenv()

BUTLER_USERNAME = os.environ["BUTLER_USERNAME"]
BUTLER_PASSWORD = os.environ["BUTLER_PASSWORD"]
SECRET_KEY = os.environ["SECRET_KEY"]
ALGORITHM = os.environ["ALGORITHM"]
ACCESS_TOKEN_EXPIRE_MINUTES = int(os.environ["ACCESS_TOKEN_EXPIRE_MINUTES"])

app = FastAPI()
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="login")


class LoginRequest(BaseModel):
    username: str
    password: str


class Token(BaseModel):
    access_token: str
    token_type: str


class AskRequest(BaseModel):
    message: str


class ButlerAction(BaseModel):
    type: str
    message: str


def _create_token(username: str) -> str:
    expire = datetime.now(timezone.utc) + timedelta(minutes=ACCESS_TOKEN_EXPIRE_MINUTES)
    return jwt.encode({"sub": username, "exp": expire}, SECRET_KEY, algorithm=ALGORITHM)


def _get_current_user(token: str = Depends(oauth2_scheme)) -> str:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        username: str = payload.get("sub")
        if username is None:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)
        return username
    except JWTError:
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED)


@app.post("/login", response_model=Token)
async def login(req: LoginRequest) -> Token:
    if req.username != BUTLER_USERNAME or req.password != BUTLER_PASSWORD:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Credenciales inválidas.",
        )
    return Token(access_token=_create_token(req.username), token_type="bearer")


@app.post("/ask", response_model=list[ButlerAction])
async def ask(req: AskRequest, _user: str = Depends(_get_current_user)) -> list[ButlerAction]:
    return [ButlerAction(type="speak", message=f"Hola! Recibí: {req.message}")]
```

- [ ] **Step 2: Run login tests — should pass**

```powershell
pytest test_main.py::test_login_success test_main.py::test_login_wrong_password test_main.py::test_login_wrong_username -v
```

Expected:
```
test_main.py::test_login_success PASSED
test_main.py::test_login_wrong_password PASSED
test_main.py::test_login_wrong_username PASSED
```

- [ ] **Step 3: Commit**

```powershell
git add conftest.py main.py test_main.py
git commit -m "feat: add /login endpoint with JWT"
```

---

## Task 4 — Write tests for `/ask`

**Files:**
- Modify: `test_main.py` — append ask tests

- [ ] **Step 1: Add ask tests to `test_main.py`**

Append to the end of the existing `test_main.py`:

```python
def _get_token() -> str:
    response = client.post("/login", json={"username": "admin", "password": "admin"})
    return response.json()["access_token"]


def test_ask_without_token():
    response = client.post("/ask", json={"message": "hola"})
    assert response.status_code == 401


def test_ask_with_invalid_token():
    response = client.post(
        "/ask",
        json={"message": "hola"},
        headers={"Authorization": "Bearer token-invalido"},
    )
    assert response.status_code == 401


def test_ask_with_valid_token():
    token = _get_token()
    response = client.post(
        "/ask",
        json={"message": "hola Alfred"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 200
    actions = response.json()
    assert len(actions) == 1
    assert actions[0]["type"] == "speak"
    assert "hola Alfred" in actions[0]["message"]


def test_ask_response_is_list():
    token = _get_token()
    response = client.post(
        "/ask",
        json={"message": "test"},
        headers={"Authorization": f"Bearer {token}"},
    )
    assert isinstance(response.json(), list)
```

- [ ] **Step 2: Run ask tests — should pass (main.py already has /ask)**

```powershell
pytest test_main.py -v
```

Expected: all 7 tests PASSED.

If any fail, check the failure output — the most likely issue is a JWT decode error due to env var mismatch between conftest.py and main.py. Verify `conftest.py` `SECRET_KEY` matches what `main.py` uses.

- [ ] **Step 3: Commit**

```powershell
git add test_main.py
git commit -m "test: add /ask endpoint tests"
```

---

## Task 5 — Manual verification with curl

> This step verifies the server works end-to-end outside of the test harness.

- [ ] **Step 1: Start the server**

```powershell
uvicorn main:app --reload --port 8000
```

Leave this running. Open a second terminal.

- [ ] **Step 2: Get a token**

```powershell
$response = Invoke-RestMethod -Method Post -Uri "http://localhost:8000/login" `
  -ContentType "application/json" `
  -Body '{"username":"admin","password":"admin"}'
$token = $response.access_token
Write-Host "Token: $token"
```

Expected: a long JWT string printed.

- [ ] **Step 3: Call /ask with the token**

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8000/ask" `
  -ContentType "application/json" `
  -Headers @{Authorization="Bearer $token"} `
  -Body '{"message":"hola Alfred desde curl"}'
```

Expected:
```
type    message
----    -------
speak   Hola! Recibí: hola Alfred desde curl
```

- [ ] **Step 4: Verify /ask without token returns 401**

```powershell
try {
    Invoke-RestMethod -Method Post -Uri "http://localhost:8000/ask" `
      -ContentType "application/json" `
      -Body '{"message":"sin token"}'
} catch {
    Write-Host $_.Exception.Response.StatusCode
}
```

Expected: `Unauthorized`

- [ ] **Step 5: Stop the server (Ctrl+C) and commit**

```powershell
git add -A
git commit -m "feat: complete alfred-backend Phase 2 mock bridge"
```

---

## Gradle/run reference

| Command | Purpose |
|---------|---------|
| `uvicorn main:app --reload --port 8000` | Start server with auto-reload |
| `pytest -v` | Run all tests |
| `pytest test_main.py::test_login_success -v` | Run single test |