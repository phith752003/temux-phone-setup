"""
Homelab Control Center API – Vsmart Live 4
FastAPI server serving a beautiful dynamic web dashboard and system endpoints.
"""
from fastapi import FastAPI, UploadFile, File, Form, BackgroundTasks, HTTPException
from fastapi.responses import HTMLResponse, FileResponse
from datetime import datetime
import platform
import os
import subprocess
import shutil
import uuid
import sqlite3
import re
import urllib.request

app = FastAPI(
    title="Homelab Control Center",
    description="Mini homelab API running on Vsmart Live 4",
    version="1.0.0"
)

# Store status of rclone sync tasks and camera replay tasks
sync_tasks = {}
replay_tasks = {}

def get_camera_db():
    db_path = os.path.expanduser("~/homelab/data/camera.db")
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    c = conn.cursor()
    # Ensure the table is created
    c.execute("""
        CREATE TABLE IF NOT EXISTS recordings (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            filename TEXT NOT NULL,
            year INTEGER NOT NULL,
            month INTEGER NOT NULL,
            day INTEGER NOT NULL,
            start_time TEXT NOT NULL,
            end_time TEXT NOT NULL,
            remote_path TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
    """)
    conn.commit()
    conn.row_factory = sqlite3.Row
    return conn

def get_rclone_remotes():
    try:
        output = subprocess.check_output(
            ["rclone", "listremotes"], text=True, timeout=5
        ).strip()
        remotes = [r.strip() for r in output.split("\n") if r.strip()]
        return remotes
    except Exception:
        return ["gdrive:"]

def run_rclone_sync(task_id: str, file_path: str, remote_target: str):
    sync_tasks[task_id]["status"] = "syncing"
    
    if not remote_target:
        remote_target = "gdrive:"
        
    if remote_target.endswith(":"):
        remote_target = f"{remote_target}uploaded_files"
    else:
        remote_target = f"{remote_target}/uploaded_files"
            
    try:
        subprocess.check_call(
            ["rclone", "copy", file_path, remote_target],
            timeout=300
        )
        sync_tasks[task_id]["status"] = "completed"
        if os.path.exists(file_path):
            os.remove(file_path)
    except Exception as e:
        sync_tasks[task_id]["status"] = "failed"
        sync_tasks[task_id]["error"] = str(e)

def parse_gdrive_link(url: str):
    # Extract folder ID
    folder_match = re.search(r'drive\.google\.com/(?:drive/folders/|drive/u/\d+/folders/)([a-zA-Z0-9_-]{25,})', url)
    if folder_match:
        return {"type": "folder", "id": folder_match.group(1)}
    
    # Extract file ID
    file_match = re.search(r'drive\.google\.com/(?:file/d/|open\?id=)([a-zA-Z0-9_-]{25,})', url)
    if file_match:
        return {"type": "file", "id": file_match.group(1)}
    
    # Try generic ID matching if it's just the ID
    if re.match(r'^[a-zA-Z0-9_-]{25,}$', url):
        return {"type": "unknown", "id": url}
        
    return None

def run_gdrive_download_sync(task_id: str, link: str, remote_target: str, folder_name: str):
    sync_tasks[task_id]["status"] = "processing"
    
    parsed = parse_gdrive_link(link)
    if not parsed:
        sync_tasks[task_id]["status"] = "failed"
        sync_tasks[task_id]["error"] = "Không thể phân tích liên kết Google Drive. Định dạng không hợp lệ."
        return
        
    gdrive_id = parsed["id"]
    gdrive_type = parsed["type"]
    
    if not remote_target:
        remote_target = "gdrive:"
        
    if not folder_name:
        folder_name = "GDrive_Sync"
        
    if not remote_target.endswith(":"):
        remote_target = f"{remote_target}/"
        
    try:
        if gdrive_type == "folder" or gdrive_type == "unknown":
            sync_tasks[task_id]["status"] = "syncing"
            
            # Fetch config of gdrive_cam1 dynamically to clone it as a temp source
            client_id = ""
            client_secret = ""
            token = ""
            try:
                config_output = subprocess.check_output(
                    ["rclone", "config", "show", "gdrive_cam1"],
                    text=True,
                    timeout=10
                )
                for line in config_output.split("\n"):
                    if "client_id =" in line:
                        client_id = line.split("=")[1].strip()
                    elif "client_secret =" in line:
                        client_secret = line.split("=")[1].strip()
                    elif "token =" in line:
                        token = line.split("=")[1].strip()
            except Exception:
                pass
                
            env = os.environ.copy()
            env["RCLONE_CONFIG_GDRIVE_TEMP_SYNC_SOURCE_TYPE"] = "drive"
            if client_id:
                env["RCLONE_CONFIG_GDRIVE_TEMP_SYNC_SOURCE_CLIENT_ID"] = client_id
            if client_secret:
                env["RCLONE_CONFIG_GDRIVE_TEMP_SYNC_SOURCE_CLIENT_SECRET"] = client_secret
            if token:
                env["RCLONE_CONFIG_GDRIVE_TEMP_SYNC_SOURCE_TOKEN"] = token
            env["RCLONE_CONFIG_GDRIVE_TEMP_SYNC_SOURCE_ROOT_FOLDER_ID"] = gdrive_id
            
            try:
                subprocess.check_call([
                    "rclone", "copy",
                    "gdrive_temp_sync_source:",
                    f"{remote_target}{folder_name}"
                ], env=env, timeout=1800)
                sync_tasks[task_id]["status"] = "completed"
            except subprocess.CalledProcessError as e:
                if e.returncode == 1:
                    sync_tasks[task_id]["status"] = "completed"
                    sync_tasks[task_id]["warning"] = "Đồng bộ hoàn tất (một số file bị chặn tải xuống bởi chủ sở hữu)."
                else:
                    raise e
            
        else: # file
            sync_tasks[task_id]["status"] = "downloading"
            temp_dir = os.path.expanduser("~/homelab/data/temp_downloads")
            os.makedirs(temp_dir, exist_ok=True)
            
            filename = f"gdrive_file_{gdrive_id}"
            base_url = "https://drive.google.com/uc?export=download"
            url = f"{base_url}&id={gdrive_id}"
            
            req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            try:
                with urllib.request.urlopen(req) as response:
                    content_disposition = response.headers.get('Content-Disposition', '')
                    name_match = re.search(r'filename="([^"]+)"', content_disposition)
                    if name_match:
                        filename = name_match.group(1)
                    
                    html_content = response.read()
                    html_str = html_content.decode('utf-8', errors='ignore')
                    confirm_match = re.search(r'confirm=([a-zA-Z0-9_]+)', html_str)
                    
                    if confirm_match:
                        confirm_token = confirm_match.group(1)
                        url = f"{base_url}&id={gdrive_id}&confirm={confirm_token}"
                        req = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
                        with urllib.request.urlopen(req) as final_resp:
                            content_disposition = final_resp.headers.get('Content-Disposition', '')
                            name_match = re.search(r'filename="([^"]+)"', content_disposition)
                            if name_match:
                                filename = name_match.group(1)
                            file_content = final_resp.read()
                    else:
                        file_content = html_content
            except Exception as e:
                sync_tasks[task_id]["status"] = "failed"
                sync_tasks[task_id]["error"] = f"Lỗi tải file từ GDrive: {str(e)}"
                return

            local_path = os.path.join(temp_dir, filename)
            with open(local_path, "wb") as f:
                f.write(file_content)
                
            sync_tasks[task_id]["status"] = "uploading"
            dest_target = f"{remote_target}{folder_name}/{filename}"
            subprocess.check_call([
                "rclone", "copyto",
                local_path,
                dest_target
            ], timeout=600)
            
            if os.path.exists(local_path):
                os.remove(local_path)
                
            sync_tasks[task_id]["status"] = "completed"
            
    except Exception as e:
        sync_tasks[task_id]["status"] = "failed"
        sync_tasks[task_id]["error"] = str(e)

def process_camera_replay(task_id: str, date: str, start: str, end: str):
    try:
        parts = date.split("-")
        year, month, day = int(parts[0]), int(parts[1]), int(parts[2])
    except Exception:
        replay_tasks[task_id]["status"] = "failed"
        replay_tasks[task_id]["error"] = "Định dạng ngày không hợp lệ. Vui lòng sử dụng YYYY-MM-DD."
        return

    # Query recordings for that day
    conn = get_camera_db()
    c = conn.cursor()
    c.execute(
        "SELECT filename, remote_path, start_time, end_time FROM recordings WHERE year = ? AND month = ? AND day = ? ORDER BY start_time ASC",
        (year, month, day)
    )
    rows = c.fetchall()
    conn.close()

    # Filter segments overlapping with [start, end]
    # Simple string comparison works because time format is HH:MM (24-hour)
    overlapping = []
    for r in rows:
        if r['start_time'] < end and r['end_time'] > start:
            overlapping.append(r)

    if not overlapping:
        replay_tasks[task_id]["status"] = "failed"
        replay_tasks[task_id]["error"] = "Không tìm thấy phân đoạn video nào trong khoảng thời gian này."
        return

    replay_tasks[task_id]["status"] = "downloading"
    temp_dir = os.path.expanduser(f"~/homelab/data/camera/replay_{task_id}")
    os.makedirs(temp_dir, exist_ok=True)
    replay_tasks[task_id]["temp_dir"] = temp_dir

    downloaded_files = []
    rclone_remote = "gdrive" # Remote used for camera backups

    # Hàm hỗ trợ tải/copy song song một phân đoạn
    def download_segment(record):
        filename = record['filename']
        remote_path = record['remote_path']
        # Handle legacy database entries that don't have camera-backups/ prefix
        full_remote_path = remote_path
        if not full_remote_path.startswith("camera-backups/"):
            full_remote_path = f"camera-backups/{full_remote_path}"
        remote_file_path = f"{rclone_remote}:{full_remote_path}"
        local_segment_path = os.path.join(temp_dir, filename)
        
        # 1. Kiểm tra bộ nhớ đệm cục bộ trước
        local_cache_path = os.path.expanduser(f"~/homelab/data/camera/temp/{filename}")
        if os.path.exists(local_cache_path) and os.path.getsize(local_cache_path) > 0:
            try:
                shutil.copy2(local_cache_path, local_segment_path)
                return local_segment_path
            except Exception:
                pass # Nếu copy lỗi thì chuyển qua tải từ Google Drive
                
        # 2. Tải song song từ Google Drive qua Rclone (áp dụng tối ưu hóa luồng tải)
        try:
            subprocess.check_call(
                [
                    "rclone", "copyto", remote_file_path, local_segment_path,
                    "--drive-chunk-size", "64M",
                    "--buffer-size", "32M",
                    "--low-level-retries", "3"
                ],
                timeout=60
            )
            return local_segment_path
        except Exception as e:
            raise Exception(f"Lỗi tải video {filename} từ GDrive: {str(e)}")

    # Thực hiện tải song song tối đa 4 tệp cùng lúc
    from concurrent.futures import ThreadPoolExecutor
    try:
        with ThreadPoolExecutor(max_workers=4) as executor:
            futures = [executor.submit(download_segment, r) for r in overlapping]
            for future in futures:
                res_path = future.result()  # Trả về đường dẫn tệp hoặc ném ngoại lệ nếu tải lỗi
                downloaded_files.append(res_path)
    except Exception as e:
        replay_tasks[task_id]["status"] = "failed"
        replay_tasks[task_id]["error"] = str(e)
        shutil.rmtree(temp_dir, ignore_errors=True)
        return



    replay_tasks[task_id]["status"] = "concatenating"
    output_path = os.path.join(temp_dir, f"replay_{date}_{start.replace(':', '-')}_to_{end.replace(':', '-')}.mp4")
    replay_tasks[task_id]["file_path"] = output_path
    
    # If only one segment downloaded, just copy it to output
    if len(downloaded_files) == 1:
        shutil.copy2(downloaded_files[0], output_path)
    else:
        # Create ffmpeg concat file
        list_file_path = os.path.join(temp_dir, "list.txt")
        with open(list_file_path, "w", encoding="utf-8") as f:
            for file_path in downloaded_files:
                f.write(f"file '{file_path}'\n")

        try:
            # -c copy does instant concatenation without re-encoding, utilizing ~0% CPU
            subprocess.check_call(
                ["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", list_file_path, "-c", "copy", output_path],
                timeout=120
            )
        except Exception as e:
            replay_tasks[task_id]["status"] = "failed"
            replay_tasks[task_id]["error"] = f"Lỗi ghép nối video: {str(e)}"
            shutil.rmtree(temp_dir, ignore_errors=True)
            return

    replay_tasks[task_id]["status"] = "ready"

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
            --error-red: #ef4444;
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

        /* Upload & Select Styles */
        .select-wrapper {
            display: flex;
            gap: 0.8rem;
            margin-bottom: 1rem;
            align-items: center;
        }

        .remote-label {
            font-size: 0.9rem;
            color: var(--text-muted);
        }

        .custom-select {
            background: rgba(10, 14, 23, 0.8);
            color: var(--text-main);
            border: 1px solid var(--border-color);
            border-radius: 8px;
            padding: 0.5rem 1rem;
            outline: none;
            font-size: 0.9rem;
            cursor: pointer;
            transition: border-color 0.2s;
        }

        .custom-select:focus {
            border-color: var(--accent-cyan);
        }

        .drop-zone {
            border: 2px dashed rgba(255, 255, 255, 0.15);
            border-radius: 12px;
            padding: 2.5rem 1.5rem;
            text-align: center;
            cursor: pointer;
            transition: border-color 0.2s, background-color 0.2s;
        }

        .drop-zone:hover, .drop-zone.dragover {
            border-color: var(--accent-cyan);
            background-color: rgba(0, 242, 254, 0.03);
        }

        .drop-zone-text {
            color: var(--text-muted);
            font-size: 0.95rem;
        }

        .drop-zone-highlight {
            color: var(--accent-cyan);
            text-decoration: underline;
        }

        .upload-list {
            margin-top: 1rem;
            display: flex;
            flex-direction: column;
            gap: 0.6rem;
            max-height: 250px;
            overflow-y: auto;
            padding-right: 0.3rem;
        }

        .upload-list::-webkit-scrollbar {
            width: 4px;
        }

        .upload-list::-webkit-scrollbar-thumb {
            background: rgba(255, 255, 255, 0.1);
            border-radius: 2px;
        }

        .upload-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            background: rgba(255, 255, 255, 0.02);
            padding: 0.6rem 1rem;
            border-radius: 8px;
            border: 1px solid rgba(255, 255, 255, 0.04);
            font-size: 0.9rem;
        }

        .upload-name {
            font-weight: 500;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            max-width: 60%;
        }

        .status-badge-inline {
            font-size: 0.75rem;
            padding: 0.25rem 0.6rem;
            border-radius: 12px;
            font-weight: 600;
        }

        .badge-local {
            background: rgba(79, 172, 254, 0.15);
            color: var(--accent-purple);
        }

        .badge-syncing {
            background: rgba(245, 158, 11, 0.15);
            color: #fbbf24;
            animation: pulse-sync 1.5s infinite;
        }

        .badge-completed {
            background: rgba(16, 185, 129, 0.15);
            color: var(--success-green);
        }

        .badge-failed {
            background: rgba(239, 68, 68, 0.15);
            color: var(--error-red);
        }

        @keyframes pulse-sync {
            0% { opacity: 0.6; }
            50% { opacity: 1; }
            100% { opacity: 0.6; }
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
            <div class="card">
                <div class="card-title">⏱️ Uptime & OS</div>
                <div class="metric-value" id="uptime">Loading...</div>
                <div class="sub-value" id="platform">Loading...</div>
            </div>

            <div class="card">
                <div class="card-title">💾 RAM Usage</div>
                <div class="metric-value" id="ram-usage">Loading...</div>
                <div class="progress-bar-container">
                    <div class="progress-bar" id="ram-progress"></div>
                </div>
                <div class="sub-value" id="ram-detail">Loading...</div>
            </div>

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
            <div class="card">
                <div class="card-title">📊 CPU Info</div>
                <div class="metric-value" id="cpu-load">Loading...</div>
                <div class="sub-value" id="cpu-arch">Loading...</div>
            </div>

            <div class="card" style="grid-column: span 2;">
                <div class="card-title">🌡️ Temperatures</div>
                <div class="temp-grid" id="temp-grid">
                    <div class="sub-value">Loading sensors...</div>
                </div>
            </div>
        </div>

        <!-- Camera Replay Card -->
        <div class="grid" style="grid-template-columns: 1fr;">
            <div class="card">
                <div class="card-title">📹 Camera Replay (NVR Player)</div>
                
                <div style="display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 1rem; margin-bottom: 1.5rem;">
                    <div>
                        <span class="remote-label">Chọn ngày:</span>
                        <select id="replay-date" class="custom-select" style="width: 100%; margin-top: 0.4rem;">
                            <option value="">Đang tải danh sách ngày...</option>
                        </select>
                    </div>
                    <div>
                        <span class="remote-label">Giờ bắt đầu:</span>
                        <input type="time" id="replay-start" class="custom-select" style="width: 100%; margin-top: 0.4rem;" value="07:00">
                    </div>
                    <div>
                        <span class="remote-label">Giờ kết thúc:</span>
                        <input type="time" id="replay-end" class="custom-select" style="width: 100%; margin-top: 0.4rem;" value="09:00">
                    </div>
                    <div style="display: flex; align-items: flex-end;">
                        <button id="btn-replay" style="width: 100%; height: 38px; background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple)); border: none; border-radius: 8px; color: var(--bg-dark); font-weight: 700; cursor: pointer; transition: opacity 0.2s;">Ghép & Phát Video</button>
                    </div>
                </div>

                <div id="replay-status-box" style="display: none; margin-bottom: 1rem; padding: 0.8rem; background: rgba(255,255,255,0.02); border: 1px solid var(--border-color); border-radius: 8px; font-size: 0.9rem;">
                    <span id="replay-status-text" style="color: var(--accent-cyan);">Đang khởi tạo...</span>
                </div>

                <div style="width: 100%; display: flex; justify-content: center; background: rgba(0,0,0,0.3); border-radius: 12px; overflow: hidden; border: 1px solid var(--border-color);">
                    <video id="replay-player" controls style="width: 100%; max-height: 480px; display: none;">
                        Trình duyệt không hỗ trợ phát MP4.
                    </video>
                    <div id="player-placeholder" style="padding: 5rem 1rem; text-align: center; color: var(--text-muted); font-size: 0.95rem;">
                        Chưa có video nào được tải. Điền mốc thời gian và nhấn nút để bắt đầu.
                    </div>
                </div>
            </div>
        </div>

        <!-- Web File Upload & Cloud Sync Card -->
        <div class="grid" style="grid-template-columns: 1fr;">
            <div class="card">
                <div class="card-title">☁️ Web Upload & Cloud Auto-Sync</div>
                
                <div class="select-wrapper">
                    <span class="remote-label">Chọn tài khoản Google Drive:</span>
                    <select id="remote-select" class="custom-select">
                        <option value="">Đang tải...</option>
                    </select>
                </div>

                <div id="drop-zone" class="drop-zone">
                    <p class="drop-zone-text">Kéo thả file vào đây hoặc nhấn để <span class="drop-zone-highlight">chọn file</span></p>
                    <input type="file" id="file-input" style="display: none;" multiple>
                </div>

                <div id="upload-list" class="upload-list">
                </div>
            </div>
        </div>

        <!-- Google Drive Shared Link Sync Card -->
        <div class="grid" style="grid-template-columns: 1fr;">
            <div class="card">
                <div class="card-title">📥 GDrive Shared Link Downloader & Sync</div>
                
                <div style="display: grid; grid-template-columns: 1fr; gap: 1rem; margin-bottom: 1rem;">
                    <div>
                        <span class="remote-label">Liên kết Google Drive chia sẻ (Thư mục hoặc Tệp):</span>
                        <input type="text" id="gdrive-link" class="custom-select" style="width: 100%; margin-top: 0.4rem;" placeholder="https://drive.google.com/drive/folders/...">
                    </div>
                </div>

                <div style="display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 1rem; margin-bottom: 1.5rem;">
                    <div>
                        <span class="remote-label">Thư mục đích:</span>
                        <input type="text" id="gdrive-target-folder" class="custom-select" style="width: 100%; margin-top: 0.4rem;" value="GDrive_Sync" placeholder="Ví dụ: tailieu_hoc_tap">
                    </div>
                    <div>
                        <span class="remote-label">Chọn ổ Google Drive đích:</span>
                        <select id="gdrive-target-remote" class="custom-select" style="width: 100%; margin-top: 0.4rem;">
                            <option value="gdrive:">gdrive:</option>
                        </select>
                    </div>
                    <div style="display: flex; align-items: flex-end;">
                        <button id="btn-sync-link" style="width: 100%; height: 38px; background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple)); border: none; border-radius: 8px; color: var(--bg-dark); font-weight: 700; cursor: pointer; transition: opacity 0.2s;">Tải & Đồng bộ</button>
                    </div>
                </div>

                <div id="sync-link-status" class="upload-list">
                </div>
            </div>
        </div>

        <!-- Synced Files Browser Card -->
        <div class="grid" style="grid-template-columns: 1fr;">
            <div class="card">
                <div class="card-title" style="display: flex; justify-content: space-between; align-items: center; width: 100%;">
                    <span>📂 Synced Files Browser (gdrive:GDrive_Sync)</span>
                    <button id="btn-refresh-files" style="background: rgba(255,255,255,0.05); border: 1px solid var(--border-color); color: var(--text-main); font-size: 0.8rem; padding: 0.25rem 0.6rem; border-radius: 6px; cursor: pointer; transition: background 0.2s;">🔄 Làm mới</button>
                </div>
                
                <div id="files-breadcrumb" style="display: flex; gap: 0.4rem; align-items: center; background: rgba(255,255,255,0.02); border: 1px solid var(--border-color); padding: 0.5rem 1rem; border-radius: 8px; font-size: 0.85rem; margin-top: 0.5rem; flex-wrap: wrap;">
                    <span class="breadcrumb-item" data-path="" style="cursor: pointer; color: var(--accent-cyan); font-weight: 600;">🏠 Root</span>
                </div>
                
                <div id="files-list" style="margin-top: 1rem; display: flex; flex-direction: column; gap: 0.6rem; max-height: 450px; overflow-y: auto; padding-right: 0.3rem;">
                    <div class="sub-value" style="text-align: center; padding: 2rem 0;">Đang tải danh sách tệp...</div>
                </div>
            </div>
        </div>

        <!-- Preview Modal -->
        <div id="preview-modal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; background: rgba(0,0,0,0.85); z-index: 9999; justify-content: center; align-items: center; backdrop-filter: blur(8px);">
            <div style="position: relative; width: 90%; max-width: 750px; background: rgba(18, 26, 47, 0.95); border: 1px solid var(--border-color); border-radius: 16px; padding: 1.5rem; display: flex; flex-direction: column; align-items: center; gap: 1rem; box-shadow: 0 10px 30px rgba(0,0,0,0.5);">
                <button id="btn-close-preview" style="position: absolute; top: 10px; right: 10px; background: rgba(255,255,255,0.1); border: none; border-radius: 50%; width: 32px; height: 32px; color: var(--text-main); font-weight: 700; cursor: pointer; transition: background 0.2s; display: flex; align-items: center; justify-content: center;">✕</button>
                <div id="preview-title" style="font-weight: 600; font-size: 1.1rem; color: var(--text-main); text-align: center; max-width: 80%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;">Xem tệp</div>
                <div id="preview-content-box" style="display: flex; justify-content: center; align-items: center; min-width: 250px; min-height: 150px; max-height: 60vh; width: 100%; overflow: auto;">
                    <!-- Content will be injected dynamically -->
                </div>
                <div style="display: flex; gap: 1rem; width: 100%; justify-content: center; margin-top: 0.5rem;">
                    <a id="btn-download-preview" href="#" download style="background: linear-gradient(135deg, var(--accent-cyan), var(--accent-purple)); color: var(--bg-dark); font-weight: 700; padding: 0.5rem 1.5rem; border-radius: 8px; font-size: 0.9rem; text-decoration: none; display: flex; align-items: center; gap: 0.3rem; transition: opacity 0.2s;">⬇️ Tải xuống</a>
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
                    document.getElementById('disk-detail').innerText = `Used: ${data.disk.used} / ${data.disk.total} (Available: ${data.disk.available})`;
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

        // --- Logic Rclone Upload ---
        async function loadRemotes() {
            try {
                const res = await fetch('/remotes');
                const data = await res.json();
                const select = document.getElementById('remote-select');
                select.innerHTML = '';
                
                if (data.remotes && data.remotes.length > 0) {
                    data.remotes.forEach(remote => {
                        const opt = document.createElement('option');
                        opt.value = remote;
                        opt.innerText = remote;
                        select.appendChild(opt);
                    });
                } else {
                    const opt = document.createElement('option');
                    opt.value = '';
                    opt.innerText = 'Chưa cấu hình Google Drive';
                    select.appendChild(opt);
                }
            } catch (err) {
                console.error('Failed to load remotes', err);
            }
        }

        const dropZone = document.getElementById('drop-zone');
        const fileInput = document.getElementById('file-input');
        const uploadList = document.getElementById('upload-list');

        dropZone.onclick = () => fileInput.click();

        dropZone.ondragover = (e) => {
            e.preventDefault();
            dropZone.classList.add('dragover');
        };

        dropZone.ondragleave = () => {
            dropZone.classList.remove('dragover');
        };

        dropZone.ondrop = (e) => {
            e.preventDefault();
            dropZone.classList.remove('dragover');
            if (e.dataTransfer.files.length > 0) {
                handleFiles(e.dataTransfer.files);
            }
        };

        fileInput.onchange = () => {
            if (fileInput.files.length > 0) {
                handleFiles(fileInput.files);
            }
        };

        function handleFiles(files) {
            const remote = document.getElementById('remote-select').value;
            Array.from(files).forEach(file => {
                uploadFile(file, remote);
            });
        }

        async function uploadFile(file, remote) {
            const itemId = 'upload-' + Math.random().toString(36).substring(2, 9);
            const item = document.createElement('div');
            item.className = 'upload-item';
            item.id = itemId;
            item.innerHTML = `
                <div class="upload-name">${file.name}</div>
                <div style="display: flex; gap: 0.5rem; align-items: center;">
                    <span class="upload-progress" style="font-size: 0.8rem; color: var(--text-muted);">0%</span>
                    <span class="status-badge-inline badge-local">Đang lưu local...</span>
                </div>
            `;
            uploadList.insertBefore(item, uploadList.firstChild);

            const formData = new FormData();
            formData.append('file', file);
            if (remote) {
                formData.append('remote', remote);
            }

            try {
                const xhr = new XMLHttpRequest();
                
                xhr.upload.onprogress = (event) => {
                    if (event.lengthComputable) {
                        const percent = Math.round((event.loaded / event.total) * 100);
                        item.querySelector('.upload-progress').innerText = percent + '%';
                    }
                };

                xhr.onload = () => {
                    if (xhr.status === 200) {
                        const resData = JSON.parse(xhr.responseText);
                        const taskId = resData.task_id;
                        
                        const badge = item.querySelector('.status-badge-inline');
                        badge.className = 'status-badge-inline badge-syncing';
                        badge.innerText = 'Đang đồng bộ Cloud...';
                        
                        pollSyncStatus(taskId, item);
                    } else {
                        showUploadError(item, 'Lưu local lỗi');
                    }
                };

                xhr.onerror = () => {
                    showUploadError(item, 'Lỗi kết nối');
                };

                xhr.open('POST', '/upload', true);
                xhr.send(formData);

            } catch (err) {
                showUploadError(item, err.message);
            }
        }

        function showUploadError(item, msg) {
            const badge = item.querySelector('.status-badge-inline');
            badge.className = 'status-badge-inline badge-failed';
            badge.innerText = msg;
            item.querySelector('.upload-progress').style.display = 'none';
        }

        async function pollSyncStatus(taskId, item) {
            const interval = setInterval(async () => {
                try {
                    const res = await fetch(`/sync-status/${taskId}`);
                    const data = await res.json();
                    
                    if (data.status === 'completed') {
                        clearInterval(interval);
                        const badge = item.querySelector('.status-badge-inline');
                        badge.className = 'status-badge-inline badge-completed';
                        badge.innerText = 'Đã lên GDrive';
                        item.querySelector('.upload-progress').innerText = '100%';
                    } else if (data.status === 'failed') {
                        clearInterval(interval);
                        showUploadError(item, 'Sync GDrive lỗi');
                    }
                } catch (err) {
                    console.error('Error polling status:', err);
                }
            }, 1500);
        }

        // --- Logic Camera Replay (NVR Player) ---
        async function loadRecordingDates() {
            try {
                const res = await fetch('/camera/dates');
                const data = await res.json();
                const select = document.getElementById('replay-date');
                select.innerHTML = '';
                
                if (data.dates && data.dates.length > 0) {
                    data.dates.forEach(date => {
                        const opt = document.createElement('option');
                        opt.value = date;
                        opt.innerText = date;
                        select.appendChild(opt);
                    });
                } else {
                    const opt = document.createElement('option');
                    opt.value = '';
                    opt.innerText = 'Không có dữ liệu camera';
                    select.appendChild(opt);
                }
            } catch (err) {
                console.error('Failed to load camera dates', err);
            }
        }

        const btnReplay = document.getElementById('btn-replay');
        const replayDate = document.getElementById('replay-date');
        const replayStart = document.getElementById('replay-start');
        const replayEnd = document.getElementById('replay-end');
        const replayPlayer = document.getElementById('replay-player');
        const playerPlaceholder = document.getElementById('player-placeholder');
        const statusBox = document.getElementById('replay-status-box');
        const statusText = document.getElementById('replay-status-text');

        btnReplay.onclick = async () => {
            const date = replayDate.value;
            const start = replayStart.value;
            const end = replayEnd.value;

            if (!date) {
                alert('Vui lòng chọn ngày xem lại!');
                return;
            }

            replayPlayer.style.display = 'none';
            replayPlayer.src = '';
            playerPlaceholder.style.display = 'block';
            playerPlaceholder.innerText = 'Đang chuẩn bị ghép nối video...';
            statusBox.style.display = 'block';
            statusText.style.color = 'var(--accent-cyan)';
            statusText.innerText = 'Đang khởi tạo yêu cầu ghép nối...';
            btnReplay.disabled = true;
            btnReplay.style.opacity = 0.5;

            try {
                const formData = new FormData();
                formData.append('date', date);
                formData.append('start', start);
                formData.append('end', end);

                const initRes = await fetch('/camera/replay/init', {
                    method: 'POST',
                    body: formData
                });
                const initData = await initRes.json();
                
                if (initRes.status !== 200) {
                    throw new Error(initData.detail || 'Không thể tạo phiên xem lại');
                }

                const taskId = initData.task_id;
                pollReplayStatus(taskId);

            } catch (err) {
                statusText.style.color = 'var(--error-red)';
                statusText.innerText = 'Lỗi: ' + err.message;
                playerPlaceholder.innerText = 'Ghép video thất bại.';
                btnReplay.disabled = false;
                btnReplay.style.opacity = 1;
            }
        };

        function pollReplayStatus(taskId) {
            const interval = setInterval(async () => {
                try {
                    const res = await fetch(`/camera/replay/status/${taskId}`);
                    const data = await res.json();
                    
                    if (data.status === 'downloading') {
                        statusText.style.color = 'var(--accent-cyan)';
                        statusText.innerText = '⬇️ Bước 1/2: Đang tải các phân đoạn từ Google Drive...';
                    } else if (data.status === 'concatenating') {
                        statusText.style.color = 'var(--accent-cyan)';
                        statusText.innerText = '🔄 Bước 2/2: Đang ghép nối các phân đoạn bằng FFMPEG...';
                    } else if (data.status === 'ready') {
                        clearInterval(interval);
                        statusText.style.color = 'var(--success-green)';
                        statusText.innerText = '✅ Hoàn tất! Bắt đầu phát video.';
                        
                        playerPlaceholder.style.display = 'none';
                        replayPlayer.style.display = 'block';
                        replayPlayer.src = `/camera/replay/play/${taskId}`;
                        replayPlayer.load();
                        replayPlayer.play();
                        
                        btnReplay.disabled = false;
                        btnReplay.style.opacity = 1;
                    } else if (data.status === 'failed') {
                        clearInterval(interval);
                        statusText.style.color = 'var(--error-red)';
                        statusText.innerText = '❌ Ghép nối lỗi: ' + (data.error || 'Không xác định');
                        playerPlaceholder.innerText = 'Xem lại thất bại.';
                        btnReplay.disabled = false;
                        btnReplay.style.opacity = 1;
                    }
                } catch (err) {
                    console.error('Error polling replay status', err);
                }
            }, 1500);
        }

        // --- Logic GDrive Shared Link Sync ---
        const btnSyncLink = document.getElementById('btn-sync-link');
        const gdriveLinkInput = document.getElementById('gdrive-link');
        const gdriveTargetFolder = document.getElementById('gdrive-target-folder');
        const gdriveTargetRemote = document.getElementById('gdrive-target-remote');
        const syncLinkStatusDiv = document.getElementById('sync-link-status');

        // Populate remotes dropdown for link sync once remotes are loaded
        const originalLoadRemotes = loadRemotes;
        loadRemotes = async function() {
            await originalLoadRemotes();
            const selectSource = document.getElementById('remote-select');
            const targetSelect = document.getElementById('gdrive-target-remote');
            targetSelect.innerHTML = selectSource.innerHTML;
        };

        btnSyncLink.onclick = async () => {
            const link = gdriveLinkInput.value.trim();
            const folder = gdriveTargetFolder.value.trim();
            const remote = gdriveTargetRemote.value;

            if (!link) {
                alert('Vui lòng nhập liên kết Google Drive!');
                return;
            }

            btnSyncLink.disabled = true;
            btnSyncLink.style.opacity = 0.5;

            const itemId = 'link-sync-' + Math.random().toString(36).substring(2, 9);
            const item = document.createElement('div');
            item.className = 'upload-item';
            item.id = itemId;
            item.innerHTML = `
                <div class="upload-name" style="max-width: 50%;" title="${link}">GDrive: ${link.substring(0, 30)}...</div>
                <div style="display: flex; gap: 0.5rem; align-items: center;">
                    <span class="upload-progress" style="font-size: 0.8rem; color: var(--text-muted);">Khởi tạo...</span>
                    <span class="status-badge-inline badge-syncing">Đang gửi yêu cầu...</span>
                </div>
            `;
            syncLinkStatusDiv.insertBefore(item, syncLinkStatusDiv.firstChild);

            try {
                const formData = new FormData();
                formData.append('link', link);
                formData.append('remote', remote);
                formData.append('folder_name', folder);

                const res = await fetch('/drive/download', {
                    method: 'POST',
                    body: formData
                });
                const data = await res.json();

                if (res.status === 200) {
                    const taskId = data.task_id;
                    pollLinkSyncStatus(taskId, item);
                } else {
                    showLinkSyncError(item, data.detail || 'Lỗi gửi yêu cầu');
                    btnSyncLink.disabled = false;
                    btnSyncLink.style.opacity = 1;
                }
            } catch (err) {
                showLinkSyncError(item, err.message);
                btnSyncLink.disabled = false;
                btnSyncLink.style.opacity = 1;
            }
        };

        function showLinkSyncError(item, msg) {
            const badge = item.querySelector('.status-badge-inline');
            badge.className = 'status-badge-inline badge-failed';
            badge.innerText = msg;
            item.querySelector('.upload-progress').style.display = 'none';
        }

        async function pollLinkSyncStatus(taskId, item) {
            const interval = setInterval(async () => {
                try {
                    const res = await fetch(`/sync-status/${taskId}`);
                    const data = await res.json();
                    
                    const progressSpan = item.querySelector('.upload-progress');
                    const badge = item.querySelector('.status-badge-inline');

                    if (data.status === 'processing') {
                        badge.innerText = 'Đang phân tích...';
                    } else if (data.status === 'downloading') {
                        badge.innerText = 'Đang tải file...';
                        progressSpan.innerText = '⬇️';
                    } else if (data.status === 'uploading') {
                        badge.innerText = 'Đang đẩy lên Drive...';
                        progressSpan.innerText = '⬆️';
                    } else if (data.status === 'syncing') {
                        badge.innerText = 'Rclone đang đồng bộ...';
                        progressSpan.innerText = '🔄';
                    } else if (data.status === 'completed') {
                        clearInterval(interval);
                        badge.className = 'status-badge-inline badge-completed';
                        badge.innerText = 'Đồng bộ hoàn tất';
                        progressSpan.innerText = '100%';
                        btnSyncLink.disabled = false;
                        btnSyncLink.style.opacity = 1;
                        loadSyncedFiles();
                    } else if (data.status === 'failed') {
                        clearInterval(interval);
                        showLinkSyncError(item, data.error ? data.error.substring(0, 30) : 'Lỗi đồng bộ');
                        btnSyncLink.disabled = false;
                        btnSyncLink.style.opacity = 1;
                    }
                } catch (err) {
                    console.error('Error polling sync status:', err);
                }
            }, 2000);
        }

        // --- Logic Synced Files Browser ---
        const filesListDiv = document.getElementById('files-list');
        const btnRefreshFiles = document.getElementById('btn-refresh-files');
        
        let currentPath = "";
        
        function renderBreadcrumbs() {
            const breadcrumbDiv = document.getElementById('files-breadcrumb');
            breadcrumbDiv.innerHTML = '';
            
            const rootSpan = document.createElement('span');
            rootSpan.className = 'breadcrumb-item';
            rootSpan.style.cursor = 'pointer';
            rootSpan.style.color = 'var(--accent-cyan)';
            rootSpan.style.fontWeight = '600';
            rootSpan.innerText = '🏠 Root';
            rootSpan.onclick = () => navigateTo("");
            breadcrumbDiv.appendChild(rootSpan);
            
            if (currentPath) {
                const parts = currentPath.split('/');
                let accumPath = "";
                parts.forEach((part, index) => {
                    accumPath = accumPath ? `${accumPath}/${part}` : part;
                    
                    const separator = document.createElement('span');
                    separator.style.color = 'var(--text-muted)';
                    separator.innerText = ' ❯ ';
                    breadcrumbDiv.appendChild(separator);
                    
                    const partSpan = document.createElement('span');
                    partSpan.className = 'breadcrumb-item';
                    partSpan.style.cursor = 'pointer';
                    partSpan.style.color = index === parts.length - 1 ? 'var(--text-main)' : 'var(--accent-cyan)';
                    partSpan.style.fontWeight = index === parts.length - 1 ? '700' : '500';
                    partSpan.innerText = part;
                    const thisPath = accumPath;
                    partSpan.onclick = () => navigateTo(thisPath);
                    breadcrumbDiv.appendChild(partSpan);
                });
            }
        }
        
        function navigateTo(path) {
            currentPath = path;
            renderBreadcrumbs();
            loadSyncedFiles();
        }
        
        const previewModal = document.getElementById('preview-modal');
        const previewTitle = document.getElementById('preview-title');
        const previewContentBox = document.getElementById('preview-content-box');
        const btnClosePreview = document.getElementById('btn-close-preview');
        const btnDownloadPreview = document.getElementById('btn-download-preview');
        
        btnClosePreview.onclick = () => {
            previewModal.style.display = 'none';
            previewContentBox.innerHTML = '';
        };
        
        window.onclick = (event) => {
            if (event.target === previewModal) {
                previewModal.style.display = 'none';
                previewContentBox.innerHTML = '';
            }
        };
        
        function showPreview(filePath, fileName) {
            const targetFolder = document.getElementById('gdrive-target-folder').value.trim() || 'GDrive_Sync';
            const targetRemote = document.getElementById('gdrive-target-remote').value || 'gdrive:';
            
            const fileUrl = `/drive/file/view?remote=${encodeURIComponent(targetRemote)}&folder=${encodeURIComponent(targetFolder)}&path=${encodeURIComponent(filePath)}`;
            const downloadUrl = `${fileUrl}&download=true`;
            
            previewTitle.innerText = fileName;
            btnDownloadPreview.href = downloadUrl;
            previewContentBox.innerHTML = '';
            
            const ext = fileName.split('.').pop().toLowerCase();
            if (['jpg', 'jpeg', 'png', 'gif', 'webp'].includes(ext)) {
                previewContentBox.innerHTML = `<img src="${fileUrl}" style="max-width: 100%; max-height: 55vh; object-fit: contain; border-radius: 8px;" />`;
            } else if (['mp4', 'webm', 'ogg'].includes(ext)) {
                previewContentBox.innerHTML = `<video src="${fileUrl}" controls style="max-width: 100%; max-height: 55vh; width: 100%;" />`;
            } else if (['pdf'].includes(ext)) {
                previewContentBox.innerHTML = `<iframe src="${fileUrl}" style="width: 100%; height: 50vh; border: none; border-radius: 8px;" />`;
            } else {
                previewContentBox.innerHTML = `
                    <div style="text-align: center; padding: 2rem 0; color: var(--text-muted); width: 100%;">
                        <div style="font-size: 3rem; margin-bottom: 1rem;">📄</div>
                        <div>Tệp tin này không hỗ trợ xem trực tiếp. Bấm nút bên dưới để tải xuống.</div>
                    </div>
                `;
            }
            
            previewModal.style.display = 'flex';
        }

        async function loadSyncedFiles() {
            filesListDiv.innerHTML = '<div class="sub-value" style="text-align: center; padding: 2rem 0; color: var(--accent-cyan);">🔄 Đang quét danh sách tệp trên Google Drive...</div>';
            try {
                const targetFolder = document.getElementById('gdrive-target-folder').value.trim() || 'GDrive_Sync';
                const targetRemote = document.getElementById('gdrive-target-remote').value || 'gdrive:';
                
                const url = `/drive/files?remote=${encodeURIComponent(targetRemote)}&folder=${encodeURIComponent(targetFolder)}&path=${encodeURIComponent(currentPath)}`;
                const res = await fetch(url);
                const data = await res.json();
                
                if (data.status === 'success') {
                    filesListDiv.innerHTML = '';
                    
                    if (currentPath) {
                        const backItem = document.createElement('div');
                        backItem.className = 'upload-item';
                        backItem.style.cursor = 'pointer';
                        backItem.style.background = 'rgba(255, 255, 255, 0.04)';
                        backItem.innerHTML = `
                            <div class="upload-name" style="font-weight: 600; color: var(--accent-cyan);">
                                📁 .. (Thư mục cha)
                            </div>
                        `;
                        backItem.onclick = () => {
                            const parts = currentPath.split('/');
                            parts.pop();
                            navigateTo(parts.join('/'));
                        };
                        filesListDiv.appendChild(backItem);
                    }
                    
                    if (data.files && data.files.length > 0) {
                        data.files.forEach(file => {
                            const item = document.createElement('div');
                            item.className = 'upload-item';
                            item.style.cursor = 'pointer';
                            
                            const icon = file.IsDir ? '📁' : '📄';
                            const sizeText = file.IsDir ? 'Thư mục' : formatBytesSize(file.Size);
                            const relativeFilePath = currentPath ? `${currentPath}/${file.Name}` : file.Name;
                            
                            item.innerHTML = `
                                <div class="upload-name" style="max-width: 65%; font-weight: 500;" title="${file.Name}">
                                    ${icon} ${file.Name}
                                </div>
                                <div style="display: flex; gap: 0.8rem; align-items: center;">
                                    <span class="status-badge-inline badge-local">${sizeText}</span>
                                    ${!file.IsDir ? `<a href="/drive/file/view?remote=${encodeURIComponent(targetRemote)}&folder=${encodeURIComponent(targetFolder)}&path=${encodeURIComponent(relativeFilePath)}&download=true" class="status-badge-inline badge-completed" style="text-decoration: none;" onclick="event.stopPropagation();">📥</a>` : ''}
                                </div>
                            `;
                            
                            item.onclick = () => {
                                if (file.IsDir) {
                                    navigateTo(relativeFilePath);
                                } else {
                                    showPreview(relativeFilePath, file.Name);
                                }
                            };
                            
                            filesListDiv.appendChild(item);
                        });
                    } else {
                        filesListDiv.innerHTML += '<div class="sub-value" style="text-align: center; padding: 2rem 0; color: var(--text-muted);">Thư mục trống hoặc chưa có file nào được đồng bộ.</div>';
                    }
                } else {
                    filesListDiv.innerHTML = `<div class="sub-value" style="text-align: center; padding: 2rem 0; color: var(--error-red);">Lỗi: ${data.message || 'Không thể tải danh sách tệp'}</div>`;
                }
            } catch (err) {
                filesListDiv.innerHTML = `<div class="sub-value" style="text-align: center; padding: 2rem 0; color: var(--error-red);">Lỗi kết nối: ${err.message}</div>`;
            }
        }

        function formatBytesSize(bytes) {
            if (bytes === 0) return '0 B';
            const k = 1024;
            const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
            const i = Math.floor(Math.log(bytes) / Math.log(k));
            return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
        }

        btnRefreshFiles.onclick = loadSyncedFiles;

        // Khởi chạy khi tải trang
        updateStats();
        loadRemotes();
        loadRecordingDates();
        loadSyncedFiles();
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
    try:
        uptime_raw = subprocess.check_output(
            ["uptime", "-p"], text=True, timeout=5
        ).strip()
    except Exception:
        uptime_raw = "unknown"

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

@app.get("/remotes")
def get_remotes():
    return {"remotes": get_rclone_remotes()}

@app.post("/upload")
async def upload_file(
    background_tasks: BackgroundTasks,
    file: UploadFile = File(...),
    remote: str = Form(None)
):
    upload_dir = os.path.expanduser("~/homelab/data/uploads")
    os.makedirs(upload_dir, exist_ok=True)
    
    file_path = os.path.join(upload_dir, file.filename)
    with open(file_path, "wb") as buffer:
        shutil.copyfileobj(file.file, buffer)
        
    task_id = str(uuid.uuid4())
    sync_tasks[task_id] = {
        "status": "pending",
        "error": None,
        "file_name": file.filename
    }
    
    background_tasks.add_task(run_rclone_sync, task_id, file_path, remote)
    
    return {"task_id": task_id, "status": "pending"}

@app.get("/sync-status/{task_id}")
def get_sync_status(task_id: str):
    if task_id not in sync_tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    return sync_tasks[task_id]

@app.post("/drive/download")
async def download_drive_link(
    background_tasks: BackgroundTasks,
    link: str = Form(...),
    remote: str = Form(None),
    folder_name: str = Form(None)
):
    task_id = str(uuid.uuid4())
    sync_tasks[task_id] = {
        "status": "pending",
        "error": None,
        "file_name": link
    }
    
    background_tasks.add_task(run_gdrive_download_sync, task_id, link, remote, folder_name)
    return {"task_id": task_id, "status": "pending"}

# --- Camera Replay Endpoints ---
@app.get("/camera/dates")
def get_recording_dates():
    conn = get_camera_db()
    c = conn.cursor()
    c.execute("SELECT DISTINCT year, month, day FROM recordings ORDER BY year DESC, month DESC, day DESC")
    rows = c.fetchall()
    conn.close()
    return {"dates": [f"{r['year']}-{r['month']:02d}-{r['day']:02d}" for r in rows]}

@app.post("/camera/replay/init")
def init_replay(
    background_tasks: BackgroundTasks,
    date: str = Form(...),   # YYYY-MM-DD
    start: str = Form(...),  # HH:MM
    end: str = Form(...)     # HH:MM
):
    task_id = str(uuid.uuid4())
    replay_tasks[task_id] = {
        "status": "pending",
        "error": None,
        "file_path": None,
        "temp_dir": None
    }
    background_tasks.add_task(process_camera_replay, task_id, date, start, end)
    return {"task_id": task_id}

@app.get("/camera/replay/status/{task_id}")
def get_replay_status(task_id: str):
    if task_id not in replay_tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    return {
        "status": replay_tasks[task_id]["status"],
        "error": replay_tasks[task_id]["error"]
    }

@app.get("/camera/replay/play/{task_id}")
def play_replay(task_id: str, background_tasks: BackgroundTasks):
    if task_id not in replay_tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    task = replay_tasks[task_id]
    if task["status"] != "ready":
        raise HTTPException(status_code=400, detail="Replay is not ready yet")
        
    output_path = task["file_path"]
    temp_dir = task["temp_dir"]
    
    def cleanup_replay_dir():
        try:
            if os.path.exists(temp_dir):
                shutil.rmtree(temp_dir)
            replay_tasks.pop(task_id, None)
        except Exception:
            pass
            
    background_tasks.add_task(cleanup_replay_dir)
    return FileResponse(output_path, media_type="video/mp4", filename=os.path.basename(output_path))

@app.get("/drive/files")
def list_drive_files(remote: str = "gdrive:", folder: str = "GDrive_Sync", path: str = ""):
    try:
        if path:
            path = path.strip("/")
            remote_path = f"{remote}{folder}/{path}"
        else:
            remote_path = f"{remote}{folder}"
            
        output = subprocess.check_output(
            ["rclone", "lsjson", remote_path, "--max-depth", "1"],
            text=True,
            timeout=15
        )
        import json
        files = json.loads(output)
        return {"status": "success", "files": files}
    except subprocess.CalledProcessError as e:
        if e.returncode == 3:
            return {"status": "success", "files": []}
        return {"status": "error", "message": str(e)}
    except Exception as e:
        return {"status": "error", "message": str(e)}

from fastapi.responses import StreamingResponse

@app.get("/drive/file/view")
def view_drive_file(remote: str = "gdrive:", folder: str = "GDrive_Sync", path: str = "", download: str = "false"):
    if not path:
        raise HTTPException(status_code=400, detail="Path is required")
        
    path = path.strip("/")
    remote_path = f"{remote}{folder}/{path}"
    
    try:
        def iterfile():
            proc = subprocess.Popen(
                ["rclone", "cat", remote_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE
            )
            try:
                while True:
                    chunk = proc.stdout.read(64 * 1024)
                    if not chunk:
                        break
                    yield chunk
            finally:
                proc.kill()
                proc.wait()
                
        media_type = "application/octet-stream"
        ext = os.path.splitext(path)[1].lower()
        if ext in (".jpg", ".jpeg"):
            media_type = "image/jpeg"
        elif ext == ".png":
            media_type = "image/png"
        elif ext == ".gif":
            media_type = "image/gif"
        elif ext == ".mp4":
            media_type = "video/mp4"
        elif ext == ".pdf":
            media_type = "application/pdf"
            
        filename = os.path.basename(path)
        if download == "true":
            headers = {
                "Content-Disposition": f"attachment; filename=\"{filename}\""
            }
        else:
            headers = {
                "Content-Disposition": f"inline; filename=\"{filename}\""
            }
            
        return StreamingResponse(iterfile(), media_type=media_type, headers=headers)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
