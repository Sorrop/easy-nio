(ns easy-nio.protocols)

(defprotocol NIOChannel
  "Common operations across all channel types."
  (open? [ch]
    "Returns true if the channel is open.")
  (close! [ch]
    "Closes the channel. Idempotent — safe to call on an already-closed channel."))

(defprotocol StreamReadable
  "Stream-oriented reading into a ByteBuffer.
   Implemented by SocketChannel.
   Returns the number of bytes read, or -1 on end-of-stream."
  (read! [ch buf]
    "Reads bytes from `ch` into `buf`. Returns bytes read, or -1 on EOF."))

(defprotocol StreamWritable
  "Stream-oriented writing from a ByteBuffer.
   Implemented by SocketChannel.
   Returns the number of bytes written."
  (write! [ch buf]
    "Writes bytes from `buf` to `ch`. Returns bytes written."))

(defprotocol DatagramIO
  "Connectionless datagram send/receive.
   Implemented by DatagramChannel."
  (send! [ch buf addr]
    "Sends the bytes remaining in `buf` to `addr` (a SocketAddress).
     Returns the number of bytes sent.")
  (receive! [ch buf]
    "Receives a datagram into `buf`.
     Returns the source SocketAddress, or nil if no datagram was available
     (non-blocking mode with empty receive queue)."))
