(ns easy-nio.channel-test
  (:require [clojure.test :refer [deftest is testing]]
            [easy-nio.channel :as ch]
            [easy-nio.protocols :as proto]
            [easy-nio.buffer :as buf])
  (:import [java.net InetSocketAddress StandardSocketOptions]
           [java.nio.channels SocketChannel ServerSocketChannel DatagramChannel]))

;;; ============================================================
;;; Helpers
;;; ============================================================

(defn- with-server-channel
  "Opens a ServerSocketChannel bound to an ephemeral loopback port,
   calls (f server-ch bound-address), then closes the channel."
  [f]
  (let [server (ch/server-socket-channel)
        addr   (ch/inet-address "127.0.0.1" 0)]
    (ch/bind! server addr)
    (try
      (f server (ch/local-address server))
      (finally
        (proto/close! server)))))

;;; ============================================================
;;; inet-address
;;; ============================================================

(deftest test-inet-address
  (testing "host + port"
    (let [a (ch/inet-address "127.0.0.1" 8080)]
      (is (instance? InetSocketAddress a))
      (is (= 8080 (.getPort a)))
      (is (= "127.0.0.1" (.getHostString a)))))

  (testing "wildcard port only"
    (let [a (ch/inet-address 9090)]
      (is (instance? InetSocketAddress a))
      (is (= 9090 (.getPort a)))
      (is (.isAnyLocalAddress (.getAddress a))))))

;;; ============================================================
;;; Socket options
;;; ============================================================

(deftest test-set-get-option-socket-channel
  (let [ch (ch/socket-channel)]
    (try
      (ch/set-option! ch StandardSocketOptions/TCP_NODELAY true)
      (is (true? (ch/get-option ch StandardSocketOptions/TCP_NODELAY)))
      (ch/set-option! ch StandardSocketOptions/SO_RCVBUF (int 65536))
      (is (pos? (ch/get-option ch StandardSocketOptions/SO_RCVBUF)))
      (finally
        (proto/close! ch)))))

(deftest test-set-get-option-server-socket-channel
  (let [ch (ch/server-socket-channel)]
    (try
      (ch/set-option! ch StandardSocketOptions/SO_REUSEADDR true)
      (is (true? (ch/get-option ch StandardSocketOptions/SO_REUSEADDR)))
      (finally
        (proto/close! ch)))))

(deftest test-set-get-option-datagram-channel
  (let [ch (ch/datagram-channel)]
    (try
      (ch/set-option! ch StandardSocketOptions/SO_BROADCAST true)
      (is (true? (ch/get-option ch StandardSocketOptions/SO_BROADCAST)))
      (finally
        (proto/close! ch)))))

;;; ============================================================
;;; SocketChannel
;;; ============================================================

(deftest test-socket-channel-open
  (let [sc (ch/socket-channel)]
    (try
      (is (instance? SocketChannel sc))
      (is (proto/open? sc))
      (is (ch/blocking? sc))
      (finally
        (proto/close! sc)))))

(deftest test-socket-channel-connected-arity
  (with-server-channel
    (fn [_server bound-addr]
      (let [client (ch/socket-channel bound-addr)]
        (try
          (is (proto/open? client))
          (is (ch/connected? client))
          (is (some? (ch/remote-address client)))
          (finally
            (proto/close! client)))))))

(deftest test-socket-channel-close
  (let [sc (ch/socket-channel)]
    (proto/close! sc)
    (is (not (proto/open? sc)))))

(deftest test-configure-blocking
  (let [sc (ch/socket-channel)]
    (try
      (ch/configure-blocking! sc false)
      (is (not (ch/blocking? sc)))
      (ch/configure-blocking! sc true)
      (is (ch/blocking? sc))
      (finally
        (proto/close! sc)))))

(deftest test-connect-blocking
  (with-server-channel
    (fn [_server bound-addr]
      (let [client (ch/socket-channel)]
        (try
          (ch/connect! client bound-addr)
          (is (ch/connected? client))
          (is (not (ch/connection-pending? client)))
          (finally
            (proto/close! client)))))))

(deftest test-connect-refused
  (let [client (ch/socket-channel)
        addr   (ch/inet-address "127.0.0.1" 1)]
    (try
      ;; port 1 should be refused — if not, the assertion is skipped
      (let [ex (try (ch/connect! client addr) nil (catch Exception e e))]
        (when ex
          (is (instance? java.io.IOException ex))))
      (finally
        (proto/close! client)))))

(deftest test-non-blocking-connect
  (with-server-channel
    (fn [_server bound-addr]
      (let [client (ch/socket-channel)]
        (try
          (ch/configure-blocking! client false)
          (let [immediate? (ch/connect! client bound-addr)]
            (when-not immediate?
              ;; spin with a bounded retry — loopback connects fast
              (loop [attempts 0]
                (when (and (not (ch/finish-connect! client))
                           (< attempts 1000))
                  (Thread/sleep 1)
                  (recur (inc attempts))))))
          (is (ch/connected? client))
          (finally
            (proto/close! client)))))))

(deftest test-finish-connect-not-yet-connected
  (let [client (ch/socket-channel)]
    (try
      (ch/configure-blocking! client false)
      (is (thrown? java.nio.channels.NoConnectionPendingException
                   (ch/finish-connect! client)))
      (finally
        (proto/close! client)))))

(deftest test-remote-address
  (with-server-channel
    (fn [_server bound-addr]
      (let [client (ch/socket-channel)]
        (try
          (is (nil? (ch/remote-address client)))
          (ch/connect! client bound-addr)
          (is (some? (ch/remote-address client)))
          (finally
            (proto/close! client)))))))

(deftest test-local-address-socket-channel
  (with-server-channel
    (fn [_server bound-addr]
      (let [client (ch/socket-channel)]
        (try
          (ch/connect! client bound-addr)
          (is (some? (ch/local-address client)))
          (finally
            (proto/close! client)))))))

;;; ============================================================
;;; ServerSocketChannel
;;; ============================================================

(deftest test-server-socket-channel-open
  (let [server (ch/server-socket-channel)]
    (try
      (is (instance? ServerSocketChannel server))
      (is (proto/open? server))
      (is (ch/blocking? server))
      (finally
        (proto/close! server)))))

(deftest test-server-bind
  (let [server (ch/server-socket-channel)
        addr   (ch/inet-address "127.0.0.1" 0)]
    (try
      (ch/bind! server addr)
      (let [local (ch/local-address server)]
        (is (some? local))
        (is (pos? (.getPort ^InetSocketAddress local))))
      (finally
        (proto/close! server)))))

(deftest test-bind-with-backlog
  (let [server (ch/server-socket-channel)
        addr   (ch/inet-address "127.0.0.1" 0)]
    (try
      ;; backlog hint is OS-advisory — just verify it doesn't throw
      (is (some? (ch/bind! server addr 128)))
      (finally
        (proto/close! server)))))

(deftest test-bind-already-in-use
  (with-server-channel
    (fn [_server bound-addr]
      (let [server2 (ch/server-socket-channel)]
        (try
          (is (thrown? java.net.BindException
                       (ch/bind! server2 bound-addr)))
          (finally
            (proto/close! server2)))))))

(deftest test-accept-blocking
  (with-server-channel
    (fn [server bound-addr]
      (let [client (ch/socket-channel)
            ;; accept in a separate thread since it blocks
            accept-future (future (ch/accept! server))]
        (try
          (ch/connect! client bound-addr)
          (let [accepted @accept-future]
            (is (some? accepted))
            (is (instance? SocketChannel accepted))
            (is (proto/open? accepted))
            (proto/close! accepted))
          (finally
            (proto/close! client)))))))

(deftest test-accept-non-blocking-no-pending
  (let [server (ch/server-socket-channel)
        addr   (ch/inet-address "127.0.0.1" 0)]
    (try
      (ch/bind! server addr)
      (ch/configure-blocking! server false)
      ;; no client connected — should return nil in non-blocking mode
      (is (nil? (ch/accept! server)))
      (finally
        (proto/close! server)))))

;;; ============================================================
;;; Read / Write via StreamReadable / StreamWritable
;;; ============================================================

(deftest test-read-write-over-socket
  (with-server-channel
    (fn [server bound-addr]
      (let [client       (ch/socket-channel)
            accept-f     (future (ch/accept! server))
            _            (ch/connect! client bound-addr)
            accepted     @accept-f
            payload      (.getBytes "hello" "UTF-8")
            write-buf    (buf/from-bytes payload)
            read-buf     (buf/allocate 16)]
        (try
          (let [written (proto/write! client write-buf)]
            (is (= (count payload) written)))
          (let [read-n (proto/read! accepted read-buf)]
            (is (= (count payload) read-n))
            (buf/flip! read-buf)
            (is (= "hello" (buf/->str read-buf))))
          (finally
            (proto/close! accepted)
            (proto/close! client)))))))

(deftest test-read-returns-eof
  (with-server-channel
    (fn [server bound-addr]
      (let [client   (ch/socket-channel)
            accept-f (future (ch/accept! server))
            _        (ch/connect! client bound-addr)
            accepted @accept-f
            read-buf (buf/allocate 16)]
        (try
          ;; close the writer side, reader should get -1
          (proto/close! client)
          (is (= -1 (proto/read! accepted read-buf)))
          (finally
            (proto/close! accepted)))))))

;;; ============================================================
;;; DatagramChannel
;;; ============================================================

(deftest test-datagram-channel-open
  (let [dc (ch/datagram-channel)]
    (try
      (is (instance? DatagramChannel dc))
      (is (proto/open? dc))
      (finally
        (proto/close! dc)))))

(deftest test-datagram-bind
  (let [dc   (ch/datagram-channel)
        addr (ch/inet-address 0)]
    (try
      (ch/datagram-bind! dc addr)
      (let [local (ch/local-address dc)]
        (is (some? local))
        (is (pos? (.getPort ^InetSocketAddress local))))
      (finally
        (proto/close! dc)))))

(deftest test-datagram-bind-already-in-use
  (let [dc1  (ch/datagram-channel)
        addr (ch/inet-address 0)]
    (ch/datagram-bind! dc1 addr)
    (let [bound-addr (ch/local-address dc1)
          dc2        (ch/datagram-channel)]
      (try
        (is (thrown? java.net.BindException
                     (ch/datagram-bind! dc2 bound-addr)))
        (finally
          (proto/close! dc1)
          (proto/close! dc2))))))

(deftest test-datagram-send-receive
  (let [receiver (ch/datagram-channel)
        sender   (ch/datagram-channel)]
    (try
      (ch/datagram-bind! receiver (ch/inet-address 0))
      (let [recv-addr (ch/local-address receiver)
            payload   (.getBytes "ping" "UTF-8")
            send-buf  (buf/from-bytes payload)
            recv-buf  (buf/allocate 16)]
        (let [sent (proto/send! sender send-buf recv-addr)]
          (is (= (count payload) sent)))
        (let [src-addr (proto/receive! receiver recv-buf)]
          (is (some? src-addr))
          (buf/flip! recv-buf)
          (is (= "ping" (buf/->str recv-buf)))))
      (finally
        (proto/close! receiver)
        (proto/close! sender)))))

(deftest test-datagram-connect-disconnect
  (let [dc   (ch/datagram-channel)
        addr (ch/inet-address "127.0.0.1" 9999)]
    (try
      (ch/datagram-connect! dc addr)
      (is (ch/datagram-connected? dc))
      (ch/disconnect! dc)
      (is (not (ch/datagram-connected? dc)))
      (finally
        (proto/close! dc)))))

(deftest test-datagram-receive-non-blocking-no-data
  (let [dc (ch/datagram-channel)]
    (try
      (ch/datagram-bind! dc (ch/inet-address 0))
      (ch/configure-blocking! dc false)
      (let [recv-buf (buf/allocate 16)]
        ;; no sender — receive! returns nil in non-blocking mode
        (is (nil? (proto/receive! dc recv-buf))))
      (finally
        (proto/close! dc)))))


;;; ============================================================
;;; Shutdown
;;; ============================================================
 
(deftest test-shutdown-input
  (with-server-channel
    (fn [server bound-addr]
      (let [client   (ch/socket-channel)
            accept-f (future (ch/accept! server))
            _        (ch/connect! client bound-addr)
            accepted @accept-f
            read-buf (buf/allocate 16)]
        (try
          (ch/shutdown-input! accepted)
          ;; no data written — shutdown-input makes reads return -1 immediately
          (is (= -1 (proto/read! accepted read-buf)))
          (finally
            (proto/close! accepted)
            (proto/close! client)))))))
 
(deftest test-shutdown-output
  (with-server-channel
    (fn [server bound-addr]
      (let [client   (ch/socket-channel)
            accept-f (future (ch/accept! server))
            _        (ch/connect! client bound-addr)
            accepted @accept-f]
        (try
          (ch/shutdown-output! client)
          (let [ex (try (proto/write! client (buf/from-string "data"))
                        (catch Exception e e))]
            (is (some? ex))
            (is (instance? java.io.IOException ex)))
          (finally
            (proto/close! accepted)
            (proto/close! client)))))))
 
(deftest test-shutdown-not-yet-connected
  (let [client (ch/socket-channel)]
    (try
      (is (thrown? java.nio.channels.NotYetConnectedException
                   (ch/shutdown-input! client)))
      (is (thrown? java.nio.channels.NotYetConnectedException
                   (ch/shutdown-output! client)))
      (finally
        (proto/close! client)))))
 
