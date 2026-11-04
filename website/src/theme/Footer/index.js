import React from 'react';
import Footer from '@theme-original/Footer';
import BrowserOnly from '@docusaurus/BrowserOnly';

// ByteBrain chat assistant, answering from the ZIO HTTP docs. The key is public
// by design: the chat server only accepts it from the https://ziohttp.com
// origin. Mounted here so it appears on every Docusaurus page (the generated
// /api pages do not render the Footer, so they have no widget).
const CHAT_API_KEY = 'caaf47a5-fb77-4013-bbe3-1b715d1b3be6';

const WELCOME_MESSAGES = [
  "Hi, I'm the ZIO HTTP assistant.",
  'Ask me anything about ZIO HTTP and the ZIO ecosystem.',
];

export default function FooterWrapper(props) {
  return (
    <>
      <Footer {...props} />
      <div className="zh-chat">
        <BrowserOnly>
          {() => {
            const {ChatApp} = require('@bytebrain.ai/bytebrain-ui');
            return (
              <ChatApp
                websocketHost="chat.zio.dev"
                websocketPort="80"
                websocketEndpoint="/chat"
                apikey={CHAT_API_KEY}
                title="ZIO HTTP Chat"
                buttonLabel="Ask the docs"
                placeholder="Ask a question about the docs"
                welcomeMessages={WELCOME_MESSAGES}
                askOnSelect
              />
            );
          }}
        </BrowserOnly>
      </div>
    </>
  );
}
