package zio.http

import zio.http.codec.Doc
import zio.http.template2._

object RoutesOverview {
  def apply(patterns: Iterable[RoutePattern[_]]): Dom = {
    val sortedPatterns = patterns.toSeq.sortBy(_.method.asInstanceOf[Product].productPrefix).sortBy(_.pathCodec.render)
    html(
      head(
        meta(charset := "UTF-8"),
        meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
        title("404 - Route Not Found"),
        // language=JavaScript
        script(s"""
          const routes = ${routesToJson(sortedPatterns)};
          let currentRoute = null;

          function openDialog(routeIdx) {
            currentRoute = routes[routeIdx];
            const dialog = document.getElementById('paramDialog');
            const form = document.getElementById('dialogForm');

            form.innerHTML = '';

            if (currentRoute.params.length === 0) {
              form.innerHTML = '<p style="color: #6b7280; font-style: italic;">No parameters required for this route.</p>';
            } else {
              currentRoute.params.forEach(param => {
                const group = document.createElement('div');
                group.className = 'form-group';

                const label = document.createElement('label');
                label.textContent = param.name + (param.type ? ' (' + param.type + ')' : '');

                const input = document.createElement('input');
                input.id = 'param-' + param.name;
                input.type = param.type === 'int' || param.type === 'long' ? 'number' : 'text';
                input.placeholder = 'Enter ' + param.name;
                input.oninput = updatePreview;

                group.appendChild(label);
                group.appendChild(input);
                form.appendChild(group);
              });
            }

            updatePreview();
            dialog.showModal();
          }

          function closeDialog() {
            document.getElementById('paramDialog').close();
          }

          function updatePreview() {
            if (!currentRoute) return;

            let url = currentRoute.path;
            let allFilled = currentRoute.params.length === 0;

            if (currentRoute.params.length > 0) {
              allFilled = true;
              currentRoute.params.forEach(param => {
                const input = document.getElementById('param-' + param.name);
                const value = input.value || '{' + param.name + '}';
                url = url.replace('{' + param.name + '}', value);
                if (!input.value) {
                  allFilled = false;
                }
              });
            }

            const previewDiv = document.getElementById('previewUrl');
            const isNavigable = currentRoute.method === 'GET' || currentRoute.method === 'ANY';

            if (isNavigable && allFilled) {
              previewDiv.innerHTML = '<a href="' + url + '">' + url + '</a>';
            } else {
              previewDiv.textContent = url;
            }

            // Enable/disable buttons based on whether all params are filled
            const copyBtn = document.getElementById('copyBtn');
            const goBtn = document.getElementById('goBtn');

            if (allFilled) {
              copyBtn.removeAttribute('disabled');
              if (isNavigable) {
                goBtn.removeAttribute('disabled');
              } else {
                goBtn.setAttribute('disabled', 'true');
              }
            } else {
              copyBtn.setAttribute('disabled', 'true');
              goBtn.setAttribute('disabled', 'true');
            }
          }

          function navigateToUrl() {
            if (!currentRoute) return;

            let url = currentRoute.path;
            let allFilled = true;

            currentRoute.params.forEach(param => {
              const input = document.getElementById('param-' + param.name);
              if (!input.value) {
                allFilled = false;
                input.style.borderColor = '#ef4444';
              } else {
                input.style.borderColor = '#d1d5db';
                url = url.replace('{' + param.name + '}', input.value);
              }
            });

            if (allFilled) {
              window.location.href = url;
            }
          }

          function copyToClipboard() {
            if (!currentRoute) return;

            let url = currentRoute.path;
            currentRoute.params.forEach(param => {
              const input = document.getElementById('param-' + param.name);
              if (input.value) {
                url = url.replace('{' + param.name + '}', input.value);
              }
            });

            navigator.clipboard.writeText(url).then(() => {
              const copyBtn = document.getElementById('copyBtn');
              const originalText = copyBtn.textContent;
              copyBtn.textContent = 'Copied!';
              setTimeout(() => {
                copyBtn.textContent = originalText;
              }, 2000);
            });
          }

          // Theme management
          function getPreferredTheme() {
            const stored = localStorage.getItem('theme');
            if (stored) {
              return stored;
            }
            return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
          }

          function setTheme(theme) {
            document.documentElement.setAttribute('data-theme', theme);
            localStorage.setItem('theme', theme);
            updateThemeIcon(theme);
          }

          function updateThemeIcon(theme) {
            const icon = document.getElementById('themeIcon');
            icon.textContent = theme === 'dark' ? '☀️' : '🌙';
          }

          function toggleTheme() {
            const currentTheme = document.documentElement.getAttribute('data-theme') || getPreferredTheme();
            const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
            setTheme(newTheme);
          }

          // Initialize theme on page load
          document.addEventListener('DOMContentLoaded', () => {
            setTheme(getPreferredTheme());
          });

          // Listen for system theme changes
          window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
            if (!localStorage.getItem('theme')) {
              setTheme(e.matches ? 'dark' : 'light');
            }
          });

          // Set initial theme immediately (before DOMContentLoaded)
          setTheme(getPreferredTheme());
        """),
        // language=css
        style.inlineCss("""
          :root {
            --bg-gradient-start: #667eea;
            --bg-gradient-end: #764ba2;
            --card-bg: #ffffff;
            --card-hover-bg: #f9fafb;
            --text-primary: #333333;
            --text-secondary: #6b7280;
            --text-tertiary: #9ca3af;
            --text-light: #ffffff;
            --border-color: #e5e7eb;
            --input-border: #d1d5db;
            --preview-bg: #f3f4f6;
            --dialog-bg: #ffffff;
            --shadow-sm: rgba(0,0,0,0.1);
            --shadow-md: rgba(0,0,0,0.15);
          }

          @media (prefers-color-scheme: dark) {
            :root {
              --bg-gradient-start: #1e1b4b;
              --bg-gradient-end: #312e81;
              --card-bg: #1f2937;
              --card-hover-bg: #374151;
              --text-primary: #f9fafb;
              --text-secondary: #d1d5db;
              --text-tertiary: #9ca3af;
              --text-light: #ffffff;
              --border-color: #374151;
              --input-border: #4b5563;
              --preview-bg: #374151;
              --dialog-bg: #1f2937;
              --shadow-sm: rgba(0,0,0,0.3);
              --shadow-md: rgba(0,0,0,0.5);
            }
          }

          [data-theme="light"] {
            --bg-gradient-start: #667eea;
            --bg-gradient-end: #764ba2;
            --card-bg: #ffffff;
            --card-hover-bg: #f9fafb;
            --text-primary: #333333;
            --text-secondary: #6b7280;
            --text-tertiary: #9ca3af;
            --text-light: #ffffff;
            --border-color: #e5e7eb;
            --input-border: #d1d5db;
            --preview-bg: #f3f4f6;
            --dialog-bg: #ffffff;
            --shadow-sm: rgba(0,0,0,0.1);
            --shadow-md: rgba(0,0,0,0.15);
          }

          [data-theme="dark"] {
            --bg-gradient-start: #1e1b4b;
            --bg-gradient-end: #312e81;
            --card-bg: #1f2937;
            --card-hover-bg: #374151;
            --text-primary: #f9fafb;
            --text-secondary: #d1d5db;
            --text-tertiary: #9ca3af;
            --text-light: #ffffff;
            --border-color: #374151;
            --input-border: #4b5563;
            --preview-bg: #374151;
            --dialog-bg: #1f2937;
            --shadow-sm: rgba(0,0,0,0.3);
            --shadow-md: rgba(0,0,0,0.5);
          }

          * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
          }

          body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
            background: linear-gradient(135deg, var(--bg-gradient-start) 0%, var(--bg-gradient-end) 100%);
            color: var(--text-primary);
            min-height: 100vh;
            padding: 2rem;
            transition: background 0.3s ease, color 0.3s ease;
          }

          .container {
            max-width: 1200px;
            margin: 0 auto;
          }

          .header {
            text-align: center;
            color: var(--text-light);
            margin-bottom: 3rem;
            position: relative;
          }

          .header h1 {
            font-size: 4rem;
            font-weight: 700;
            margin-bottom: 0.5rem;
            text-shadow: 2px 2px 4px rgba(0,0,0,0.2);
          }

          .header p {
            font-size: 1.25rem;
            opacity: 0.9;
          }

          .theme-toggle {
            position: absolute;
            top: 0;
            right: 0;
            background: var(--card-bg);
            border: none;
            border-radius: 50%;
            width: 50px;
            height: 50px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.5rem;
            box-shadow: 0 4px 6px var(--shadow-sm);
            transition: transform 0.2s ease, box-shadow 0.2s ease;
          }

          .theme-toggle:hover {
            transform: scale(1.1);
            box-shadow: 0 6px 12px var(--shadow-md);
          }

          .routes-grid {
            display: flex;
            flex-direction: column;
            gap: 1rem;
            margin-bottom: 2rem;
          }

          .route-card {
            background: var(--card-bg);
            border-radius: 12px;
            padding: 1.5rem;
            box-shadow: 0 4px 6px var(--shadow-sm), 0 1px 3px var(--shadow-sm);
            transition: transform 0.2s ease, box-shadow 0.2s ease, background 0.3s ease;
          }

          .route-card:hover {
            transform: translateX(4px);
            box-shadow: 0 12px 20px var(--shadow-md), 0 2px 4px var(--shadow-sm);
          }

          .route-card.clickable {
            cursor: pointer;
          }

          .route-card.clickable:hover {
            background: var(--card-hover-bg);
          }

          .method {
            display: inline-block;
            padding: 0.35rem 0.75rem;
            border-radius: 6px;
            font-weight: 600;
            font-size: 0.875rem;
            text-transform: uppercase;
            letter-spacing: 0.5px;
            margin-bottom: 0.75rem;
          }

          .method-GET { background: #10b981; color: white; }
          .method-POST { background: #3b82f6; color: white; }
          .method-PUT { background: #f59e0b; color: white; }
          .method-DELETE { background: #ef4444; color: white; }
          .method-PATCH { background: #8b5cf6; color: white; }
          .method-HEAD { background: #6b7280; color: white; }
          .method-OPTIONS { background: #14b8a6; color: white; }
          .method-ANY { background: #ec4899; color: white; }

          .path {
            font-family: 'Courier New', Courier, monospace;
            font-size: 1rem;
            color: var(--text-primary);
            margin-bottom: 0.75rem;
            word-break: break-all;
            line-height: 1.6;
          }

          .docs {
            color: var(--text-secondary);
            font-size: 0.9rem;
            line-height: 1.6;
            border-top: 1px solid var(--border-color);
            padding-top: 0.75rem;
            margin-top: 0.75rem;
          }

          .no-docs {
            color: var(--text-tertiary);
            font-style: italic;
          }

          .footer {
            text-align: center;
            color: var(--text-light);
            margin-top: 3rem;
            opacity: 0.8;
          }

          dialog {
            border: none;
            border-radius: 12px;
            padding: 2rem;
            max-width: 500px;
            width: 90%;
            position: fixed;
            top: 50%;
            left: 50%;
            transform: translate(-50%, -50%);
            margin: 0;
            background: var(--dialog-bg);
            color: var(--text-primary);
            box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.2);
          }

          dialog::backdrop {
            background: rgba(0, 0, 0, 0.5);
          }

          dialog h2 {
            margin-bottom: 1.5rem;
            color: var(--text-primary);
          }

          .dialog-form {
            display: flex;
            flex-direction: column;
            gap: 1rem;
          }

          .form-group {
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
          }

          .form-group label {
            font-weight: 600;
            color: var(--text-secondary);
            font-size: 0.875rem;
          }

          .form-group input {
            padding: 0.5rem;
            border: 1px solid var(--input-border);
            border-radius: 6px;
            font-size: 1rem;
            background: var(--card-bg);
            color: var(--text-primary);
          }

          .form-group input:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
          }

          .dialog-buttons {
            display: flex;
            gap: 0.75rem;
            margin-top: 1rem;
          }

          .dialog-buttons button {
            flex: 1;
            padding: 0.75rem;
            border: none;
            border-radius: 6px;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.2s ease;
          }

          .btn-primary {
            background: #667eea;
            color: white;
          }

          .btn-primary:hover {
            background: #5568d3;
          }

          .btn-secondary {
            background: #e5e7eb;
            color: #374151;
          }

          .btn-secondary:hover {
            background: #d1d5db;
          }

          .preview-url {
            margin-top: 1rem;
            padding: 0.75rem;
            background: var(--preview-bg);
            border-radius: 6px;
            font-family: 'Courier New', Courier, monospace;
            font-size: 0.875rem;
            word-break: break-all;
            color: var(--text-primary);
          }

          .preview-url a {
            color: #667eea;
            text-decoration: none;
          }

          .preview-url a:hover {
            text-decoration: underline;
          }

          .dialog-buttons button:disabled {
            opacity: 0.5;
            cursor: not-allowed;
          }

          .btn-copy {
            background: #10b981;
            color: white;
          }

          .btn-copy:hover:not(:disabled) {
            background: #059669;
          }
        """),
      ),
      body(
        div(`class` := "container")(
          div(`class` := "header")(
            button(
              `class` := "theme-toggle",
              id      := "themeToggle",
              Dom.attr("onclick")("toggleTheme()"),
              Dom.attr("aria-label")("Toggle theme"),
            )(
              span(id := "themeIcon")("🌙"),
            ),
            h1("404"),
            p("The route you're looking for doesn't exist. Here are the available routes:"),
          ),
          div(`class` := "routes-grid")(
            sortedPatterns.zipWithIndex.map { case (pattern, idx) =>
              val pathStr = pattern.pathCodec.render

              div(
                `class` := "route-card clickable",
                Dom.attr("onclick")(s"openDialog($idx)"),
              )(
                span(`class` := s"method method-${pattern.method.asInstanceOf[Product].productPrefix}")(
                  pattern.method.render,
                ),
                div(`class` := "path")(
                  pathStr,
                ),
                if (pattern.doc != Doc.empty) {
                  div(`class` := "docs")(
                    pattern.doc.toPlaintext(),
                  )
                } else {
                  div(`class` := "docs no-docs")(
                    "No documentation available",
                  )
                },
              )
            },
          ),
        ),
        dialog(id := "paramDialog")(
          h2("Enter Parameters"),
          div(id := "dialogForm", `class` := "dialog-form"),
          div(id := "previewUrl", `class` := "preview-url"),
          div(`class` := "dialog-buttons")(
            button(`class` := "btn-secondary", Dom.attr("onclick")("closeDialog()"))("Cancel"),
            button(
              id      := "copyBtn",
              `class` := "btn-copy",
              Dom.attr("onclick")("copyToClipboard()"),
              Dom.attr("disabled")("true"),
            )("Copy URL"),
            button(
              id      := "goBtn",
              `class` := "btn-primary",
              Dom.attr("onclick")("navigateToUrl()"),
              Dom.attr("disabled")("true"),
            )("Go"),
          ),
        ),
      ),
    )
  }

  private def routesToJson(patterns: Seq[RoutePattern[_]]): String = {
    val routesJson = patterns.map { pattern =>
      val pathStr    = pattern.pathCodec.render
      val methodStr  = pattern.method.render
      val params     = extractParams(pathStr)
      val paramsJson = params.map { case (name, paramType) =>
        s"""{"name":"$name","type":"$paramType"}"""
      }.mkString(",")
      s"""{"path":"$pathStr","method":"$methodStr","params":[$paramsJson]}"""
    }.mkString(",")
    s"[$routesJson]"
  }

  private def extractParams(path: String): List[(String, String)] = {
    val paramPattern = "\\{([^}]+)}".r
    paramPattern
      .findAllMatchIn(path)
      .map { m =>
        val fullParam = m.group(1)
        // Try to extract type hint from path like {id} or just {name}
        val name      = fullParam

        // Infer type from parameter name
        val paramType =
          if (name.toLowerCase.contains("id") || name.toLowerCase == "int") "int"
          else if (name.toLowerCase.contains("long")) "long"
          else if (name.toLowerCase.contains("uuid")) "uuid"
          else "string"

        (name, paramType)
      }
      .toList
  }

}
