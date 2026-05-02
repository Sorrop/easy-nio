# Basic TCP server/client example

```clojure
(ns my-namespace.core
  (:require [easy-nio.channel :as ch]
            [easy-nio.protocols :as proto]
            [easy-nio.selector :as sel]
            [easy-nio.buffer :as buf]))


(defn run-server []
  (future
    (let [server (ch/server-socket-channel)
          addr   (ch/inet-address "127.0.0.1" 8080)]
      (ch/bind! server addr)
      (let [client (ch/accept! server)] ; blocks until a connection arrives
        (let [rb (buf/allocate 256)]
          (proto/read! client rb)
          (buf/flip! rb)
          (println "received:" (buf/->str rb)))
        (proto/close! client))
      (proto/close! server)
      (println "Server closed."))))

(defn non-blocking-server []
  (future
    (let [selector (sel/selector)
          server   (-> (ch/server-socket-channel)
                       (ch/configure-blocking! false))]
      (-> server
          (ch/bind! (ch/inet-address "127.0.0.1" 9090))
          (sel/register! selector sel/op-accept))

      (loop []
        (sel/select! selector)
        (let [keys (sel/selected-keys selector)]
          (doseq [key keys]
            (cond
              (sel/acceptable? key)
              (-> (ch/accept! server)
                  (ch/configure-blocking! false)
                  (sel/register! selector sel/op-read (buf/allocate 1024)))

              (sel/readable? key)
              (let [client (sel/channel key)
                    rb     (sel/attachment key)
                    n      (proto/read! client rb)]
                (if (= -1 n)
                  (do (sel/deregister! key)
                      (proto/close! client))
                  (do (buf/flip! rb)
                      (println "received:" (buf/->str rb))
                      (buf/clear! rb))))))
          (sel/clear-keys! keys))
        (recur)))))

;; client
(defn run-client [port]
  (let [client (ch/socket-channel (ch/inet-address "127.0.0.1" port))]
    (proto/write! client (buf/from-string "hellooooo"))
    (proto/close! client)))
```
