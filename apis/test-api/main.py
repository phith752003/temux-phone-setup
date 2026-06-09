"""
Homelab Control Center API – Vsmart Live 4
FastAPI server serving a beautiful dynamic web dashboard and system endpoints.
"""
from fastapi import FastAPI
from fastapi.responses import HTMLResponse
from datetime import datetime
import platform
import os
import subprocess

app = FastAPI(
    title="Homelab Control Center",
    description="Mini homelab API running on Vsmart Live 4",
    version="1.0.0"
)

# HTML template for the premium dashboard
DASHBOARD_HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>🏠 Homelab Control Center</title>
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap" rel="stylesheet">
    <style>
        :root {
            --bg-dark: #0a0e17;
            --panel-bg: rgba(18, 26, 47, 0.6);
            --border-color: rgba(255, 255, 255, 0.08);
            --accent-cyan: #00f2fe;
            --accent-purple: #4facfe;
            --text-main: #f3f4f6;
            --text-muted: #9ca3af;
            --success-green: #10b981;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: 'Inter', sans-serif;
        }

        body {
            background-color: var(--bg-dark);
            color: var(--text-main);
            min-height: 100vh;
            padding: 2rem 1rem;
            display: flex;
            justify-content: center;
            align-items: center;
            background-image: radial-gradient(circle at 10% 20%, rgba(0, 242, 254, 0.05) 0%, transparent 40%),
                              radial-gradient(circle at 90% 80%, rgba(79, 172, 254, 0.05) 0%, transparent 40%);
        }

        .container {
            width: 100%;
            max-width: 900px;
            display: grid;
            gap: 1.5rem;
        }

        header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 1rem 0;
            border-bottom: 1px solid var(--border-color);
        }

        .logo {
            font-size: 1.5rem;
            font-weight: 700;
            background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            display: flex;
            align-items: center;
            gap: 0.5rem;
        }

        .status-badge {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            background: rgba(16, 185, 129, 0.1);
            color: var(--success-green);
            padding: 0.4rem 0.8rem;
            border-radius: 20px;
            font-size: 0.85rem;
            font-weight: 600;
            border: 1px solid rgba(16, 185, 129, 0.2);
        }

        .status-dot {
            width: 8px;
            height: 8px;
            background-color: var(--success-green);
            border-radius: 50%;
            box-shadow: 0 0 10px var(--success-green);
            animation: pulse 2s infinite;
        }

        @keyframes pulse {
            0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
            70% { transform: scale(1); box-shadow: 0 0 0 8px rgba(16, 185, 129, 0); }
            100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
        }

        .grid {
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
            border-color: rgba(0, 242, 254, 0.2);
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
            margin-top: 0.5rem;
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
            margin-top: 0.5rem;
        }

        .temp-grid {
            display: grid;
            grid-template-columns: repeat(2, 1fr);
            gap: 0.5rem;
            font-size: 0.85rem;
        }

        .temp-item {
            display: flex;
            justify-content: space-between;
            padding: 0.3rem 0.5rem;
            background: rgba(255, 255, 255, 0.02);
            border-radius: 6px;
            border: 1px solid rgba(255, 255, 255, 0.03);
        }

        .temp-name {
            color: var(--text-muted);
        }

        .temp-val {
            font-weight: 600;
            color: var(--accent-cyan);
        }

        footer {
            text-align: center;
            font-size: 0.8rem;
            color: var(--text-muted);
            margin-top: 2rem;
        }

        a {
            color: var(--accent-purple);
            text-decoration: none;
        }

        a:hover {
            text-decoration: underline;
        }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div class="logo">🏠 Homelab Control Center</div>
            <div class="status-badge">
                <div class="status-dot"></div>
                ONLINE
            </div>
        </header>

        <div class="grid">
            <!-- System Card -->
            <div class="card">
                <div class="card-title">⏱️ Uptime & OS</div>
                <div class="metric-value" id="uptime">Loading...</div>
                <div class="sub-value" id="platform">Loading...</div>
            </div>

            <!-- RAM Card -->
            <div class="card">
                <div class="card-title">💾 RAM Usage</div>
                <div class="metric-value" id="ram-usage">Loading...</div>
                <div class="progress-bar-container">
                    <div class="progress-bar" id="ram-progress"></div>
                </div>
                <div class="sub-value" id="ram-detail">Loading...</div>
            </div>

            <!-- Storage Card -->
            <div class="card">
                <div class="card-title">💿 Storage</div>
                <div class="metric-value" id="disk-usage">Loading...</div>
                <div class="progress-bar-container">
                    <div class="progress-bar" id="disk-progress"></div>
                </div>
                <div class="sub-value" id="disk-detail">Loading...</div>
            </div>
        </div>

        <div class="grid">
            <!-- CPU Info -->
            <div class="card">
                <div class="card-title">📊 CPU Info</div>
                <div class="metric-value" id="cpu-load">Loading...</div>
                <div class="sub-value" id="cpu-arch">Loading...</div>
            </div>

            <!-- Temperatures -->
            <div class="card" style="grid-column: span 2;">
                <div class="card-title">🌡️ Temperatures</div>
                <div class="temp-grid" id="temp-grid">
                    <div class="sub-value">Loading sensors...</div>
                </div>
            </div>
        </div>

        <footer>
            Vsmart Live 4 Homelab Server • Powered by <a href="/docs" target="_blank">FastAPI</a> & Termux Native
        </footer>
    </div>

    <script>
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

                // Update Uptime & OS
                document.getElementById('uptime').innerText = data.uptime.replace('up ', '');
                document.getElementById('platform').innerText = data.platform;

                // Update RAM
                if (data.memory.MemTotal && data.memory.MemAvailable) {
                    const total = parseInt(data.memory.MemTotal.replace(/[^0-9]/g, ''));
                    const available = parseInt(data.memory.MemAvailable.replace(/[^0-9]/g, ''));
                    const used = total - available;
                    const percent = (used / total) * 100;
                    
                    document.getElementById('ram-usage').innerText = percent.toFixed(1) + '%';
                    document.getElementById('ram-progress').style.width = percent + '%';
                    document.getElementById('ram-detail').innerText = `Used: ${formatBytes(used.toString() + ' kB')} / ${formatBytes(data.memory.MemTotal)}`;
                }

                // Update Disk
                if (data.disk.usage) {
                    document.getElementById('disk-usage').innerText = data.disk.usage;
                    document.getElementById('disk-progress').style.width = data.disk.usage;
                    document.getElementById('disk-detail').innerText = `Used: ${data.disk.used} / ${data.disk.total} (Available: ${data.disk.available})`;
                }

                // Update CPU Load
                document.getElementById('cpu-load').innerText = data.architecture;
                document.getElementById('cpu-arch').innerText = `Python ${data.python_version} • Node ${data.node}`;

                // Update Temps
                const tempGrid = document.getElementById('temp-grid');
                tempGrid.innerHTML = '';
                if (typeof data.temperature === 'object' && Object.keys(data.temperature).length > 0) {
                    // Show top 6 important sensors to keep UI clean
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

                    // Fallback to general list if priority not found
                    if (count === 0) {
                        for (const [key, value] of Object.entries(data.temperature).slice(0, 8)) {
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

        // Initial load
        updateStats();
        // Update every 3 seconds
        setInterval(updateStats, 3000);
    </script>
</body>
</html>
"""

@app.get("/", response_class=HTMLResponse)
def root():
    return DASHBOARD_HTML


@app.get("/health")
def health():
    return {
        "status": "healthy",
        "timestamp": datetime.now().isoformat()
    }


@app.get("/time")
def get_time():
    return {
        "utc": datetime.utcnow().isoformat(),
        "local": datetime.now().isoformat(),
        "timezone": str(datetime.now().astimezone().tzinfo)
    }


@app.get("/system")
def system_info():
    # Uptime
    try:
        uptime_raw = subprocess.check_output(
            ["uptime", "-p"], text=True, timeout=5
        ).strip()
    except Exception:
        uptime_raw = "unknown"

    # Memory
    try:
        with open("/proc/meminfo", "r") as f:
            meminfo = {}
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
        disk = subprocess.check_output(
            ["df", "-h", os.path.expanduser("~")], text=True, timeout=5
        ).strip().split("\n")[-1].split()
        disk_info = {
            "total": disk[1] if len(disk) > 1 else "?",
            "used": disk[2] if len(disk) > 2 else "?",
            "available": disk[3] if len(disk) > 3 else "?",
            "usage": disk[4] if len(disk) > 4 else "?"
        }
    except Exception:
        disk_info = {"error": "cannot read disk info"}

    # Temperature
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
