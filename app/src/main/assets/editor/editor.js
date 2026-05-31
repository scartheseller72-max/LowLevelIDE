/*
 * LowLevelIDE editor logic.
 *
 * Loaded inside the WebView hosted by EditorFragment. Bridges to native via
 * the global `Android` object exposed from FileInterface.kt:
 *   - Android.openFile(path) -> string
 *   - Android.saveFile(path, content) -> boolean
 *   - Android.listFiles(path) -> JSON string
 *   - Android.fileExists(path) -> boolean
 *   - Android.deleteFile(path) -> boolean
 *
 * One CodeMirror instance, multiple Doc objects keyed by path.
 */
(function () {
    const docs = new Map();      // path -> CodeMirror.Doc
    const order = [];             // tab order (paths)
    let active = null;            // currently active path
    let cm = null;
    let currentTheme = "material-darker";  // synced from native AppSettings
    let wordWrap = true;

    // Light themes need a light page background so gutters/status blend in.
    const LIGHT_THEMES = { "eclipse": 1, "idea": 1 };
    function applyChromeForTheme(name) {
        const light = !!LIGHT_THEMES[name];
        document.body.style.background = light ? "#ffffff" : "#1e1e1e";
        document.body.style.color = light ? "#222" : "#ddd";
        const bar = document.getElementById("status-bar");
        if (bar) {
            bar.style.background = light ? "#f0f0f0" : "#252526";
            bar.style.color = light ? "#555" : "#aaa";
            bar.style.borderTop = light ? "1px solid #ddd" : "1px solid #333";
        }
    }

    function modeFor(name) {
        const ext = (name || "").toLowerCase().split(".").pop();
        switch (ext) {
            case "c": case "h": return "text/x-csrc";
            case "cc": case "cpp": case "cxx": case "hpp": case "hxx": return "text/x-c++src";
            case "java": return "text/x-java";
            case "kt": return "text/x-kotlin";
            case "py": return "python";
            case "js": case "mjs": return "javascript";
            case "ts": case "tsx": return "text/typescript";
            case "rs": return "rust";
            case "go": return "go";
            case "sh": case "bash": case "zsh": return "shell";
            case "rb": return "ruby";
            case "php": return "php";
            case "html": case "htm": return "htmlmixed";
            case "css": return "css";
            case "xml": return "xml";
            case "json": return { name: "javascript", json: true };
            case "yaml": case "yml": return "yaml";
            case "md": case "markdown": return "markdown";
            case "sql": return "sql";
            default: return "text/plain";
        }
    }

    function newCodeMirror() {
        const host = document.getElementById("editor-host");
        host.innerHTML = "";
        cm = CodeMirror(host, {
            value: "",
            mode: "text/plain",
            theme: currentTheme,
            lineNumbers: true,
            indentUnit: 4,
            tabSize: 4,
            indentWithTabs: false,
            matchBrackets: true,
            autoCloseBrackets: true,
            styleActiveLine: true,
            lineWrapping: wordWrap,
            extraKeys: {
                "Ctrl-S": saveActive,
                "Cmd-S": saveActive,
                "Ctrl-F": openFind,
                "Ctrl-H": openReplace,
                "Ctrl-G": "jumpToLine",
                "Ctrl-N": promptNewFile,
                "Ctrl-W": closeActive,
            }
        });
        cm.on("cursorActivity", function () {
            const c = cm.getCursor();
            document.getElementById("status-cursor").textContent = (c.line + 1) + ":" + (c.ch + 1);
        });
    }

    function showWelcome() {
        const host = document.getElementById("editor-host");
        host.innerHTML =
            '<div class="welcome-screen">' +
            '  <h2>Welcome to LowLevelIDE</h2>' +
            '  <p>Open a file from the drawer (left-side menu &rarr; Files), or create a new one with the + button above.</p>' +
            '</div>';
        cm = null;
    }

    function ensureEditor() { if (!cm) newCodeMirror(); }

    function setActive(path) {
        ensureEditor();
        active = path;
        cm.swapDoc(docs.get(path));
        cm.setOption("mode", modeFor(path));
        document.getElementById("status-mode").textContent = (cm.getOption("mode") || "text") + "";
        cm.focus();
    }

    function openFile(path) {
        if (typeof Android === "undefined") return;
        if (docs.has(path)) { setActive(path); return; }
        const text = Android.openFile(path);
        const doc = CodeMirror.Doc(text, modeFor(path));
        docs.set(path, doc);
        order.push(path);
        setActive(path);
    }

    function saveActive() {
        if (!active || !cm) return;
        if (typeof Android === "undefined") return;
        Android.saveFile(active, cm.getValue());
    }

    function saveAll() {
        if (typeof Android === "undefined") return;
        order.forEach(function (p) {
            const d = docs.get(p);
            if (d) Android.saveFile(p, d.getValue());
        });
    }

    function closeActive() {
        if (!active) return;
        const idx = order.indexOf(active);
        order.splice(idx, 1);
        docs.delete(active);
        if (order.length === 0) {
            active = null;
            showWelcome();
        } else {
            setActive(order[Math.max(0, idx - 1)]);
        }
    }

    function promptNewFile() {
        const name = prompt("New file name (relative to home):", "untitled.txt");
        if (!name) return;
        if (!docs.has(name)) {
            const doc = CodeMirror.Doc("", modeFor(name));
            docs.set(name, doc);
            order.push(name);
        }
        setActive(name);
    }

    function openFind() { ensureEditor(); cm && cm.execCommand("find"); }
    function openReplace() { ensureEditor(); cm && cm.execCommand("replace"); }

    function setEditorFontSize(px) {
        const css = ".CodeMirror { font-size: " + px + "px; }";
        let style = document.getElementById("dyn-font");
        if (!style) {
            style = document.createElement("style");
            style.id = "dyn-font";
            document.head.appendChild(style);
        }
        style.textContent = css;
    }

    function getActiveFile() { return active; }

    function setEditorTheme(name) {
        if (!name) return;
        currentTheme = name;
        applyChromeForTheme(name);
        if (cm) cm.setOption("theme", name);
    }

    function setWordWrap(on) {
        wordWrap = !!on;
        if (cm) cm.setOption("lineWrapping", wordWrap);
    }

    // Expose to host activity / file browser bridge.
    window.openFile = openFile;
    window.saveActive = saveActive;
    window.saveAll = saveAll;
    window.closeActive = closeActive;
    window.openFind = openFind;
    window.openReplace = openReplace;
    window.promptNewFile = promptNewFile;
    window.setEditorFontSize = setEditorFontSize;
    window.setEditorTheme = setEditorTheme;
    window.setWordWrap = setWordWrap;
    window.getActiveFile = getActiveFile;

    // Initial state.
    document.addEventListener("DOMContentLoaded", function () {
        showWelcome();
    });
    if (document.readyState !== "loading") showWelcome();
})();
