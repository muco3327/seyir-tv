/**
 * Network & Device Diagnostic Endpoint (/bilgi, /ip, /cihaz)
 * Provides Local IP detection via WebRTC, Public IP from server socket,
 * and technical security breakdown for MAC address.
 */

function getClientPublicIp(req) {
    let ip = req.headers['x-forwarded-for'] || req.headers['x-real-ip'] || req.socket.remoteAddress || '';
    if (ip.includes(',')) ip = ip.split(',')[0].trim();
    if (ip.startsWith('::ffff:')) ip = ip.replace('::ffff:', '');
    return ip || 'Bilinmiyor';
}

function renderDiagnosticPage(req) {
    const publicIp = getClientPublicIp(req);
    const userAgent = req.headers['user-agent'] || 'Bilinmiyor';

    return `<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Ağ & Cihaz Teşhis Merkezi</title>
    <style>
        :root {
            --bg: #090d16;
            --surface: #111827;
            --surface-border: #1f293d;
            --primary: #38bdf8;
            --primary-glow: rgba(56, 189, 248, 0.2);
            --success: #10b981;
            --warning: #f59e0b;
            --danger: #ef4444;
            --text: #f3f4f6;
            --text-dim: #9ca3af;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Oxygen, Ubuntu, Cantarell, sans-serif;
        }

        body {
            background-color: var(--bg);
            color: var(--text);
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            padding: 24px 16px;
        }

        .header {
            text-align: center;
            max-width: 600px;
            margin-bottom: 24px;
        }

        .header h1 {
            font-size: 24px;
            font-weight: 700;
            letter-spacing: -0.5px;
            color: #ffffff;
            margin-bottom: 6px;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 10px;
        }

        .header p {
            color: var(--text-dim);
            font-size: 14px;
        }

        .grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
            gap: 16px;
            width: 100%;
            max-width: 860px;
        }

        .card {
            background: var(--surface);
            border: 1px solid var(--surface-border);
            border-radius: 14px;
            padding: 20px;
            box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
            position: relative;
            overflow: hidden;
            display: flex;
            flex-direction: column;
        }

        .card::before {
            content: '';
            position: absolute;
            top: 0;
            left: 0;
            right: 0;
            height: 3px;
            background: linear-gradient(90deg, transparent, var(--card-glow, var(--primary)), transparent);
        }

        .card-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: 14px;
        }

        .card-title {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-dim);
            text-transform: uppercase;
            letter-spacing: 0.8px;
            display: flex;
            align-items: center;
            gap: 8px;
        }

        .card-badge {
            font-size: 11px;
            padding: 3px 8px;
            border-radius: 999px;
            font-weight: 600;
            text-transform: uppercase;
        }

        .badge-local {
            background: rgba(16, 185, 129, 0.15);
            color: var(--success);
            border: 1px solid rgba(16, 185, 129, 0.3);
        }

        .badge-warn {
            background: rgba(245, 158, 11, 0.15);
            color: var(--warning);
            border: 1px solid rgba(245, 158, 11, 0.3);
        }

        .badge-public {
            background: rgba(56, 189, 248, 0.15);
            color: var(--primary);
            border: 1px solid rgba(56, 189, 248, 0.3);
        }

        .card-val {
            font-size: 26px;
            font-weight: 700;
            font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
            color: #ffffff;
            word-break: break-all;
            margin: 6px 0 10px 0;
        }

        .card-val.loading {
            color: var(--text-dim);
            font-size: 18px;
            animation: pulse 1.5s infinite;
        }

        @keyframes pulse {
            0%, 100% { opacity: 0.6; }
            50% { opacity: 1; }
        }

        .card-desc {
            font-size: 13px;
            color: var(--text-dim);
            line-height: 1.5;
            margin-top: auto;
        }

        .btn-action {
            display: inline-flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
            background: #1e293b;
            color: #f1f5f9;
            border: 1px solid #334155;
            padding: 9px 14px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            margin-top: 12px;
            transition: all 0.2s ease;
            width: fit-content;
        }

        .btn-action:hover {
            background: #334155;
            border-color: #475569;
        }

        .info-box {
            background: rgba(255, 255, 255, 0.03);
            border: 1px solid rgba(255, 255, 255, 0.06);
            border-radius: 8px;
            padding: 10px 12px;
            font-size: 12px;
            color: #cbd5e1;
            line-height: 1.5;
            margin-top: 10px;
        }

        .device-info-list {
            list-style: none;
            display: flex;
            flex-direction: column;
            gap: 8px;
            margin-top: 8px;
        }

        .device-info-item {
            display: flex;
            justify-content: space-between;
            font-size: 13px;
            padding-bottom: 6px;
            border-bottom: 1px solid rgba(255, 255, 255, 0.04);
        }

        .device-info-item span:first-child {
            color: var(--text-dim);
        }

        .device-info-item span:last-child {
            font-weight: 600;
            font-family: monospace;
            color: #ffffff;
        }

        .footer {
            margin-top: 30px;
            text-align: center;
            font-size: 12px;
            color: #64748b;
        }
    </style>
</head>
<body>

    <div class="header">
        <h1>🌐 Ağ & Cihaz Teşhis Merkezi</h1>
        <p>Bağlantı parametreleri ve donanım güvenlik analizi</p>
    </div>

    <div class="grid">
        <!-- 1. YEREL IP -->
        <div class="card" style="--card-glow: var(--success);">
            <div class="card-header">
                <span class="card-title">📡 Yerel (Local / LAN) IP</span>
                <span class="card-badge badge-local">İç Ağ</span>
            </div>
            <div class="card-val loading" id="localIpVal">Tespit ediliyor…</div>
            <p class="card-desc" id="localIpDesc">
                Ev/ofis Wi-Fi modeminize bağlı cihazınızın iç ağ IP adresi (örn: 192.168.x.x veya 10.x.x.x).
            </p>
            <div id="mdnsSection" style="display:none; margin-top: 10px;">
                <div class="info-box" style="border-left: 3px solid var(--warning);">
                    ⚠️ <strong>mDNS Koruması:</strong> Tarayıcınız yerel IP'nizi anonimleştirdi (gizledi). Doğrudan yerel IP'yi çözmek için aşağıdaki butona basıp izin verebilirsiniz:
                </div>
                <button class="btn-action" id="btnResolveMdns" onclick="resolveMdnsWithPermission()">
                    🔍 Yerel IP'yi Çöz (İzin Ver)
                </button>
            </div>
        </div>

        <!-- 2. MAC ADRESİ -->
        <div class="card" style="--card-glow: var(--warning);">
            <div class="card-header">
                <span class="card-title">🔒 Donanım (MAC) Adresi</span>
                <span class="card-badge badge-warn">W3C Koruması</span>
            </div>
            <div class="card-val" style="color: #cbd5e1; font-size: 20px;">Erişilemez (Güvenlik Korumalı)</div>
            <p class="card-desc">
                İnternet web tarayıcılarında donanım MAC adresine erişim standartlar gereği engellidir.
            </p>
            <div class="info-box">
                🛡️ <strong>Teknik Neden:</strong> MAC adresi OSI 2. Katman (Data-Link) bilgisidir. İnternet paketleriniz modeme ulaştığında MAC adresi modemin içinde kalır ve internete çıkmaz. Hiçbir web sitesi uzaktan cihazınızın MAC adresini okuyamaz.
            </div>
            <div class="info-box" style="margin-top: 6px;">
                📺 <strong>MAC Adresini Nereden Bulabilirsiniz?</strong><br>
                • <strong>Android TV:</strong> Ayarlar > Ağ ve İnternet > Ağ Durumu<br>
                • <strong>Telefon:</strong> Ayarlar > Cihaz Hakkında > Wi-Fi MAC adresi<br>
                • <strong>Windows PC:</strong> CMD'ye <code>getmac</code> veya <code>ipconfig /all</code>
            </div>
        </div>

        <!-- 3. DIŞ IP -->
        <div class="card" style="--card-glow: var(--primary);">
            <div class="card-header">
                <span class="card-title">🌍 Dış (Public / WAN) IP</span>
                <span class="card-badge badge-public">İnternet</span>
            </div>
            <div class="card-val">${publicIp}</div>
            <p class="card-desc">
                İnternet Servis Sağlayıcınızın (Türk Telekom, Superonline vb.) modeminize atadığı dış IP adresi.
            </p>
            <div class="info-box" style="margin-top: 10px;">
                Sunucuya gelen bağlantı paketi üzerinden doğrulanmıştır.
            </div>
        </div>

        <!-- 4. CİHAZ VE TARAYICI BİLGİSİ -->
        <div class="card">
            <div class="card-header">
                <span class="card-title">💻 Cihaz & Ekran Bilgisi</span>
            </div>
            <ul class="device-info-list">
                <li class="device-info-item">
                    <span>Ekran Çözünürlüğü:</span>
                    <span id="screenRes">Hesaplanıyor…</span>
                </li>
                <li class="device-info-item">
                    <span>Ağ Durumu:</span>
                    <span id="netType">Hesaplanıyor…</span>
                </li>
                <li class="device-info-item">
                    <span>Platform:</span>
                    <span id="platformName">Hesaplanıyor…</span>
                </li>
                <li class="device-info-item">
                    <span>User-Agent:</span>
                    <span style="font-size: 11px; max-width: 180px; text-overflow: ellipsis; overflow: hidden; white-space: nowrap;" title="${userAgent}">${userAgent}</span>
                </li>
            </ul>
        </div>
    </div>

    <div class="footer">
        Seyir TV Diagnostic Engine • Portsuz Doğrudan Erişim (Port 80/3000)
    </div>

    <script>
        // Device info
        document.getElementById('screenRes').innerText = window.screen.width + 'x' + window.screen.height + ' (' + window.devicePixelRatio + 'x)';
        document.getElementById('platformName').innerText = navigator.platform || 'Bilinmiyor';
        
        if (navigator.connection) {
            const conn = navigator.connection;
            document.getElementById('netType').innerText = (conn.effectiveType ? conn.effectiveType.toUpperCase() : '') + (conn.rtt ? ' (' + conn.rtt + 'ms)' : '');
        } else {
            document.getElementById('netType').innerText = 'Standart (Online)';
        }

        // WebRTC Local IP Detection
        function detectLocalIP() {
            const localEl = document.getElementById('localIpVal');
            const mdnsSec = document.getElementById('mdnsSection');

            const RTCPC = window.RTCPeerConnection || window.webkitRTCPeerConnection || window.mozRTCPeerConnection;
            if (!RTCPC) {
                localEl.innerText = 'Desteklenmiyor';
                localEl.classList.remove('loading');
                return;
            }

            let found = false;
            let mdnsMasked = false;
            const pc = new RTCPC({ iceServers: [] });
            pc.createDataChannel('');
            
            pc.createOffer().then(offer => pc.setLocalDescription(offer)).catch(e => {
                localEl.innerText = 'Okunamadı';
                localEl.classList.remove('loading');
            });

            pc.onicecandidate = (event) => {
                if (!event || !event.candidate || !event.candidate.candidate) {
                    if (!found && mdnsMasked) {
                        localEl.innerText = 'mDNS ile Gizlenmiş';
                        localEl.classList.remove('loading');
                        mdnsSec.style.display = 'block';
                    } else if (!found) {
                        setTimeout(() => {
                            if (!found) {
                                localEl.innerText = 'Tespit Edilemedi';
                                localEl.classList.remove('loading');
                                mdnsSec.style.display = 'block';
                            }
                        }, 1200);
                    }
                    return;
                }

                const cand = event.candidate.candidate;
                const ipRegex = /([0-9]{1,3}(\\.[0-9]{1,3}){3})/;
                const match = cand.match(ipRegex);

                if (match) {
                    const ip = match[1];
                    // Check if private IP (RFC 1918)
                    if (ip.startsWith('192.168.') || ip.startsWith('10.') || ip.match(/^172\\.(1[6-9]|2[0-9]|3[0-1])\\./)) {
                        found = true;
                        localEl.innerText = ip;
                        localEl.classList.remove('loading');
                        mdnsSec.style.display = 'none';
                    }
                } else if (cand.includes('.local')) {
                    mdnsMasked = true;
                }
            };

            setTimeout(() => {
                if (!found && mdnsMasked) {
                    localEl.innerText = 'mDNS ile Maskeli';
                    localEl.classList.remove('loading');
                    mdnsSec.style.display = 'block';
                } else if (!found) {
                    localEl.innerText = 'Tespit Edilemedi';
                    localEl.classList.remove('loading');
                    mdnsSec.style.display = 'block';
                }
            }, 2500);
        }

        // Resolve mDNS via temporary media permission (unmasks private IP in Chrome/Firefox)
        async function resolveMdnsWithPermission() {
            const btn = document.getElementById('btnResolveMdns');
            btn.innerText = 'İzin Bekleniyor…';
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                // Stop audio tracks immediately
                stream.getTracks().forEach(t => t.stop());
                btn.innerText = 'Taranıyor…';
                detectLocalIP();
            } catch (err) {
                btn.innerText = 'İzin Verilmedi';
                alert('Yerel IP korumasını kaldırmak için tarayıcı mikrofon iznini onaylamanız gerekir. İzin sonrasında anında kapatılır.');
            }
        }

        // Run detection on load
        detectLocalIP();
    </script>
</body>
</html>`;
}

module.exports = {
    renderDiagnosticPage,
    getClientPublicIp
};
