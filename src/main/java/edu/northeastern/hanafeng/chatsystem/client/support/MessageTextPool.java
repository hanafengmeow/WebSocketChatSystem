package edu.northeastern.hanafeng.chatsystem.client.support;

public final class MessageTextPool {
  private MessageTextPool() {}

  public static final String[] POOL = new String[]{
      "hi","hello","ok","good","thx","bye","yo","hey","kk","okok",
      "done","cool","wow","yay","ping","pong","foo","bar","baz","cs6650",
      "how are you doing today?",
      "nice day for distributed systems programming!",
      "WebSockets are awesome for real-time messaging.",
      "Go Huskies! NU Seattle rocks!",
      "Persistent connections reduce latency a lot.",
      "Gson makes JSON handling really convenient.",
      "Thread pools are essential for concurrency.",
      "Java NIO is powerful but tricky sometimes.",
      "Reconnection logic avoids dropped sessions.",
      "Message queues keep producers and consumers balanced.",
      "This is a longer test message meant to simulate more realistic chat content across multiple sentences. We want to ensure the system can handle larger payloads without breaking.",
      "Another medium-long message that will add some variety into the pool so that not every message looks identical in size. This helps measure performance under more diverse workloads.",
      "JSON validation must ensure that fields like userId, username, messageType, and timestamp are always valid according to the spec. This message is long to test boundary conditions.",

      "The deal includes the release of all hostages in exchange for Palestinian prisoners, as well as an initial pullback by the Israeli military in Gaza.",
      "This is a deliberately very long chat message designed to test the system’s ability to handle near-maximum payload size. By pushing closer to 500 characters, we ensure both serialization and transmission layers are exercised. If this goes through without issues, we know the server and client can handle stress at the upper message size boundary.",
      "Sometimes developers underestimate the importance of stress-testing with realistic but long messages. In real systems, messages may include logs, URLs, stack traces, or long chat paragraphs. This example is inserted here to simulate such scenarios and confirm the system behaves correctly under conditions close to maximum message length as required.",

      "Client retry logic uses exponential backoff up to five attempts.",
      "Reusing WebSocket connections avoids handshake overhead.",
      "Keep-alive frames detect broken connections early.",
      "Backpressure is handled by bounded queues.",
      "Circuit breaker avoids storm of failing retries.",
      "Sender threads pull from the queue without blocking the producer.",
      "We measure total time and messages per second for the report.",
      "Producer uses a single thread to satisfy assignment rule.",
      "Room mapping distributes messages across 20 rooms.",
      "Happy testing and benchmarking!"
  };
}
