(ns easy-nio.protocols)

(defprotocol IReadable
  (read! [this buf]))

(defprotocol IWritable
  (write! [this buf]))
