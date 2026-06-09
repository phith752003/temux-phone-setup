import os
import sqlite3
import random
import string
from fastapi import FastAPI, Request, HTTPException, Form
from fastapi.responses import RedirectResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
from slowapi import Limiter, _rate_limit_exceeded_handler
from slowapi.util import get_remote_address
from slowapi.errors import RateLimitExceeded
from pydantic import BaseModel, HttpUrl

# Initialize Rate Limiter
limiter = Limiter(key_func=get_remote_address)

app = FastAPI(
    title="Public API Gateway",
    description="Secure public-facing endpoints (URL Shortener, etc.)",
    version="1.0.0"
)

# Attach Limiter to App
app.state.limiter = limiter
app.add_exception_handler(RateLimitExceeded, _rate_limit_exceeded_handler)

# Secure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # In production, restrict to your frontend domain like https://s.phith.click
    allow_methods=["GET", "POST"],
    allow_headers=["*"],
)

# Helper function to generate random keys
def generate_key(length=6):
    chars = string.ascii_letters + string.digits
    return "".join(random.choice(chars) for _ in range(length))

def get_db_connection():
    # Database is shared with the Homelab control center
    db_path = os.path.expanduser("~/homelab/data/shortener.db")
    if not os.path.exists(db_path):
        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        conn = sqlite3.connect(db_path)
        c = conn.cursor()
        c.execute("""
            CREATE TABLE IF NOT EXISTS urls (
                code TEXT PRIMARY KEY,
                long_url TEXT NOT NULL,
                clicks INTEGER DEFAULT 0,
                created_at TEXT NOT NULL
            )
        """)
        conn.commit()
    else:
        conn = sqlite3.connect(db_path)
    
    conn.row_factory = sqlite3.Row
    return conn

class ShortenRequest(BaseModel):
    long_url: HttpUrl
    custom_code: str = None

@app.get("/health")
@limiter.limit("20/minute")
def health_check(request: Request):
    return {"status": "healthy", "service": "public-api"}

@app.post("/api/shorten")
@limiter.limit("5/minute") # Strict rate limit to prevent DB spam
async def shorten_url(request: Request, body: ShortenRequest):
    conn = get_db_connection()
    c = conn.cursor()
    
    long_url = str(body.long_url)
    code = body.custom_code
    
    if code:
        # Validate custom code
        if not code.isalnum() or len(code) > 20:
            raise HTTPException(status_code=400, detail="Custom code must be alphanumeric and max 20 chars")
        # Check if exists
        c.execute("SELECT code FROM urls WHERE code = ?", (code,))
        if c.fetchone():
            raise HTTPException(status_code=400, detail="Custom code already taken")
    else:
        # Generate random code
        for _ in range(5):
            code = generate_key()
            c.execute("SELECT code FROM urls WHERE code = ?", (code,))
            if not c.fetchone():
                break
        else:
            raise HTTPException(status_code=500, detail="Could not generate unique code")

    from datetime import datetime
    c.execute("INSERT INTO urls (code, long_url, created_at) VALUES (?, ?, ?)",
              (code, long_url, datetime.utcnow().isoformat()))
    conn.commit()
    conn.close()
    
    return {"short_url": f"https://s.phith.click/{code}", "code": code}

@app.get("/{code}")
@limiter.limit("60/minute") # Higher limit for redirections
async def redirect_to_url(request: Request, code: str):
    if not code.isalnum():
        raise HTTPException(status_code=400, detail="Invalid code")
        
    conn = get_db_connection()
    c = conn.cursor()
    c.execute("SELECT long_url FROM urls WHERE code = ?", (code,))
    row = c.fetchone()
    
    if not row:
        conn.close()
        raise HTTPException(status_code=404, detail="URL not found")
        
    # Increment clicks asynchronously in a real app, but here synchronously is fine for homelab
    c.execute("UPDATE urls SET clicks = clicks + 1 WHERE code = ?", (code,))
    conn.commit()
    conn.close()
    
    return RedirectResponse(url=row["long_url"], status_code=302)

@app.get("/api/stats/{code}")
@limiter.limit("10/minute")
async def get_stats(request: Request, code: str):
    conn = get_db_connection()
    c = conn.cursor()
    c.execute("SELECT clicks, created_at FROM urls WHERE code = ?", (code,))
    row = c.fetchone()
    conn.close()
    
    if not row:
        raise HTTPException(status_code=404, detail="URL not found")
        
    return dict(row)
