import os
import platform
import subprocess
import sqlite3
import random
import string
import shutil
from datetime import datetime, timedelta
from typing import Optional
from fastapi import FastAPI, Form, UploadFile, File, HTTPException, Request
from fastapi.responses import HTMLResponse, RedirectResponse, FileResponse, JSONResponse
from pydantic import BaseModel

app = FastAPI(
    title="Homelab Control Center",
    description="Premium mini homelab portal and utility suite running native on Android Termux.",
    version="2.0.0"
)

# Helper function to generate random keys
def generate_key(length=6):
    chars = string.ascii_letters + string.digits
    return "".join(random.choice(chars) for _ in range(length))

# Initialize databases
def init_db():
    db_dir = os.path.expanduser("~/homelab/data")
    os.makedirs(db_dir, exist_ok=True)
    
    # URL Shortener Table
    conn = sqlite3.connect(os.path.join(db_dir, "shortener.db"))
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
    conn.close()

    # Pastebin Table
    conn = sqlite3.connect(os.path.join(db_dir, "pastebin.db"))
    c = conn.cursor()
    c.execute("""
        CREATE TABLE IF NOT EXISTS pastes (
            id TEXT PRIMARY KEY,
            content TEXT NOT NULL,
            title TEXT,
            language TEXT,
            created_at TEXT NOT NULL,
            expires_at TEXT
        )
    """)
    conn.commit()
    conn.close()

    # Create upload directory
    upload_dir = os.path.join(db_dir, "uploads")
    os.makedirs(upload_dir, exist_ok=True)

@app.on_event("startup")
def startup_event():
    init_db()

# DB Helpers
def get_db_connection(name):
    db_path = os.path.expanduser(f"~/homelab/data/{name}.db")
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    return conn

# Main Portal HTML template
PORTAL_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🏠 Homelab Control Portal</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700&family=Inter:wght@300;400;500;600&display=swap" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #080c14;
            --panel-bg: rgba(17, 24, 39, 0.7);
            --border-color: rgba(255, 255, 255, 0.08);
            --accent-cyan: #00f2fe;
            --accent-purple: #4facfe;
            --accent-green: #10b981;
            --accent-red: #ef4444;
            --text-main: #f3f4f6;
            --text-muted: #9ca3af;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
        }

        body {
            background-color: var(--bg-dark);
            color: var(--text-main);
            font-family: 'Inter', sans-serif;
            min-height: 100vh;
            display: flex;
            background-image: radial-gradient(circle at 10% 20%, rgba(0, 242, 254, 0.05) 0%, transparent 40%),
                              radial-gradient(circle at 90% 80%, rgba(79, 172, 254, 0.05) 0%, transparent 40%);
        }

        /* Sidebar Nav */
        .sidebar {
            width: 260px;
            background: rgba(10, 15, 28, 0.95);
            border-right: 1px solid var(--border-color);
            padding: 2rem 1.5rem;
            display: flex;
            flex-direction: column;
            gap: 2rem;
            position: fixed;
            height: 100vh;
            z-index: 10;
        }

        .logo {
            font-family: 'Outfit', sans-serif;
            font-size: 1.4rem;
            font-weight: 700;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            display: flex;
            align-items: center;
            gap: 0.75rem;
        }

        .nav-links {
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }

        .nav-item {
            padding: 0.85rem 1rem;
            border-radius: 10px;
            cursor: pointer;
            display: flex;
            align-items: center;
            gap: 1rem;
            font-size: 0.95rem;
            font-weight: 500;
            color: var(--text-muted);
            transition: all 0.2s ease;
        }

        .nav-item:hover, .nav-item.active {
            color: var(--text-main);
            background: rgba(255, 255, 255, 0.05);
            box-shadow: inset 3px 0 0 var(--accent-cyan);
        }

        .nav-item.active i {
            color: var(--accent-cyan);
        }

        /* Main Content */
        .main-content {
            margin-left: 260px;
            padding: 2.5rem;
            width: calc(100% - 260px);
            max-width: 1200px;
            min-height: 100vh;
        }

        .header-section {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 2rem;
        }

        .header-title h1 {
            font-family: 'Outfit', sans-serif;
            font-size: 2rem;
            font-weight: 600;
        }

        .header-title p {
            color: var(--text-muted);
            font-size: 0.9rem;
            margin-top: 0.25rem;
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            background: rgba(16, 185, 129, 0.1);
            color: var(--accent-green);
            padding: 0.5rem 1rem;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
            border: 1px solid rgba(16, 185, 129, 0.2);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background-color: var(--accent-green);
            border-radius: 50%;
            box-shadow: 0 0 10px var(--accent-green);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }

        /* Views */
        .view-pane {
            display: none;
        }

        .view-pane.active {
            display: flex;
            flex-direction: column;
            gap: 2rem;
        }

        /* Cards and Layouts */
        .card-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
            gap: 1.5rem;
        }

        .card {
            background: var(--panel-bg);
            border: 1px solid var(--border-color);
            border-radius: 16px;
            padding: 1.5rem;
            backdrop-filter: blur(12px);
            transition: transform 0.2s, border-color 0.2s;
        }

        .card:hover {
            transform: translateY(-2px);
            border-color: rgba(0, 242, 254, 0.15);
        }

        .card-title {
            font-size: 1rem;
            font-weight: 600;
            color: var(--text-muted);
            margin-bottom: 1rem;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .metric-value {
            font-size: 1.8rem;
            font-weight: 700;
            margin-bottom: 0.5rem;
        }

        .progress-bar-container {
            width: 100%;
            height: 8px;
            background: rgba(255, 255, 255, 0.05);
            border-radius: 4px;
            overflow: hidden;
            margin: 0.75rem 0;
        }

        .progress-bar {
            height: 100%;
            background: linear-gradient(90deg, var(--accent-cyan), var(--accent-purple));
            width: 0%;
            transition: width 0.5s ease-in-out;
        }

        .sub-value {
            font-size: 0.85rem;
            color: var(--text-muted);
        }

        /* Form Controls */
        .form-group {
            margin-bottom: 1.25rem;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
        }

        label {
            font-size: 0.9rem;
            font-weight: 600;
            color: var(--text-muted);
        }

        input, textarea, select {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 0.75rem 1rem;
            color: var(--text-main);
            font-size: 0.95rem;
            transition: border-color 0.2s;
            width: 100%;
        }

        input:focus, textarea:focus, select:focus {
            outline: none;
            border-color: var(--accent-cyan);
            background: rgba(255, 255, 255, 0.05);
        }

        .btn {
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
            color: #000;
            font-weight: 700;
            border: none;
            border-radius: 8px;
            padding: 0.75rem 1.5rem;
            cursor: pointer;
            transition: opacity 0.2s, transform 0.1s;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 0.5rem;
            font-size: 0.95rem;
        }

        .btn:hover {
            opacity: 0.9;
        }

        .btn:active {
            transform: scale(0.98);
        }

        .btn-danger {
            background: var(--accent-red);
            color: white;
        }

        /* Tables */
        .table-container {
            width: 100%;
            overflow-x: auto;
            margin-top: 1rem;
        }

        table {
            width: 100%;
            border-collapse: collapse;
            text-align: left;
        }

        th, td {
            padding: 1rem;
            border-bottom: 1px solid var(--border-color);
            font-size: 0.9rem;
        }

        th {
            font-weight: 600;
            color: var(--text-muted);
        }

        tr:hover td {
            background: rgba(255, 255, 255, 0.02);
        }

        .action-icon {
            cursor: pointer;
            color: var(--text-muted);
            transition: color 0.2s;
        }

        .action-icon:hover {
            color: var(--accent-cyan);
        }

        .action-icon.delete:hover {
            color: var(--accent-red);
        }

        /* Drag & Drop Upload Zone */
        .upload-zone {
            border: 2px dashed var(--border-color);
            border-radius: 12px;
            padding: 2.5rem;
            text-align: center;
            cursor: pointer;
            transition: border-color 0.2s;
            background: rgba(255, 255, 255, 0.01);
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 0.75rem;
        }

        .upload-zone:hover, .upload-zone.dragover {
            border-color: var(--accent-cyan);
            background: rgba(0, 242, 254, 0.02);
        }

        .upload-zone i {
            font-size: 2.5rem;
            color: var(--text-muted);
        }

        /* Alerts */
        .alert {
            padding: 1rem;
            border-radius: 8px;
            margin-bottom: 1.5rem;
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-size: 0.9rem;
        }

        .alert-success {
            background: rgba(16, 185, 129, 0.1);
            border: 1px solid rgba(16, 185, 129, 0.2);
            color: var(--accent-green);
        }

        .alert-error {
            background: rgba(239, 68, 68, 0.1);
            border: 1px solid rgba(239, 68, 68, 0.2);
            color: var(--accent-red);
        }

        .temp-grid {
            display: grid;
            grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
            gap: 0.75rem;
        }

        .temp-item {
            display: flex;
            flex-direction: column;
            padding: 0.75rem;
            background: rgba(255, 255, 255, 0.02);
            border-radius: 10px;
            border: 1px solid var(--border-color);
        }

        .temp-name {
            font-size: 0.75rem;
            color: var(--text-muted);
            margin-bottom: 0.25rem;
        }

        .temp-val {
            font-size: 1.1rem;
            font-weight: 700;
            color: var(--accent-cyan);
        }
    </style>
</head>
<body>
    <div class="sidebar">
        <div class="logo"><i class="fa-solid fa-server"></i> Homelab</div>
        <ul class="nav-links">
            <li class="nav-item active" onclick="switchView('system')"><i class="fa-solid fa-chart-line"></i> System Dashboard</li>
            <li class="nav-item" onclick="switchView('shortener')"><i class="fa-solid fa-link"></i> Link Shortener</li>
            <li class="nav-item" onclick="switchView('pastebin')"><i class="fa-solid fa-file-code"></i> Pastebin</li>
            <li class="nav-item" onclick="switchView('files')"><i class="fa-solid fa-folder-open"></i> File Sharing</li>
            <li class="nav-item" onclick="switchView('status')"><i class="fa-solid fa-heart-pulse"></i> Status Monitor</li>
        </ul>
    </div>

    <div class="main-content">
        <header class="header-section">
            <div class="header-title">
                <h1 id="view-title">System Dashboard</h1>
                <p id="view-desc">Real-time resources and device metrics from your Vsmart Live 4.</p>
            </div>
            <div class="status-badge">
                <div class="status-dot"></div>
                HOMELAB ACTIVE
            </div>
        </header>

        <div id="alert-box" style="display: none;"></div>

        <!-- VIEW: System Dashboard -->
        <div id="view-system" class="view-pane active">
            <div class="card-grid">
                <div class="card">
                    <div class="card-title"><i class="fa-solid fa-clock"></i> Uptime & OS</div>
                    <div class="metric-value" id="uptime">Loading...</div>
                    <div class="sub-value" id="platform">Loading...</div>
                </div>

                <div class="card">
                    <div class="card-title"><i class="fa-solid fa-memory"></i> Memory Usage</div>
                    <div class="metric-value" id="ram-usage">Loading...</div>
                    <div class="progress-bar-container">
                        <div class="progress-bar" id="ram-progress"></div>
                    </div>
                    <div class="sub-value" id="ram-detail">Loading...</div>
                </div>

                <div class="card">
                    <div class="card-title"><i class="fa-solid fa-hard-drive"></i> Disk Storage</div>
                    <div class="metric-value" id="disk-usage">Loading...</div>
                    <div class="progress-bar-container">
                        <div class="progress-bar" id="disk-progress"></div>
                    </div>
                    <div class="sub-value" id="disk-detail">Loading...</div>
                </div>
            </div>

            <div class="card-grid">
                <div class="card">
                    <div class="card-title"><i class="fa-solid fa-microchip"></i> System Core</div>
                    <div class="metric-value" id="cpu-load">Loading...</div>
                    <div class="sub-value" id="cpu-arch">Loading...</div>
                </div>

                <div class="card" style="grid-column: span 2;">
                    <div class="card-title"><i class="fa-solid fa-temperature-half"></i> Temperature Sensors</div>
                    <div class="temp-grid" id="temp-grid">
                        <div class="sub-value">Loading sensors...</div>
                    </div>
                </div>
            </div>
        </div>

        <!-- VIEW: Link Shortener -->
        <div id="view-shortener" class="view-pane">
            <div class="card">
                <div class="card-title"><i class="fa-solid fa-plus"></i> Shorten a New URL</div>
                <form id="form-shorten" onsubmit="handleShorten(event)">
                    <div class="form-group">
                        <label for="long_url">Destination URL</label>
                        <input type="url" id="long_url" placeholder="https://example.com/very-long-path" required>
                    </div>
                    <div class="form-group">
                        <label for="short_code">Custom Short Code (Optional)</label>
                        <input type="text" id="short_code" placeholder="my-custom-path">
                    </div>
                    <button type="submit" class="btn"><i class="fa-solid fa-bolt"></i> Generate Link</button>
                </form>
            </div>

            <div class="card">
                <div class="card-title"><i class="fa-solid fa-list-ul"></i> Active Shortened Links</div>
                <div class="table-container">
                    <table>
                        <thead>
                            <tr>
                                <th>Code</th>
                                <th>Original URL</th>
                                <th>Clicks</th>
                                <th>Created At</th>
                            </tr>
                        </thead>
                        <tbody id="table-links">
                            <tr><td colspan="4" class="sub-value">Loading links...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- VIEW: Pastebin -->
        <div id="view-pastebin" class="view-pane">
            <div class="card">
                <div class="card-title"><i class="fa-solid fa-pen-nib"></i> Create a New Paste</div>
                <form id="form-paste" onsubmit="handlePaste(event)">
                    <div class="form-group">
                        <label for="paste_title">Title (Optional)</label>
                        <input type="text" id="paste_title" placeholder="Untitled snippet">
                    </div>
                    <div class="form-group">
                        <label for="paste_lang">Language Syntax</label>
                        <select id="paste_lang">
                            <option value="text">Plain Text</option>
                            <option value="javascript">JavaScript</option>
                            <option value="python">Python</option>
                            <option value="html">HTML</option>
                            <option value="css">CSS</option>
                            <option value="json">JSON</option>
                            <option value="bash">Bash/Shell</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="paste_expiry">Expiry Period</label>
                        <select id="paste_expiry">
                            <option value="">Never</option>
                            <option value="10">10 Minutes</option>
                            <option value="60">1 Hour</option>
                            <option value="1440">1 Day</option>
                            <option value="10080">1 Week</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label for="paste_content">Content</label>
                        <textarea id="paste_content" rows="10" placeholder="Paste your code or text notes here..." required></textarea>
                    </div>
                    <button type="submit" class="btn"><i class="fa-solid fa-cloud-arrow-up"></i> Save Paste</button>
                </form>
            </div>

            <div class="card">
                <div class="card-title"><i class="fa-solid fa-clock-rotate-left"></i> Recent Pastes</div>
                <div class="table-container">
                    <table>
                        <thead>
                            <tr>
                                <th>Title</th>
                                <th>Language</th>
                                <th>Expires At</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody id="table-pastes">
                            <tr><td colspan="4" class="sub-value">Loading snippets...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- VIEW: File Sharing -->
        <div id="view-files" class="view-pane">
            <div class="card">
                <div class="card-title"><i class="fa-solid fa-cloud-arrow-up"></i> Upload File</div>
                <div class="upload-zone" id="drop-zone" onclick="document.getElementById('file-input').click()">
                    <i class="fa-solid fa-file-arrow-up"></i>
                    <p>Drag & drop your files here or click to browse</p>
                    <input type="file" id="file-input" style="display:none;" onchange="handleFileUpload(event)">
                </div>
            </div>

            <div class="card">
                <div class="card-title"><i class="fa-solid fa-file-invoice"></i> Shared Directory Files</div>
                <div class="table-container">
                    <table>
                        <thead>
                            <tr>
                                <th>Filename</th>
                                <th>Size</th>
                                <th>Actions</th>
                            </tr>
                        </thead>
                        <tbody id="table-files">
                            <tr><td colspan="3" class="sub-value">Loading files...</td></tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>

        <!-- VIEW: Status Page -->
        <div id="view-status" class="view-pane">
            <div class="card">
                <div class="card-title"><i class="fa-solid fa-heart-pulse"></i> Homelab Services Monitor</div>
                <div class="table-container">
                    <table>
                        <thead>
                            <tr>
                                <th>Service Name</th>
                                <th>Protocol/Port</th>
                                <th>Status</th>
                                <th>Ping Latency</th>
                            </tr>
                        </thead>
                        <tbody id="table-status">
                            <tr>
                                <td>FastAPI Gateway</td>
                                <td>HTTP (Port 3000)</td>
                                <td><span style="color:var(--accent-green);font-weight:700;"><i class="fa-solid fa-check-circle"></i> ONLINE</span></td>
                                <td id="latency-api">-- ms</td>
                            </tr>
                            <tr>
                                <td>SSH Server</td>
                                <td>TCP (Port 8022)</td>
                                <td id="status-ssh"><span class="sub-value">Checking...</span></td>
                                <td>--</td>
                            </tr>
                            <tr>
                                <td>Shortener Database</td>
                                <td>SQLite</td>
                                <td id="status-db-shortener"><span class="sub-value">Checking...</span></td>
                                <td>--</td>
                            </tr>
                            <tr>
                                <td>Pastebin Database</td>
                                <td>SQLite</td>
                                <td id="status-db-pastebin"><span class="sub-value">Checking...</span></td>
                                <td>--</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    </div>

    <script>
        const viewsInfo = {
            system: { title: "System Dashboard", desc: "Real-time resources and device metrics from your Vsmart Live 4." },
            shortener: { title: "Link Shortener", desc: "Rút gọn link nhanh và theo dõi lượt truy cập." },
            pastebin: { title: "Pastebin Code-Share", desc: "Lưu và chia sẻ nhanh snippets, ghi chú hỗ trợ highlight code." },
            files: { title: "File Sharing Server", desc: "Tải lên và tải xuống file nhanh chóng qua trình duyệt." },
            status: { title: "Services Status Page", desc: "Giám sát thời gian thực trạng thái các dịch vụ nội bộ." }
        };

        function switchView(viewName) {
            document.querySelectorAll('.nav-item').forEach(el => el.classList.remove('active'));
            document.querySelectorAll('.view-pane').forEach(el => el.classList.remove('active'));
            
            const activeNav = Array.from(document.querySelectorAll('.nav-item')).find(el => el.getAttribute('onclick').includes(viewName));
            if (activeNav) activeNav.classList.add('active');
            
            document.getElementById(`view-${viewName}`).classList.add('active');
            document.getElementById('view-title').innerText = viewsInfo[viewName].title;
            document.getElementById('view-desc').innerText = viewsInfo[viewName].desc;

            // Trigger updates
            if (viewName === 'shortener') loadLinks();
            if (viewName === 'pastebin') loadPastes();
            if (viewName === 'files') loadFiles();
            if (viewName === 'status') loadServiceStatuses();
        }

        function showAlert(message, isError = false) {
            const alertBox = document.getElementById('alert-box');
            alertBox.className = `alert ${isError ? 'alert-error' : 'alert-success'}`;
            alertBox.innerHTML = `<span>${message}</span><i class="fa-solid fa-xmark action-icon" onclick="this.parentElement.style.display='none'"></i>`;
            alertBox.style.display = 'flex';
            setTimeout(() => { alertBox.style.display = 'none'; }, 6000);
        }

        function formatBytes(kbString) {
            const kb = parseInt(kbString.replace(/[^0-9]/g, ''));
            if (isNaN(kb)) return 'N/A';
            const gb = kb / (1024 * 1024);
            return gb.toFixed(2) + ' GB';
        }

        async function updateStats() {
            try {
                const response = await fetch('/system');
                const data = await response.json();

                document.getElementById('uptime').innerText = data.uptime.replace('up ', '');
                document.getElementById('platform').innerText = data.platform;

                if (data.memory.MemTotal && data.memory.MemAvailable) {
                    const total = parseInt(data.memory.MemTotal.replace(/[^0-9]/g, ''));
                    const available = parseInt(data.memory.MemAvailable.replace(/[^0-9]/g, ''));
                    const used = total - available;
                    const percent = (used / total) * 100;
                    
                    document.getElementById('ram-usage').innerText = percent.toFixed(1) + '%';
                    document.getElementById('ram-progress').style.width = percent + '%';
                    document.getElementById('ram-detail').innerText = `Used: ${formatBytes(used.toString() + ' kB')} / ${formatBytes(data.memory.MemTotal)}`;
                }

                if (data.disk.usage) {
                    document.getElementById('disk-usage').innerText = data.disk.usage;
                    document.getElementById('disk-progress').style.width = data.disk.usage;
                    document.getElementById('disk-detail').innerText = `Used: ${data.disk.used} / ${data.disk.total} (Free: ${data.disk.available})`;
                }

                document.getElementById('cpu-load').innerText = data.architecture;
                document.getElementById('cpu-arch').innerText = `Python ${data.python_version} • Node ${data.node}`;

                const tempGrid = document.getElementById('temp-grid');
                tempGrid.innerHTML = '';
                if (typeof data.temperature === 'object' && Object.keys(data.temperature).length > 0) {
                    const prioritySensors = ['cpu-1-1-usr', 'cpuss-3-usr', 'gpu-usr', 'battery', 'xo-therm', 'sdm-therm'];
                    let count = 0;
                    for (const sensor of prioritySensors) {
                        if (data.temperature[sensor]) {
                            const item = document.createElement('div');
                            item.className = 'temp-item';
                            item.innerHTML = `<span class="temp-name">${sensor}</span><span class="temp-val">${data.temperature[sensor]}</span>`;
                            tempGrid.appendChild(item);
                            count++;
                        }
                    }
                    if (count === 0) {
                        for (const [key, value] of Object.entries(data.temperature).slice(0, 6)) {
                            const item = document.createElement('div');
                            item.className = 'temp-item';
                            item.innerHTML = `<span class="temp-name">${key}</span><span class="temp-val">${value}</span>`;
                            tempGrid.appendChild(item);
                        }
                    }
                } else {
                    tempGrid.innerHTML = '<div class="sub-value">No temperature sensors available</div>';
                }
            } catch (error) {
                console.error('Failed to fetch stats:', error);
            }
        }

        // --- Link Shortener Operations ---
        async function loadLinks() {
            try {
                const res = await fetch('/api/links');
                const data = await res.json();
                const tbody = document.getElementById('table-links');
                tbody.innerHTML = '';
                if (data.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="4" class="sub-value" style="text-align:center;">No links shortened yet.</td></tr>';
                    return;
                }
                data.forEach(link => {
                    const tr = document.createElement('tr');
                    const shortUrl = `${window.location.origin}/${link.code}`;
                    tr.innerHTML = `
                        <td><a href="${shortUrl}" target="_blank" style="color:var(--accent-cyan); font-weight:600;">${link.code}</a></td>
                        <td style="max-width:300px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;">${link.long_url}</td>
                        <td>${link.clicks}</td>
                        <td>${link.created_at}</td>
                    `;
                    tbody.appendChild(tr);
                });
            } catch (e) {
                console.error(e);
            }
        }

        async function handleShorten(e) {
            e.preventDefault();
            const longUrl = document.getElementById('long_url').value;
            const code = document.getElementById('short_code').value;
            const formData = new FormData();
            formData.append('long_url', longUrl);
            if(code) formData.append('code', code);
            
            try {
                const res = await fetch('/api/shorten', { method: 'POST', body: formData });
                const data = await res.json();
                if(res.ok) {
                    const shortUrl = `${window.location.origin}/${data.code}`;
                    showAlert(`Generated Short Link: <a href="${shortUrl}" target="_blank">${shortUrl}</a>`);
                    document.getElementById('form-shorten').reset();
                    loadLinks();
                } else {
                    showAlert(data.detail || "Error occurred", true);
                }
            } catch (err) {
                showAlert(err.message, true);
            }
        }

        // --- Pastebin Operations ---
        async function loadPastes() {
            try {
                const res = await fetch('/api/pastes');
                const data = await res.json();
                const tbody = document.getElementById('table-pastes');
                tbody.innerHTML = '';
                if(data.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="4" class="sub-value" style="text-align:center;">No pastes saved yet.</td></tr>';
                    return;
                }
                data.forEach(paste => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td>${paste.title || 'Untitled'}</td>
                        <td><span style="background:rgba(255,255,255,0.05); padding:2px 6px; border-radius:4px;">${paste.language}</span></td>
                        <td>${paste.expires_at || 'Never'}</td>
                        <td>
                            <a href="/paste/${paste.id}" target="_blank" class="action-icon"><i class="fa-solid fa-eye"></i> View</a>
                        </td>
                    `;
                    tbody.appendChild(tr);
                });
            } catch(e) {
                console.error(e);
            }
        }

        async function handlePaste(e) {
            e.preventDefault();
            const title = document.getElementById('paste_title').value;
            const language = document.getElementById('paste_lang').value;
            const expiry = document.getElementById('paste_expiry').value;
            const content = document.getElementById('paste_content').value;
            
            const formData = new FormData();
            formData.append('content', content);
            if(title) formData.append('title', title);
            formData.append('language', language);
            if(expiry) formData.append('expiry', expiry);

            try {
                const res = await fetch('/api/paste', { method: 'POST', body: formData });
                const data = await res.json();
                if(res.ok) {
                    const pasteUrl = `${window.location.origin}/paste/${data.id}`;
                    showAlert(`Paste Created: <a href="${pasteUrl}" target="_blank">${pasteUrl}</a>`);
                    document.getElementById('form-paste').reset();
                    loadPastes();
                } else {
                    showAlert(data.detail || "Error saving paste", true);
                }
            } catch(err) {
                showAlert(err.message, true);
            }
        }

        // --- File Sharing Operations ---
        async function loadFiles() {
            try {
                const res = await fetch('/api/files');
                const data = await res.json();
                const tbody = document.getElementById('table-files');
                tbody.innerHTML = '';
                if(data.length === 0) {
                    tbody.innerHTML = '<tr><td colspan="3" class="sub-value" style="text-align:center;">No files uploaded yet.</td></tr>';
                    return;
                }
                data.forEach(file => {
                    const tr = document.createElement('tr');
                    tr.innerHTML = `
                        <td>${file.name}</td>
                        <td>${(file.size / 1024 / 1024).toFixed(2)} MB</td>
                        <td>
                            <a href="/api/files/download/${file.name}" target="_blank" class="action-icon" style="margin-right:15px;"><i class="fa-solid fa-download"></i></a>
                            <i class="fa-solid fa-trash-can action-icon delete" onclick="deleteFile('${file.name}')"></i>
                        </td>
                    `;
                    tbody.appendChild(tr);
                });
            } catch(e) {
                console.error(e);
            }
        }

        async function handleFileUpload(e) {
            const file = e.target.files[0];
            if(!file) return;
            const formData = new FormData();
            formData.append('file', file);
            
            try {
                showAlert("Uploading file...");
                const res = await fetch('/api/files/upload', { method: 'POST', body: formData });
                const data = await res.json();
                if(res.ok) {
                    showAlert("File uploaded successfully!");
                    loadFiles();
                } else {
                    showAlert(data.detail || "Upload failed", true);
                }
            } catch(err) {
                showAlert(err.message, true);
            }
        }

        async function deleteFile(filename) {
            if(!confirm(`Delete ${filename}?`)) return;
            try {
                const res = await fetch(`/api/files/${filename}`, { method: 'DELETE' });
                if(res.ok) {
                    showAlert("File deleted successfully!");
                    loadFiles();
                } else {
                    const data = await res.json();
                    showAlert(data.detail || "Delete failed", true);
                }
            } catch(err) {
                showAlert(err.message, true);
            }
        }

        // Drag & Drop
        const dropZone = document.getElementById('drop-zone');
        ['dragenter', 'dragover'].forEach(name => {
            dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.add('dragover'); }, false);
        });
        ['dragleave', 'drop'].forEach(name => {
            dropZone.addEventListener(name, (e) => { e.preventDefault(); dropZone.classList.remove('dragover'); }, false);
        });
        dropZone.addEventListener('drop', (e) => {
            const files = e.dataTransfer.files;
            if(files.length > 0) {
                document.getElementById('file-input').files = files;
                handleFileUpload({ target: { files: files } });
            }
        });

        // --- Status Page Checking ---
        async function loadServiceStatuses() {
            // Check API Latency
            const start = performance.now();
            try {
                await fetch('/health');
                const latency = (performance.now() - start).toFixed(0);
                document.getElementById('latency-api').innerText = `${latency} ms`;
            } catch {
                document.getElementById('latency-api').innerText = 'Error';
            }

            // Fetch details from status endpoint
            try {
                const res = await fetch('/api/status');
                const data = await res.json();
                
                document.getElementById('status-ssh').innerHTML = data.ssh === 'online' ? 
                    '<span style="color:var(--accent-green);font-weight:700;"><i class="fa-solid fa-check-circle"></i> ONLINE</span>' :
                    '<span style="color:var(--accent-red);font-weight:700;"><i class="fa-solid fa-circle-exclamation"></i> OFFLINE</span>';

                document.getElementById('status-db-shortener').innerHTML = data.db_shortener === 'healthy' ? 
                    '<span style="color:var(--accent-green);font-weight:700;"><i class="fa-solid fa-check-circle"></i> HEALTHY</span>' :
                    '<span style="color:var(--accent-red);font-weight:700;"><i class="fa-solid fa-circle-exclamation"></i> ERROR</span>';

                document.getElementById('status-db-pastebin').innerHTML = data.db_pastebin === 'healthy' ? 
                    '<span style="color:var(--accent-green);font-weight:700;"><i class="fa-solid fa-check-circle"></i> HEALTHY</span>' :
                    '<span style="color:var(--accent-red);font-weight:700;"><i class="fa-solid fa-circle-exclamation"></i> ERROR</span>';
            } catch {
                document.getElementById('status-ssh').innerHTML = '<span style="color:var(--accent-red)">Error check</span>';
            }
        }

        // Init
        updateStats();
        setInterval(updateStats, 3000);
    </script>
</body>
</html>
"""

# HTML template for view paste page
PASTE_VIEW_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>📋 Paste {paste_id} - Pastebin</title>
    <link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;600;700&family=Fira+Code:wght@400;500&display=swap" rel="stylesheet">
    <link href="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/themes/prism-tomorrow.min.css" rel="stylesheet">
    <style>
        body {{
            background-color: #0d1117;
            color: #c9d1d9;
            font-family: 'Outfit', sans-serif;
            margin: 0;
            padding: 2rem 1rem;
            display: flex;
            justify-content: center;
        }}
        .container {{
            width: 100%;
            max-width: 900px;
            background: rgba(22, 27, 34, 0.8);
            border: 1px solid rgba(255, 255, 255, 0.08);
            border-radius: 12px;
            padding: 1.5rem 2rem;
            box-shadow: 0 4px 30px rgba(0, 0, 0, 0.5);
            backdrop-filter: blur(8px);
        }}
        header {{
            display: flex;
            justify-content: space-between;
            align-items: center;
            border-bottom: 1px solid rgba(255, 255, 255, 0.08);
            padding-bottom: 1rem;
            margin-bottom: 1.5rem;
        }}
        h1 {{
            font-size: 1.5rem;
            margin: 0;
            font-weight: 600;
            color: #58a6ff;
        }}
        .meta {{
            font-size: 0.85rem;
            color: #8b949e;
        }}
        .lang-badge {{
            background: rgba(88, 166, 255, 0.15);
            color: #58a6ff;
            padding: 0.2rem 0.6rem;
            border-radius: 4px;
            font-weight: 600;
        }}
        pre {{
            border-radius: 8px;
            overflow: auto;
            margin: 0;
        }}
        code {{
            font-family: 'Fira Code', monospace !important;
            font-size: 0.95rem !important;
        }}
        .back-link {{
            color: #58a6ff;
            text-decoration: none;
            font-size: 0.9rem;
            display: inline-flex;
            align-items: center;
            gap: 0.5rem;
            margin-top: 1.5rem;
        }}
        .back-link:hover {{
            text-decoration: underline;
        }}
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div>
                <h1>{title}</h1>
                <div class="meta" style="margin-top:0.4rem;">Created on: {created_at} | Expires: {expires_at}</div>
            </div>
            <span class="lang-badge">{language}</span>
        </header>
        <pre><code class="language-{language}">{content}</code></pre>
        <a href="/" class="back-link">← Go to Homelab Portal</a>
    </div>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/components/prism-core.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/prism/1.29.0/plugins/autoloader/prism-autoloader.min.js"></script>
</body>
</html>
"""

# Portal Route
@app.get("/", response_class=HTMLResponse)
def root():
    return PORTAL_HTML

@app.get("/health")
def health():
    return {"status": "healthy"}

# API: System Metrics
@app.get("/system")
def system_info():
    # Uptime
    try:
        uptime_raw = subprocess.check_output(["uptime", "-p"], text=True, timeout=5).strip()
    except Exception:
        uptime_raw = "unknown"

    # Memory
    meminfo = {}
    try:
        with open("/proc/meminfo", "r") as f:
            for line in f:
                parts = line.split(":")
                if len(parts) == 2:
                    key = parts[0].strip()
                    val = parts[1].strip()
                    if key in ("MemTotal", "MemFree", "MemAvailable"):
                        meminfo[key] = val
    except Exception:
        meminfo = {"error": "cannot read /proc/meminfo"}

    # Disk
    try:
        disk = subprocess.check_output(["df", "-h", os.path.expanduser("~")], text=True, timeout=5).strip().split("\n")[-1].split()
        disk_info = {
            "total": disk[1] if len(disk) > 1 else "?",
            "used": disk[2] if len(disk) > 2 else "?",
            "available": disk[3] if len(disk) > 3 else "?",
            "usage": disk[4] if len(disk) > 4 else "?"
        }
    except Exception:
        disk_info = {"error": "cannot read disk info"}

    # Temperatures
    temps = {}
    try:
        for zone_dir in sorted(os.listdir("/sys/class/thermal/")):
            if zone_dir.startswith("thermal_zone"):
                temp_path = f"/sys/class/thermal/{zone_dir}/temp"
                type_path = f"/sys/class/thermal/{zone_dir}/type"
                if os.path.isfile(temp_path):
                    with open(temp_path) as tf:
                        raw = tf.read().strip()
                    name = zone_dir
                    if os.path.isfile(type_path):
                        with open(type_path) as nf:
                            name = nf.read().strip()
                    if raw.isdigit() and int(raw) > 0:
                        temps[name] = f"{int(raw) // 1000}°C"
    except Exception:
        pass

    return {
        "platform": platform.platform(),
        "architecture": platform.machine(),
        "python_version": platform.python_version(),
        "node": platform.node(),
        "uptime": uptime_raw,
        "memory": meminfo,
        "disk": disk_info,
        "temperature": temps if temps else "not available"
    }

# --- LINK SHORTENER ENDPOINTS ---

@app.post("/api/shorten")
def api_shorten(long_url: str = Form(...), code: Optional[str] = Form(None)):
    conn = get_db_connection("shortener")
    c = conn.cursor()
    
    if code:
        code = code.strip()
        # Check uniqueness
        c.execute("SELECT code FROM urls WHERE code = ?", (code,))
        if c.fetchone():
            conn.close()
            raise HTTPException(status_code=400, detail="Short code is already in use.")
    else:
        # Generate random unique code
        for _ in range(10):
            code = generate_key(6)
            c.execute("SELECT code FROM urls WHERE code = ?", (code,))
            if not c.fetchone():
                break
        else:
            conn.close()
            raise HTTPException(status_code=500, detail="Failed to generate unique code.")

    c.execute(
        "INSERT INTO urls (code, long_url, created_at) VALUES (?, ?, ?)",
        (code, long_url, datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    )
    conn.commit()
    conn.close()
    
    return {"code": code, "long_url": long_url}

@app.get("/api/links")
def api_links():
    conn = get_db_connection("shortener")
    c = conn.cursor()
    c.execute("SELECT code, long_url, clicks, created_at FROM urls ORDER BY created_at DESC")
    rows = c.fetchall()
    conn.close()
    return [dict(row) for row in rows]

@app.get("/{code}")
def redirect_short(code: str):
    conn = get_db_connection("shortener")
    c = conn.cursor()
    c.execute("SELECT long_url, clicks FROM urls WHERE code = ?", (code,))
    row = c.fetchone()
    if not row:
        conn.close()
        # Check if it falls into Pastebin or File download before raising 404
        raise HTTPException(status_code=404, detail="Short link not found.")
    
    # Update click count
    c.execute("UPDATE urls SET clicks = clicks + 1 WHERE code = ?", (code,))
    conn.commit()
    conn.close()
    
    return RedirectResponse(url=row["long_url"])

# --- PASTEBIN ENDPOINTS ---

@app.post("/api/paste")
def api_paste(content: str = Form(...), title: Optional[str] = Form(""), language: Optional[str] = Form("text"), expiry: Optional[int] = Form(None)):
    conn = get_db_connection("pastebin")
    c = conn.cursor()
    
    paste_id = generate_key(8)
    created_at = datetime.now()
    
    expires_at = None
    if expiry:
        expires_at = (created_at + timedelta(minutes=expiry)).strftime("%Y-%m-%d %H:%M:%S")
        
    c.execute(
        "INSERT INTO pastes (id, content, title, language, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?)",
        (paste_id, content, title if title else "Untitled", language, created_at.strftime("%Y-%m-%d %H:%M:%S"), expires_at)
    )
    conn.commit()
    conn.close()
    
    return {"id": paste_id}

@app.get("/api/pastes")
def api_pastes():
    conn = get_db_connection("pastebin")
    c = conn.cursor()
    now_str = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    c.execute("SELECT id, title, language, expires_at, created_at FROM pastes WHERE expires_at IS NULL OR expires_at > ? ORDER BY created_at DESC LIMIT 20", (now_str,))
    rows = c.fetchall()
    conn.close()
    return [dict(row) for row in rows]

@app.get("/paste/{paste_id}", response_class=HTMLResponse)
def view_paste(paste_id: str):
    conn = get_db_connection("pastebin")
    c = conn.cursor()
    c.execute("SELECT title, content, language, created_at, expires_at FROM pastes WHERE id = ?", (paste_id,))
    row = c.fetchone()
    conn.close()
    
    if not row:
        raise HTTPException(status_code=404, detail="Paste not found.")
        
    # Check expiry
    if row["expires_at"]:
        expires = datetime.strptime(row["expires_at"], "%Y-%m-%d %H:%M:%S")
        if datetime.now() > expires:
            raise HTTPException(status_code=410, detail="This paste has expired.")
            
    # Escape HTML to prevent XSS
    escaped_content = row["content"].replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    
    return PASTE_VIEW_HTML.format(
        paste_id=paste_id,
        title=row["title"],
        language=row["language"],
        created_at=row["created_at"],
        expires_at=row["expires_at"] if row["expires_at"] else "Never",
        content=escaped_content
    )

# --- FILE SHARING ENDPOINTS ---

@app.get("/api/files")
def api_files():
    upload_dir = os.path.expanduser("~/homelab/data/uploads")
    files_list = []
    if os.path.exists(upload_dir):
        for name in os.listdir(upload_dir):
            path = os.path.join(upload_dir, name)
            if os.path.isfile(path):
                files_list.append({
                    "name": name,
                    "size": os.path.getsize(path)
                })
    return files_list

@app.post("/api/files/upload")
def upload_file(file: UploadFile = File(...)):
    upload_dir = os.path.expanduser("~/homelab/data/uploads")
    os.makedirs(upload_dir, exist_ok=True)
    
    # Sanitize filename
    filename = os.path.basename(file.filename)
    dest_path = os.path.join(upload_dir, filename)
    
    with open(dest_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    return {"filename": filename, "status": "success"}

@app.get("/api/files/download/{filename}")
def download_file(filename: str):
    file_path = os.path.expanduser(f"~/homelab/data/uploads/{filename}")
    if not os.path.isfile(file_path):
        raise HTTPException(status_code=404, detail="File not found.")
    return FileResponse(path=file_path, filename=filename)

@app.delete("/api/files/{filename}")
def delete_file(filename: str):
    file_path = os.path.expanduser(f"~/homelab/data/uploads/{filename}")
    if not os.path.isfile(file_path):
        raise HTTPException(status_code=404, detail="File not found.")
    try:
        os.remove(file_path)
        return {"status": "deleted"}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

# --- STATUS MONITOR ENDPOINT ---

@app.get("/api/status")
def system_status():
    status = {
        "ssh": "offline",
        "db_shortener": "error",
        "db_pastebin": "error"
    }
    
    # 1. Check SSH
    try:
        # Run local check to see if port 8022 is active
        output = subprocess.check_output("netstat -an | grep 8022", shell=True, text=True)
        if "LISTEN" in output or "8022" in output:
            status["ssh"] = "online"
    except Exception:
        # Fallback check using sshd process identification
        try:
            pgrep = subprocess.check_output("pgrep sshd", shell=True)
            if pgrep:
                status["ssh"] = "online"
        except Exception:
            pass

    # 2. Check Database Shortener
    try:
        conn = get_db_connection("shortener")
        conn.execute("SELECT 1")
        conn.close()
        status["db_shortener"] = "healthy"
    except Exception:
        pass
        
    # 3. Check Database Pastebin
    try:
        conn = get_db_connection("pastebin")
        conn.execute("SELECT 1")
        conn.close()
        status["db_pastebin"] = "healthy"
    except Exception:
        pass

    return status
