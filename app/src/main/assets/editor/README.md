# Editor assets — auto-installed at first launch

In v2 of LowLevelIDE, the `OnboardingActivity` calls
`CodeMirrorInstaller.install()` which downloads CodeMirror v5 directly into
`<filesDir>/editor/` from jsDelivr. **You don't need to drop anything in this
folder before building.**

If you'd rather ship offline (no network at first launch), drop the same set of
files into this folder and the installer will pick them up via the asset:// path
fallback before going to the network.

## Files the runtime expects (resolved via /cm/ in WebView)

```
editor/
├── codemirror.js
├── codemirror.css
├── theme/
│   ├── material-darker.css
│   └── eclipse.css
├── addon/
│   ├── edit/{matchbrackets,closebrackets}.js
│   ├── dialog/{dialog,dialog.css}
│   ├── search/{search,searchcursor,jump-to-line,matchesonscrollbar,matchesonscrollbar.css}.js
│   ├── scroll/annotatescrollbar.js
│   └── selection/active-line.js
└── mode/
    ├── clike/clike.js          (C, C++, Java, Kotlin)
    ├── python/python.js
    ├── javascript/javascript.js
    ├── shell/shell.js
    ├── rust/rust.js
    ├── go/go.js
    ├── xml/xml.js
    ├── css/css.js
    ├── htmlmixed/htmlmixed.js
    ├── markdown/markdown.js
    ├── yaml/yaml.js
    └── sql/sql.js
```

## License

CodeMirror 5 is MIT licensed. Keep its `LICENSE` file alongside any redistributed
APK. Source: https://github.com/codemirror/codemirror5
