package com.kuqforza.iptv.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WebAdminServer(
    private val context: Context,
    port: Int = 8089
) : NanoHTTPD(port) {

    private val configFile: File
        get() = File(context.filesDir, "portals.json")

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        val method = session.method

        return when {
            uri == "/" || uri == "/admin" -> serveAdmin()
            uri == "/api/portals" && method == Method.GET -> getPortals()
            uri == "/api/portals" && method == Method.POST -> addPortal(session)
            uri.startsWith("/api/portals/") && method == Method.DELETE -> deletePortal(uri)
            uri == "/api/status" -> getStatus()
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }

    private fun loadPortals(): JSONArray {
        return try {
            if (configFile.exists()) {
                JSONArray(configFile.readText())
            } else JSONArray()
        } catch (e: Exception) { JSONArray() }
    }

    private fun savePortals(portals: JSONArray) {
        configFile.writeText(portals.toString(2))
    }

    private fun getPortals(): Response {
        val portals = loadPortals()
        return jsonResponse(portals.toString())
    }

    private fun addPortal(session: IHTTPSession): Response {
        val body = HashMap<String, String>()
        session.parseBody(body)
        val json = body["postData"] ?: return jsonResponse("""{"ok":false,"error":"No data"}""", 400)

        try {
            val portal = JSONObject(json)
            val portals = loadPortals()
            portal.put("id", System.currentTimeMillis().toString())
            portals.put(portal)
            savePortals(portals)
            return jsonResponse("""{"ok":true,"id":"${portal.getString("id")}"}""")
        } catch (e: Exception) {
            return jsonResponse("""{"ok":false,"error":"${e.message}"}""", 400)
        }
    }

    private fun deletePortal(uri: String): Response {
        val id = uri.removePrefix("/api/portals/")
        val portals = loadPortals()
        val newPortals = JSONArray()
        for (i in 0 until portals.length()) {
            val p = portals.getJSONObject(i)
            if (p.optString("id") != id) newPortals.put(p)
        }
        savePortals(newPortals)
        return jsonResponse("""{"ok":true}""")
    }

    private fun getStatus(): Response {
        val portals = loadPortals()
        val status = JSONObject()
        status.put("app", "Kuqforza IPTV Premium")
        status.put("portals", portals.length())
        status.put("running", true)
        return jsonResponse(status.toString())
    }

    private fun serveAdmin(): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html", ADMIN_HTML)
    }

    private fun jsonResponse(json: String, code: Int = 200): Response {
        val status = if (code == 200) Response.Status.OK else Response.Status.BAD_REQUEST
        val r = newFixedLengthResponse(status, "application/json", json)
        r.addHeader("Access-Control-Allow-Origin", "*")
        return r
    }

    companion object {
        const val ADMIN_HTML = """
<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8"/>
<meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>Kuqforza Admin</title>
<style>
:root{--bg:#000;--bg1:#060c16;--bg2:#0c1424;--text:#e8eef6;--dim:#5a6a82;--acc:#0088ff;--acc2:#55bbff;--ok:#00e676;--err:#ff4d58;--line:rgba(0,136,255,.12)}
*{box-sizing:border-box;margin:0;padding:0;-webkit-tap-highlight-color:transparent}
body{background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,sans-serif;min-height:100vh}
body::before{content:"";position:fixed;inset:0;pointer-events:none;background:radial-gradient(ellipse 50% 40% at 70% 0%,rgba(0,136,255,.06),transparent 60%),radial-gradient(ellipse 40% 50% at 10% 90%,rgba(85,187,255,.04),transparent 50%)}
.container{max-width:520px;margin:0 auto;padding:20px 16px;position:relative;z-index:1}

/* Header */
.header{text-align:center;margin-bottom:24px}
.logo{font-size:28px;font-weight:900;letter-spacing:-1px}
.logo .k{color:var(--acc)}
.logo .q{color:var(--acc2)}
.logo .rest{color:var(--text)}
.sub{font-size:10px;color:var(--dim);letter-spacing:2px;text-transform:uppercase;margin-top:2px}
.status-dot{display:inline-block;width:8px;height:8px;border-radius:50%;background:var(--ok);margin-right:4px;animation:pulse 2s infinite}
@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}

/* Cards */
.glass{background:var(--bg1);border:1px solid var(--line);border-radius:16px;padding:16px;margin-bottom:12px;position:relative;overflow:hidden}
.glass::before{content:"";position:absolute;top:0;left:0;right:0;height:2px;background:linear-gradient(90deg,transparent,var(--acc),transparent);opacity:.5}
.glass h2{font-size:14px;font-weight:700;margin-bottom:12px;display:flex;align-items:center;gap:8px}
.glass h2 .icon{width:28px;height:28px;border-radius:8px;background:rgba(0,136,255,.15);display:flex;align-items:center;justify-content:center;font-size:14px}

/* Forms */
.label{font-size:9px;font-weight:700;color:var(--dim);text-transform:uppercase;letter-spacing:.6px;margin-bottom:4px}
input,select{width:100%;padding:10px 12px;border-radius:10px;border:1px solid var(--line);background:rgba(255,255,255,.03);color:var(--text);font-size:13px;margin-bottom:10px;outline:none;transition:border-color .2s}
input:focus,select:focus{border-color:var(--acc)}
input::placeholder{color:rgba(255,255,255,.15)}
select{appearance:none;cursor:pointer}

/* Buttons */
.btn{width:100%;padding:12px;border-radius:12px;border:none;font-weight:700;font-size:14px;cursor:pointer;transition:all .15s;display:flex;align-items:center;justify-content:center;gap:6px}
.btn:active{transform:scale(.97)}
.btn-p{background:linear-gradient(135deg,var(--acc),var(--acc2));color:#fff;box-shadow:0 4px 20px rgba(0,136,255,.3)}
.btn-s{background:rgba(255,255,255,.04);color:var(--text);border:1px solid var(--line)}
.btn-d{background:rgba(255,77,88,.1);color:var(--err);border:1px solid rgba(255,77,88,.2)}
.btn:disabled{opacity:.3}

/* Type selector */
.types{display:flex;gap:6px;margin-bottom:12px}
.type-btn{flex:1;padding:10px 6px;border-radius:10px;border:1px solid var(--line);background:transparent;color:var(--dim);font-weight:700;font-size:11px;cursor:pointer;text-align:center;transition:all .2s}
.type-btn.on{background:rgba(0,136,255,.15);border-color:var(--acc);color:var(--acc)}

/* Portal list */
.portal{background:var(--bg2);border:1px solid var(--line);border-radius:12px;padding:12px;margin-bottom:8px;display:flex;align-items:center;gap:10px}
.portal .info{flex:1;overflow:hidden}
.portal .name{font-size:13px;font-weight:700;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.portal .meta{font-size:10px;color:var(--dim);margin-top:2px}
.portal .type-badge{padding:3px 8px;border-radius:8px;font-size:9px;font-weight:700;letter-spacing:.3px}
.portal .del{width:32px;height:32px;border-radius:8px;border:1px solid rgba(255,77,88,.2);background:rgba(255,77,88,.08);color:var(--err);font-size:14px;cursor:pointer;display:flex;align-items:center;justify-content:center}
.portal .del:active{transform:scale(.9)}

.empty{text-align:center;padding:24px;color:var(--dim);font-size:12px}
.toast{position:fixed;bottom:20px;left:50%;transform:translateX(-50%);padding:10px 20px;border-radius:10px;font-size:12px;font-weight:600;z-index:999;animation:fadeUp .3s}
.toast.ok{background:rgba(0,230,118,.15);color:var(--ok);border:1px solid rgba(0,230,118,.2)}
.toast.err{background:rgba(255,77,88,.15);color:var(--err);border:1px solid rgba(255,77,88,.2)}
@keyframes fadeUp{from{opacity:0;transform:translateX(-50%) translateY(10px)}to{opacity:1;transform:translateX(-50%) translateY(0)}}
</style>
</head>
<body>
<div class="container">

<div class="header">
  <div class="logo"><span class="k">K</span><span class="q">Q</span> <span class="rest">KUQFORZA</span></div>
  <div class="sub">IPTV PREMIUM — ADMIN PANEL</div>
  <div style="margin-top:8px;font-size:11px;color:var(--dim)"><span class="status-dot"></span> Connecte</div>
</div>

<!-- ADD PORTAL -->
<div class="glass">
  <h2><div class="icon">&#x2795;</div> Ajouter un portail</h2>
  
  <div class="types" id="typeSelector">
    <button class="type-btn on" onclick="setType('xtream')">Xtream</button>
    <button class="type-btn" onclick="setType('m3u')">M3U</button>
    <button class="type-btn" onclick="setType('stalker')">Stalker</button>
  </div>

  <div class="label">Nom</div>
  <input id="pName" placeholder="Mon IPTV"/>

  <div id="fieldsXtream">
    <div class="label">Serveur</div>
    <input id="pServer" placeholder="http://server.com:8080"/>
    <div class="label">Identifiant</div>
    <input id="pUser" placeholder="username"/>
    <div class="label">Mot de passe</div>
    <input id="pPass" placeholder="password" type="password"/>
  </div>

  <div id="fieldsM3u" style="display:none">
    <div class="label">URL M3U</div>
    <input id="pM3u" placeholder="http://server.com/playlist.m3u"/>
  </div>

  <div id="fieldsStalker" style="display:none">
    <div class="label">URL Portail</div>
    <input id="pPortal" placeholder="http://server.com:8080/c/"/>
    <div class="label">Adresse MAC</div>
    <input id="pMac" placeholder="00:1A:79:XX:XX:XX"/>
  </div>

  <button class="btn btn-p" onclick="addPortal()">&#x2795; Ajouter</button>
</div>

<!-- PORTAL LIST -->
<div class="glass">
  <h2><div class="icon">&#x1f4e1;</div> Portails configures</h2>
  <div id="portalList"><div class="empty">Aucun portail</div></div>
</div>

</div>

<script>
var currentType = 'xtream';

function setType(t) {
  currentType = t;
  document.querySelectorAll('.type-btn').forEach(b => b.className = 'type-btn');
  event.target.className = 'type-btn on';
  document.getElementById('fieldsXtream').style.display = t === 'xtream' ? 'block' : 'none';
  document.getElementById('fieldsM3u').style.display = t === 'm3u' ? 'block' : 'none';
  document.getElementById('fieldsStalker').style.display = t === 'stalker' ? 'block' : 'none';
}

function toast(msg, type) {
  var t = document.createElement('div');
  t.className = 'toast ' + type;
  t.textContent = msg;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2500);
}

async function loadPortals() {
  try {
    var r = await fetch('/api/portals');
    var portals = await r.json();
    var zone = document.getElementById('portalList');
    if (!portals.length) { zone.innerHTML = '<div class="empty">Aucun portail</div>'; return; }
    var html = '';
    portals.forEach(function(p) {
      var typeColors = {xtream:'#0088ff',m3u:'#00e676',stalker:'#ffab40'};
      var color = typeColors[p.type] || '#888';
      html += '<div class="portal">'
        + '<div class="info"><div class="name">' + (p.name || '?') + '</div>'
        + '<div class="meta">' + (p.server || p.m3u_url || p.portal || '') + '</div></div>'
        + '<span class="type-badge" style="color:'+color+';border:1px solid '+color+'33;background:'+color+'15">' + (p.type||'?').toUpperCase() + '</span>'
        + '<button class="del" onclick="delPortal(''+p.id+'')">&#x2715;</button>'
        + '</div>';
    });
    zone.innerHTML = html;
  } catch(e) { console.error(e); }
}

async function addPortal() {
  var data = { name: document.getElementById('pName').value, type: currentType };
  if (currentType === 'xtream') {
    data.server = document.getElementById('pServer').value;
    data.username = document.getElementById('pUser').value;
    data.password = document.getElementById('pPass').value;
  } else if (currentType === 'm3u') {
    data.m3u_url = document.getElementById('pM3u').value;
  } else if (currentType === 'stalker') {
    data.portal = document.getElementById('pPortal').value;
    data.mac = document.getElementById('pMac').value;
  }
  if (!data.name) { toast('Entrez un nom', 'err'); return; }
  try {
    var r = await fetch('/api/portals', { method: 'POST', body: JSON.stringify(data) });
    var d = await r.json();
    if (d.ok) { toast('Portail ajoute', 'ok'); loadPortals(); } else { toast(d.error, 'err'); }
  } catch(e) { toast('Erreur: ' + e.message, 'err'); }
}

async function delPortal(id) {
  if (!confirm('Supprimer ce portail ?')) return;
  try {
    await fetch('/api/portals/' + id, { method: 'DELETE' });
    toast('Supprime', 'ok');
    loadPortals();
  } catch(e) { toast('Erreur', 'err'); }
}

loadPortals();
</script>
</body>
</html>
"""
    }
}
