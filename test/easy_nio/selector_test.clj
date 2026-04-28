(ns easy-nio.selector-test
  (:require [clojure.test :refer [deftest is]]
            [easy-nio.selector :as sel]
            [easy-nio.channel :as ch]
            [easy-nio.protocols :as proto]
            [easy-nio.buffer :as buf]))

;;; ============================================================
;;; Lifecycle
;;; ============================================================

(deftest test-selector-open-close
  (let [s (sel/selector)]
    (is (sel/open? s))
    (sel/close! s)
    (is (not (sel/open? s)))))

(deftest test-wakeup
  (let [s (sel/selector)]
    (try
      ;; wakeup! causes the next select! to return immediately
      (sel/wakeup! s)
      (let [n (sel/select!  s)]
        (is (= 0 n)))
      (finally
        (sel/close! s)))))

;;; ============================================================
;;; Registration
;;; ============================================================

(deftest test-register-and-deregister
  (let [s  (sel/selector)
        sc (ch/socket-channel)]
    (try
      (ch/configure-blocking! sc false)
      (let [key (sel/register! sc s sel/op-connect)]
        (is (sel/valid? key))
        (is (= sc (sel/channel key)))
        (is (nil? (sel/attachment key)))
        (sel/deregister! key)
        ;; cancellation takes effect on next select call
        (sel/select-now! s)
        (is (not (sel/valid? key))))
      (finally
        (proto/close! sc)
        (sel/close! s)))))

(deftest test-register-with-attachment
  (let [s   (sel/selector)
        sc  (ch/socket-channel)
        att {:id 42 :buf (buf/allocate 1024)}]
    (try
      (ch/configure-blocking! sc false)
      (let [key (sel/register! sc s sel/op-read att)]
        (is (= att (sel/attachment key))))
      (finally
        (proto/close! sc)
        (sel/close! s)))))

(deftest test-all-keys
  (let [s   (sel/selector)
        sc1 (ch/socket-channel)
        sc2 (ch/socket-channel)]
    (try
      (ch/configure-blocking! sc1 false)
      (ch/configure-blocking! sc2 false)
      (sel/register! sc1 s sel/op-read)
      (sel/register! sc2 s sel/op-read)
      (is (= 2 (count (sel/all-keys s))))
      (finally
        (proto/close! sc1)
        (proto/close! sc2)
        (sel/close! s)))))

;;; ============================================================
;;; Interest ops
;;; ============================================================

(deftest test-set-get-interests
  (let [s  (sel/selector)
        sc (ch/socket-channel)]
    (try
      (ch/configure-blocking! sc false)
      (let [key (sel/register! sc s sel/op-read)]
        (is (= sel/op-read (sel/get-interests key)))
        (sel/set-interests! key sel/op-write)
        (is (= sel/op-write (sel/get-interests key))))
      (finally
        (proto/close! sc)
        (sel/close! s)))))

;;; ============================================================
;;; Selection and readiness
;;; ============================================================

(deftest test-select-now-empty
  (let [s (sel/selector)]
    (try
      (is (= 0 (sel/select-now! s)))
      (finally
        (sel/close! s)))))

(deftest test-select-timeout-no-channels
  (let [s (sel/selector)]
    (try
      (let [start (System/currentTimeMillis)
            n     (sel/select-timeout! s 50)]
        (is (= 0 n))
        (is (>= (- (System/currentTimeMillis) start) 50)))
      (finally
        (sel/close! s)))))

(deftest test-op-read-ready
  (let [server (ch/server-socket-channel)
        s      (sel/selector)]
    (try
      (ch/bind! server (ch/inet-address "127.0.0.1" 0))
      (let [bound-addr (ch/local-address server)
            client     (ch/socket-channel)
            accept-f   (future (ch/accept! server))
            _          (ch/connect! client bound-addr)
            accepted   @accept-f]
        (try
          (ch/configure-blocking! accepted false)
          (let [key (sel/register! accepted s sel/op-read)
                ;; write from client so accepted side becomes readable
                _   (proto/write! client (buf/from-string "hello"))
                n   (sel/select-timeout! s 50)]
            (is (= n 1))
            (is (sel/readable? key)))
          (finally
            (proto/close! client)
            (proto/close! accepted))))
      (finally
        (proto/close! server)
        (sel/close! s)))))

(deftest test-op-accept-ready
  (let [server (ch/server-socket-channel)
        s      (sel/selector)]
    (try
      (ch/bind! server (ch/inet-address "127.0.0.1" 0))
      (ch/configure-blocking! server false)
      (let [key    (sel/register! server s sel/op-accept)
            client (ch/socket-channel)]
        (try
          (ch/connect! client (ch/local-address server))
          (sel/select! s)
          (is (contains? (sel/selected-keys s) key))
          (is (sel/acceptable? key))
          (finally
            (proto/close! client))))
      (finally
        (proto/close! server)
        (sel/close! s)))))
