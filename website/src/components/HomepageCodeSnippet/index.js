import React, { useState, useRef, useEffect } from 'react';
import Highlight, { defaultProps } from 'prism-react-renderer';
import dracula from 'prism-react-renderer/themes/dracula';
import useIsBrowser from '@docusaurus/useIsBrowser';
import Link from '@docusaurus/Link';
import clsx from 'clsx';
import { FaCopy, FaCheck } from 'react-icons/fa6';
import styles from './styles.module.css';

const TABS = [
  {
    label: 'Create an HTTP Server',
    code: `import zio._
import zio.http._

object GreetingServer extends ZIOAppDefault {

  val routes =
    Routes(
      Method.GET / "greet" -> handler { (req: Request) =>
        val name = req.queryParamOrElse("name", "World")
        Response.text(s"Hello, $name!")
      },
      Method.GET / "health" -> handler {
        Response.ok
      }
    )

  def run = Server.serve(routes).provide(Server.default)
}`,
  },
  {
    label: 'Define Endpoints',
    code: `import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.codec.Doc

val greetEndpoint =
  Endpoint(GET / "greet" ?? Doc.p("Greet an individual by name"))
    .query(
      HttpCodec.query[String]("name") ??
        Doc.p("The name of the person to greet")
    )
    .out[String]

val greetRoute: Route[Any, Nothing] =
  greetEndpoint.implementHandler(
    handler((name: String) => s"Hello, $name!")
  )

def run = Server
  .serve(Routes(greetRoute))
  .provide(Server.default)`,
  },
  {
    label: 'Add Middleware',
    code: `import zio._
import zio.http._
import zio.http.Middleware

val routes =
  Routes(
    Method.GET / "api" / "data" -> handler {
      Response.json("""{"status":"ok"}""")
    }
  )

// Compose middleware with the @@ operator
val app = routes
  @@ Middleware.timeout(10.seconds)
  @@ Middleware.requestLogging()
  @@ Middleware.cors()

def run = Server.serve(app).provide(Server.default)`,
  },
  {
    label: 'HTTP Client',
    code: `import zio._
import zio.http._

object ApiClient extends ZIOAppDefault {

  val program: ZIO[Client, Throwable, Unit] =
    for {
      url <- ZIO.fromEither(
               URL.decode("https://api.example.com/users")
             )
      response <- ZClient.batched(
                    Request
                      .get(url)
                      .addHeader(Header.Accept.json)
                  )
      body     <- response.bodyAs[String]
      _        <- ZIO.debug(s"Response: $body")
    } yield ()

  def run = program.provide(Client.default)
}`,
  },
  {
    label: 'WebSocket',
    code: `import zio._
import zio.http._
import zio.http.ChannelEvent._

val wsRoute =
  Method.GET / "ws" -> handler {
    Handler.webSocket { channel =>
      channel.receiveAll {
        case Read(WebSocketFrame.Text(msg)) =>
          channel.send(
            Read(WebSocketFrame.text(s"Echo: $msg"))
          )
        case ExceptionCaught(cause) =>
          ZIO.logError(s"Channel error: \${cause.getMessage}")
        case _ =>
          ZIO.unit
      }
    }.toResponse
  }

def run = Server
  .serve(Routes(wsRoute))
  .provide(Server.default)`,
  },
];

export default function HomepageCodeSnippet() {
  const [activeTab, setActiveTab] = useState(0);
  const [copied, setCopied] = useState(false);
  const isBrowser = useIsBrowser();
  const timeoutRef = useRef(null);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (timeoutRef.current) {
        clearTimeout(timeoutRef.current);
      }
    };
  }, []);

  const handleTabClick = (idx) => {
    setActiveTab(idx);
    setCopied(false);
    // Clear any pending timeout when switching tabs
    if (timeoutRef.current) {
      clearTimeout(timeoutRef.current);
    }
  };

  const handleCopy = () => {
    if (!isBrowser) return;

    const textToCopy = TABS[activeTab].code.trim();

    try {
      if (!navigator.clipboard?.writeText) {
        throw new Error('Clipboard API is not available');
      }

      navigator.clipboard
        .writeText(textToCopy)
        .then(() => {
          setCopied(true);
          // Clear any existing timeout
          if (timeoutRef.current) {
            clearTimeout(timeoutRef.current);
          }
          // Set new timeout
          timeoutRef.current = setTimeout(() => {
            setCopied(false);
            timeoutRef.current = null;
          }, 2000);
        })
        .catch((err) => {
          console.error('Failed to copy:', err);
        });
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  return (
    <section className={styles.codeSnippetSection}>
      <div className={styles.innerContainer}>
        {/* Left Column */}
        <div className={styles.leftColumn}>
          <h2>ZIO HTTP in Action</h2>
          <p>
            Explore idiomatic Scala patterns for building high-performance,
            type-safe HTTP servers and clients. From routing and endpoints to
            middleware and real-time communication.
          </p>
          <div>
            <Link
              className="button button--outline button--lg"
              to="/">
              Explore the Docs
            </Link>
          </div>
        </div>

        {/* Right Column */}
        <div className={styles.rightColumn}>
          <div className={styles.codePanel}>
            {/* Tab Bar */}
            <div className={styles.tabBar} role="tablist">
              {TABS.map((tab, idx) => (
                <button
                  key={idx}
                  id={`tab-${idx}`}
                  className={clsx(
                    styles.tab,
                    activeTab === idx && styles.tabActive
                  )}
                  onClick={() => handleTabClick(idx)}
                  aria-selected={activeTab === idx}
                  aria-controls={`tabpanel-${idx}`}
                  type="button"
                  role="tab">
                  {tab.label}
                </button>
              ))}
            </div>

            {/* Code Area */}
            <div
              id={`tabpanel-${activeTab}`}
              className={styles.codeArea}
              role="tabpanel"
              aria-labelledby={`tab-${activeTab}`}>
              <Highlight
                key={activeTab}
                {...defaultProps}
                theme={dracula}
                code={TABS[activeTab].code.trim()}
                language="scala">
                {({
                  className,
                  style,
                  tokens,
                  getLineProps,
                  getTokenProps,
                }) => (
                  <pre
                    className={`${className} ${styles.pre}`}
                    style={style}>
                    <code>
                      {tokens.map((line, i) => (
                        <div
                          key={i}
                          {...getLineProps({ line, key: i })}
                          className={styles.codeLine}>
                          <span className={styles.lineNumber}>{i + 1}</span>
                          <span className={styles.lineContent}>
                            {line.map((token, key) => (
                              <span
                                key={key}
                                {...getTokenProps({ token, key })}
                              />
                            ))}
                          </span>
                        </div>
                      ))}
                    </code>
                  </pre>
                )}
              </Highlight>
            </div>

            {/* Toolbar */}
            <div className={styles.toolbar}>
              <span className={styles.langBadge}>Scala</span>
              {isBrowser && (
                <button
                  type="button"
                  className={clsx(
                    styles.copyButton,
                    copied && styles.copyButtonCopied
                  )}
                  onClick={handleCopy}
                  aria-label={copied ? 'Copied!' : 'Copy code'}
                  title={copied ? 'Copied!' : 'Copy to clipboard'}>
                  {copied ? <FaCheck size={14} /> : <FaCopy size={14} />}
                  <span>{copied ? 'Copied!' : 'Copy'}</span>
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
