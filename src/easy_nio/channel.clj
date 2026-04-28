(ns easy-nio.channel
  "Wrapper around java.nio.channels for non-blocking network I/O.

  Channels are stateful. Functions that mutate channel state use the !
  suffix convention."
  (:require [easy-nio.protocols :as proto])
  (:import [java.net InetSocketAddress SocketAddress]
           [java.nio ByteBuffer]
           [java.nio.channels
            SocketChannel ServerSocketChannel DatagramChannel
            NetworkChannel]
           [java.nio.channels.spi AbstractSelectableChannel]))

(set! *warn-on-reflection* true)

;;; ============================================================
;;; Shared helpers
;;; ============================================================

(defn inet-address
  "Constructs an InetSocketAddress from `host` (String) and `port` (int).
   With arity-1, constructs a wildcard address bound to `port`."
  (^InetSocketAddress [^long port]
   (InetSocketAddress. (int port)))
  (^InetSocketAddress [^String host ^long port]
   (InetSocketAddress. host (int port))))

(defn configure-blocking!
  "Sets the blocking mode of `ch`. Pass false for non-blocking I/O.
   Returns `ch`."
  [^AbstractSelectableChannel ch ^Boolean blocking]
  (.configureBlocking ch blocking)
  ch)

(defn blocking?
  "Returns true if `ch` is in blocking mode."
  [^AbstractSelectableChannel ch]
  (.isBlocking ch))

(defn local-address
  "Returns the local SocketAddress bound to `ch`, or nil if unbound."
  ^SocketAddress [^NetworkChannel ch]
  (.getLocalAddress ch))

(defn set-option!
  "Sets a socket option on `ch`. `option` should be a constant from
   java.net.StandardSocketOptions e.g. StandardSocketOptions/TCP_NODELAY.
   Returns `ch`."
  [^NetworkChannel ch option value]
  (.setOption ch option value)
  ch)

(defn get-option
  "Gets the current value of a socket option on `ch`."
  [^NetworkChannel ch option]
  (.getOption ch option))

;;; ============================================================
;;; Protocol implementations
;;; ============================================================

(extend-type SocketChannel
  proto/NIOChannel
  (open? [ch] (.isOpen ch))
  (close! [ch] (.close ch))

  proto/StreamReadable
  (read! [ch ^ByteBuffer buf]
    (.read ch buf))

  proto/StreamWritable
  (write! [ch ^ByteBuffer buf]
    (.write ch buf)))

(extend-type ServerSocketChannel
  proto/NIOChannel
  (open? [ch] (.isOpen ch))
  (close! [ch] (.close ch)))

(extend-type DatagramChannel
  proto/NIOChannel
  (open? [ch] (.isOpen ch))
  (close! [ch] (.close ch))

  proto/DatagramIO
  (send! [ch ^ByteBuffer buf ^SocketAddress addr]
    (.send ch buf addr))
  (receive! [ch ^ByteBuffer buf]
    (.receive ch buf)))

;;; ============================================================
;;; SocketChannel
;;; ============================================================

(defn socket-channel
  "Opens and returns a new SocketChannel in blocking mode.
   With arity-1, opens and immediately connects to `addr`."
  (^SocketChannel []
   (SocketChannel/open))

  (^SocketChannel [^InetSocketAddress addr]
   (SocketChannel/open addr)))

(defn connect!
  "Connects `ch` to `addr` (an InetSocketAddress).

   In blocking mode: blocks until the connection is established and
   returns true.

   In non-blocking mode: initiates the connection and returns false if
   it cannot be completed immediately. Call finish-connect! later to
   complete it (typically after a Selector signals OP_CONNECT)."
  [^SocketChannel ch ^InetSocketAddress addr]
  (.connect ch addr))

(defn finish-connect!
  "Completes a non-blocking connection initiated by connect!.
   Returns true if the connection is now established, false if it is
   still in progress."
  [^SocketChannel ch]
  (.finishConnect ch))

(defn connected?
  "Returns true if `ch` has an established connection."
  [^SocketChannel ch]
  (.isConnected ch))

(defn connection-pending?
  "Returns true if a non-blocking connect! has been initiated on `ch`
   but not yet completed via finish-connect!."
  [^SocketChannel ch]
  (.isConnectionPending ch))

(defn remote-address
  "Returns the remote SocketAddress connected to `ch`, or nil if
   the channel is not connected."
  ^SocketAddress [^SocketChannel ch]
  (.getRemoteAddress ch))

(defn shutdown-input!
  "Shuts down the read half of `ch` without closing the channel.
   Subsequent reads will return -1 (EOF). The write half remains open.
   Returns `ch`.
   Throws NotYetConnectedException if the channel is not connected."
  ^SocketChannel [^SocketChannel ch]
  (.shutdownInput ch))
 
(defn shutdown-output!
  "Shuts down the write half of `ch` without closing the channel.
   Subsequent writes will throw ClosedChannelException. The read half
   remains open, allowing any in-flight data from the remote end to
   still be received. Returns `ch`.
   Throws NotYetConnectedException if the channel is not connected."
  ^SocketChannel [^SocketChannel ch]
  (.shutdownOutput ch))


;;; ============================================================
;;; ServerSocketChannel
;;; ============================================================

(defn server-socket-channel
  "Opens and returns a new ServerSocketChannel in blocking mode."
  ^ServerSocketChannel []
  (ServerSocketChannel/open))

(defn bind!
  "Binds `ch` to `addr` (an InetSocketAddress).

   For ServerSocketChannel, accepts an optional `backlog` — the maximum
   number of pending connections. Defaults to 0 (system default)."
  (^ServerSocketChannel [^ServerSocketChannel ch ^InetSocketAddress addr]
   (bind! ch addr 0))
  (^ServerSocketChannel [^ServerSocketChannel ch ^InetSocketAddress addr ^long backlog]   
   (.bind ch addr (int backlog))
   ch))

(defn accept!
  "Accepts an incoming connection on `ch`.

   In blocking mode: blocks until a connection arrives and returns the
   new SocketChannel.

   In non-blocking mode: returns the new SocketChannel immediately, or
   nil if no connection is pending."
  [^ServerSocketChannel ch]
  (.accept ch))

;;; ============================================================
;;; DatagramChannel
;;; ============================================================

(defn datagram-channel
  "Opens and returns a new DatagramChannel in blocking mode."
  ^DatagramChannel []
  (DatagramChannel/open))

(defn datagram-bind!
  "Binds `ch` (a DatagramChannel) to `addr` (an InetSocketAddress).
   Pass (inet-address port) with no host to bind to the wildcard address."
  ^DatagramChannel [^DatagramChannel ch ^InetSocketAddress addr]
  (.bind ch addr)
  ch)

(defn datagram-connect!
  "Connects `ch` (a DatagramChannel) to `addr`.
   This is optional and restricts the channel to only send/receive
   datagrams from that address. Not required for general UDP usage."
  ^DatagramChannel [^DatagramChannel ch ^InetSocketAddress addr]
  (.connect ch addr)
  ch)

(defn disconnect!
  "Disconnects `ch` (a DatagramChannel) from its connected address,
   if any. Safe to call on an unconnected channel. Returns `ch`."
  ^DatagramChannel [^DatagramChannel ch]
  (.disconnect ch)
  ch)

(defn datagram-connected?
  "Returns true if `ch` (a DatagramChannel) is connected to a specific
   remote address."
  [^DatagramChannel ch]
  (.isConnected ch))
