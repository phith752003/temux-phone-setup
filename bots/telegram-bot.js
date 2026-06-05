// bots/telegram-bot.js
const { exec } = require('child_process');
const https = require('https');
const fs = require('fs');
const path = require('path');

// Configuration paths
const configPath = path.join(__dirname, 'config.json');

// Default config structure
let config = {
    token: "YOUR_TELEGRAM_BOT_TOKEN",
    chat_id: "YOUR_TELEGRAM_CHAT_ID"
};

// Load config
if (fs.existsSync(configPath)) {
    try {
        config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
    } catch (e) {
        console.error("Failed to parse config.json, using defaults.");
    }
} else {
    fs.writeFileSync(configPath, JSON.stringify(config, null, 4), 'utf8');
    console.log("Created config.json. Please edit it with your Telegram bot token and Chat ID.");
}

const { token, chat_id } = config;

if (!token || token === "YOUR_TELEGRAM_BOT_TOKEN") {
    console.error("CRITICAL: Please set your Telegram Bot Token in bots/config.json");
    process.exit(1);
}

// Security guards: regex patterns to block destructive commands
const dangerousPatterns = [
    /rm\s+-[a-zA-Z]*r/i, // rm -r, rm -rf, etc.
    /rm\s+-[a-zA-Z]*f/i, // rm -f
    /rm\s+--recursive/i,
    /rmdir\s+/i,         // rmdir
    /dd\s+if=/i,        // dd command writing
    /mkfs/i,            // formatting partitions
    />\s*\/dev\//i      // writing directly to device nodes
];

let pendingCommand = null;
let pendingTimeout = null;
let offset = 0;

// Helper to make HTTPS requests to Telegram API
function telegramRequest(method, payload = {}) {
    return new Promise((resolve, reject) => {
        const data = JSON.stringify(payload);
        const options = {
            hostname: 'api.telegram.org',
            port: 443,
            path: `/bot${token}/${method}`,
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Content-Length': Buffer.byteLength(data)
            }
        };

        const req = https.request(options, (res) => {
            let body = '';
            res.on('data', chunk => body += chunk);
            res.on('end', () => {
                try {
                    const parsed = JSON.parse(body);
                    if (parsed.ok) {
                        resolve(parsed.result);
                    } else {
                        reject(parsed.description);
                    }
                } catch (e) {
                    reject(e);
                }
            });
        });

        req.on('error', (err) => reject(err));
        req.write(data);
        req.end();
    });
}

// Send message (handles splitting text if it exceeds 4096 character Telegram limit)
async function sendMessage(text) {
    if (!chat_id || chat_id === "YOUR_TELEGRAM_CHAT_ID") {
        console.log("No valid chat_id set. Print to stdout:\n", text);
        return;
    }
    
    const maxLen = 4000;
    if (text.length <= maxLen) {
        await telegramRequest('sendMessage', { chat_id, text: text, parse_mode: 'Markdown' }).catch(() => {
            // Fallback without Markdown in case formatting is broken
            return telegramRequest('sendMessage', { chat_id, text: text });
        });
    } else {
        // Split into chunks
        for (let i = 0; i < text.length; i += maxLen) {
            const chunk = text.substring(i, i + maxLen);
            await telegramRequest('sendMessage', { chat_id, text: chunk }).catch(err => console.error("Send error:", err));
        }
    }
}

// Safety check function
function isDangerous(cmd) {
    return dangerousPatterns.some(pattern => pattern.test(cmd));
}

// Exec helper to run command in shell
function runShellCommand(cmd) {
    return new Promise((resolve) => {
        // Prepend common binary directories to PATH for non-interactive shells
        const customEnv = { ...process.env };
        const extraPaths = '/root/.local/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin';
        customEnv.PATH = customEnv.PATH ? `${extraPaths}:${customEnv.PATH}` : extraPaths;

        exec(cmd, { shell: '/bin/bash', env: customEnv }, (error, stdout, stderr) => {
            let result = '';
            if (stdout) result += stdout;
            if (stderr) result += `[Stderr]\n${stderr}`;
            if (error) result += `[Exit Code: ${error.code}]\n${error.message}`;
            if (!result) result = '[Lệnh thực thi thành công và không trả về output]';
            resolve(result);
        });
    });
}

// Handle incoming user commands
async function handleMessage(msg) {
    const text = msg.text ? msg.text.trim() : '';
    const senderChatId = msg.chat.id.toString();

    // Verification: Only allow the configured Chat ID
    if (senderChatId !== chat_id) {
        console.warn(`Unauthorized access attempt from Chat ID: ${senderChatId}`);
        await telegramRequest('sendMessage', { 
            chat_id: senderChatId, 
            text: `🔒 Từ chối truy cập. Thiết bị này đã được khóa cấu hình.` 
        }).catch(err => console.error(err));
        return;
    }

    if (!text) return;

    // Handle special bot commands
    if (text.toLowerCase() === '/start') {
        await sendMessage(`👋 *Chào mừng bạn đến với Vsmart Homelab Controller!*\n\nBot đang hoạt động 24/7. Bạn có thể gửi bất kỳ lệnh shell nào (ví dụ: \`ls -la\`, \`node -v\`) để thực thi trực tiếp trên điện thoại.\n\n*Hệ thống an toàn (Security Guard)* đã được kích hoạt để chặn các lệnh xóa dữ liệu phá hoại.`);
        return;
    }

    if (text.toLowerCase() === '/help') {
        await sendMessage(`📖 *Hướng dẫn sử dụng Homelab Bot:*\n\n- Gửi lệnh shell bất kỳ để chạy trực tiếp (Ví dụ: \`df -h\`, \`free -m\`).\n- Gửi \`bash ~/homelab/scripts/status.sh\` để xem báo cáo trạng thái đẹp.\n- Lệnh nguy hiểm (như \`rm -rf\`) cần trả lời *YES* trong vòng 60 giây để xác nhận.`);
        return;
    }

    // Handle active confirmation flow
    if (pendingCommand) {
        if (text.toUpperCase() === 'YES') {
            const cmdToRun = pendingCommand;
            clearTimeout(pendingTimeout);
            pendingCommand = null;
            pendingTimeout = null;

            await sendMessage(`▶️ *Đang thực thi lệnh nguy hiểm (Đã xác nhận):*\n\`\`\`bash\n${cmdToRun}\n\`\`\``);
            const output = await runShellCommand(cmdToRun);
            await sendMessage(`*Kết quả:*\n\`\`\`\n${output}\n\`\`\``);
        } else {
            clearTimeout(pendingTimeout);
            const cancelledCmd = pendingCommand;
            pendingCommand = null;
            pendingTimeout = null;
            await sendMessage(`❌ *Đã hủy lệnh nguy hiểm:* \`${cancelledCmd}\``);
        }
        return;
    }

    // Checking if the command is dangerous
    if (isDangerous(text)) {
        pendingCommand = text;
        await sendMessage(`⚠️ *CẢNH BÁO NGUY HIỂM!*\nLệnh của bạn chứa từ khóa xóa file/thư mục hệ thống:\n\`\`\`bash\n${text}\n\`\`\`\nBạn có thực sự muốn tiếp tục? Nhắn *YES* trong vòng 60 giây để xác nhận.`);
        
        pendingTimeout = setTimeout(async () => {
            if (pendingCommand) {
                pendingCommand = null;
                pendingTimeout = null;
                await sendMessage(`⏰ *Hết giờ:* Lệnh nguy hiểm đã tự động bị hủy để bảo vệ dữ liệu.`);
            }
        }, 60000);
        return;
    }

    // Default: Run regular commands
    await sendMessage(`⚙️ *Đang chạy lệnh:* \`${text}\``);
    const output = await runShellCommand(text);
    await sendMessage(`*Kết quả:*\n\`\`\`\n${output}\n\`\`\``);
}

// Long polling to pull updates from Telegram
async function pollUpdates() {
    try {
        const updates = await telegramRequest('getUpdates', { offset, timeout: 30 });
        for (const update of updates) {
            offset = update.update_id + 1;
            if (update.message) {
                await handleMessage(update.message);
            }
        }
    } catch (e) {
        console.error("Polling error:", e);
        // Wait a bit before retrying on error
        await new Promise(resolve => setTimeout(resolve, 5000));
    }
    // Continue polling
    pollUpdates();
}

// Start bot
console.log("Telegram Bot started. Polling updates...");
if (chat_id === "YOUR_TELEGRAM_CHAT_ID") {
    console.log("NOTE: Please send a message to your bot and find your Chat ID to lock it in config.json.");
}
pollUpdates();
