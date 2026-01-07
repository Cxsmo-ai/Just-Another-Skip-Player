package com.brouken.player.utils

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket

class RemoteServer(private val controller: PlayerController) {

    interface PlayerController {
        fun getCurrentPosition(): Long
        fun getDuration(): Long
        fun isPlaying(): Boolean
        fun getMediaTitle(): String
        fun getStartMarker(): Long
        fun getEndMarker(): Long
        fun hasApiKey(): Boolean
        fun markStart()
        fun markEnd()
        fun submit()
        fun reset()
        fun seekTo(posMs: Long)
        fun togglePause()
        fun seekRelative(offsetMs: Long)
    }

    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        GlobalScope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(8355)
                DebugLogger.log("RemoteServer", "Server started on port 8355")
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    handleClient(client)
                }
            } catch (e: Exception) {
                DebugLogger.log("RemoteServer", "Error: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun handleClient(client: Socket) {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                client.soTimeout = 10000
                val input = BufferedReader(InputStreamReader(client.getInputStream()))
                val output = PrintWriter(client.getOutputStream(), true)

                val requestLine = input.readLine() ?: return@launch
                DebugLogger.log("RemoteServer", "Request: $requestLine")
                
                val parts = requestLine.split(" ")
                if (parts.size < 2) {
                    send400(output, "Bad request")
                    return@launch
                }

                // Drain headers
                while (true) {
                    val line = input.readLine()
                    if (line == null || line.isEmpty()) break
                }

                val method = parts[0]
                val path = parts[1]

                when {
                    method == "GET" && path == "/" -> sendWebApp(output)
                    method == "GET" && path == "/api/status" -> sendStatus(output)
                    method == "POST" && path.startsWith("/api/command") -> handleCommand(path, output)
                    method == "OPTIONS" -> handleOptions(output)
                    else -> send404(output)
                }
                
                output.flush()
                output.close()
                input.close()
                client.close()
            } catch (e: Exception) {
                DebugLogger.log("RemoteServer", "Client error: ${e.message}")
                try { client.close() } catch (_: Exception) {}
            }
        }
    }

    private fun handleCommand(path: String, output: PrintWriter) {
        val cmd = path.substringAfter("cmd=", "")
        
        DebugLogger.log("RemoteServer", "Command: $cmd")
        
        Handler(Looper.getMainLooper()).post {
            try {
                when (cmd) {
                    "markStart" -> controller.markStart()
                    "markEnd" -> controller.markEnd()
                    "submit" -> controller.submit()
                    "reset" -> controller.reset()
                    "pause" -> controller.togglePause()
                }
                DebugLogger.log("RemoteServer", "Command $cmd executed")
            } catch (e: Exception) {
                DebugLogger.log("RemoteServer", "Command error: ${e.message}")
            }
        }
        
        sendJson(output, """{"ok":true,"command":"$cmd"}""")
    }

    private fun sendStatus(output: PrintWriter) {
        val json = """{"time":${controller.getCurrentPosition()},"duration":${controller.getDuration()},"playing":${controller.isPlaying()},"title":"${controller.getMediaTitle().replace("\"", "\\\"")}","startMarker":${controller.getStartMarker()},"endMarker":${controller.getEndMarker()},"hasApiKey":${controller.hasApiKey()}}"""
        sendJson(output, json)
    }

    private fun sendJson(output: PrintWriter, json: String) {
        output.println("HTTP/1.1 200 OK")
        output.println("Content-Type: application/json")
        output.println("Access-Control-Allow-Origin: *")
        output.println("Access-Control-Allow-Methods: GET, POST, OPTIONS")
        output.println("Access-Control-Allow-Headers: Content-Type")
        output.println("Connection: close")
        output.println("Content-Length: ${json.toByteArray(Charsets.UTF_8).size}")
        output.println()
        output.println(json)
    }
    
    private fun handleOptions(output: PrintWriter) {
        output.println("HTTP/1.1 200 OK")
        output.println("Access-Control-Allow-Origin: *")
        output.println("Access-Control-Allow-Methods: GET, POST, OPTIONS")
        output.println("Access-Control-Allow-Headers: Content-Type")
        output.println("Access-Control-Max-Age: 86400")
        output.println("Connection: close")
        output.println()
    }
    
    private fun send404(output: PrintWriter) {
         output.println("HTTP/1.1 404 Not Found")
         output.println("Connection: close")
         output.println()
    }
    
    private fun send400(output: PrintWriter, message: String) {
         output.println("HTTP/1.1 400 Bad Request")
         output.println("Content-Type: text/plain")
         output.println("Connection: close")
         output.println()
         output.println(message)
    }

    private fun sendWebApp(output: PrintWriter) {
        val html = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Skip Remote</title>
    <style>
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            background: #0a0a12;
            color: white;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            padding: 20px;
        }
        .panel {
            background: #13131f;
            border-radius: 16px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .timer {
            font-size: 48px;
            font-family: monospace;
            color: #00f3ff;
            text-align: center;
            background: #050508;
            border-radius: 8px;
            padding: 20px;
            margin-bottom: 20px;
        }
        .markers {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
            margin-bottom: 20px;
        }
        .marker {
            background: #0f0f16;
            border-radius: 8px;
            padding: 16px;
            text-align: center;
        }
        .marker-label { font-size: 12px; color: #666; margin-bottom: 8px; }
        .marker-value { font-size: 24px; font-family: monospace; color: #555; }
        .marker-value.cyan { color: #00f3ff; }
        .marker-value.pink { color: #ff0055; }
        .btn-row {
            display: grid;
            grid-template-columns: 1fr 1fr;
            gap: 16px;
            margin-bottom: 16px;
        }
        .btn {
            border: none;
            border-radius: 12px;
            padding: 24px;
            font-size: 16px;
            font-weight: bold;
            cursor: pointer;
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 8px;
        }
        .btn-start {
            background: linear-gradient(135deg, #0e7490, #155e75);
            color: #22d3ee;
        }
        .btn-end {
            background: linear-gradient(135deg, #9d174d, #831843);
            color: #f472b6;
        }
        .btn-submit {
            width: 100%;
            background: #333;
            color: #666;
            padding: 20px;
        }
        .btn-submit.enabled {
            background: linear-gradient(135deg, #ca8a04, #a16207);
            color: #000;
            box-shadow: 0 0 20px rgba(234,179,8,0.3);
        }
        .icon { font-size: 28px; }
        .status { text-align: center; color: #666; margin-bottom: 20px; font-size: 14px; }
        .status.ok { color: #22c55e; }
        .status.error { color: #ef4444; }
        .toast {
            position: fixed;
            top: 20px;
            left: 50%;
            transform: translateX(-50%) translateY(-100px);
            background: #22c55e;
            color: #000;
            padding: 16px 32px;
            border-radius: 30px;
            font-weight: bold;
            opacity: 0;
            transition: all 0.3s;
        }
        .toast.show { transform: translateX(-50%) translateY(0); opacity: 1; }
    </style>
</head>
<body>
    <div class="panel">
        <div class="markers">
            <div class="marker">
                <div class="marker-label">START</div>
                <div id="start-val" class="marker-value">--:--</div>
            </div>
            <div class="marker">
                <div class="marker-label">END</div>
                <div id="end-val" class="marker-value">--:--</div>
            </div>
        </div>
    </div>
    
    <div class="btn-row">
        <button class="btn btn-start" onclick="cmd('markStart')">
            <span class="icon">▶</span>
            SET START
        </button>
        <button class="btn btn-end" onclick="cmd('markEnd')">
            <span class="icon">◼</span>
            SET END
        </button>
    </div>
    
    <button id="btn-submit" class="btn btn-submit" onclick="doSubmit()" disabled>
        <span class="icon">☁</span>
        SUBMIT TO INTRODB
    </button>
    
    <div id="toast" class="toast">✓ SUBMITTED</div>
    
    <script>
        let startMs = -1, endMs = -1;
        
        function fmt(ms) {
            if (ms < 0) return '--:--';
            const s = Math.floor(ms / 1000);
            return Math.floor(s/60).toString().padStart(2,'0') + ':' + (s%60).toString().padStart(2,'0');
        }
        
        function cmd(c) {
            fetch('/api/command?cmd=' + c, { method: 'POST' })
                .then(r => r.json())
                .then(d => { if (!d.ok) throw new Error(d.error); })
                .catch(e => console.error(e));
        }
        
        function doSubmit() {
            cmd('submit');
            const toast = document.getElementById('toast');
            toast.classList.add('show');
            setTimeout(() => toast.classList.remove('show'), 2000);
        }
        
        function updateUI(d) {
            startMs = d.startMarker;
            endMs = d.endMarker;
            
            const startVal = document.getElementById('start-val');
            const endVal = document.getElementById('end-val');
            
            startVal.innerText = fmt(startMs);
            startVal.className = startMs >= 0 ? 'marker-value cyan' : 'marker-value';
            
            endVal.innerText = fmt(endMs);
            endVal.className = endMs >= 0 ? 'marker-value pink' : 'marker-value';
            
            const btn = document.getElementById('btn-submit');
            if (startMs >= 0 && endMs > startMs && d.hasApiKey) {
                btn.disabled = false;
                btn.className = 'btn btn-submit enabled';
            } else {
                btn.disabled = true;
                btn.className = 'btn btn-submit';
            }
        }
        
        function poll() {
            fetch('/api/status')
                .then(r => r.json())
                .then(updateUI)
                .catch(() => {});
        }
        
        poll();
        setInterval(poll, 500);
    </script>
</body>
</html>
""".trimIndent()

        val bytes = html.toByteArray(Charsets.UTF_8)
        output.println("HTTP/1.1 200 OK")
        output.println("Content-Type: text/html; charset=UTF-8")
        output.println("Content-Length: " + bytes.size)
        output.println()
        output.print(html)
        output.flush()
    }
}