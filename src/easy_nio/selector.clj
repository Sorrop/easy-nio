(ns easy-nio.selector
  "Wrapper around java.nio.channels.Selector."
  (:import [java.nio.channels Selector SelectionKey]
           [java.nio.channels.spi AbstractSelectableChannel]))

(set! *warn-on-reflection* true)

(def op-read    SelectionKey/OP_READ)
(def op-write   SelectionKey/OP_WRITE)
(def op-accept  SelectionKey/OP_ACCEPT)
(def op-connect SelectionKey/OP_CONNECT)

;;; ============================================================
;;; Selector lifecycle
;;; ============================================================

(defn selector
  "Opens and returns a new Selector."
  ^Selector []
  (Selector/open))

(defn open?
  "Returns true if `sel` is open."
  [^Selector sel]
  (.isOpen sel))

(defn close!
  "Closes `sel`."
  [^Selector sel]
  (.close sel))

(defn wakeup!
  "Causes the first selection operation that has 
   not yet returned to return immediately. 
   Returns `sel`."
  ^Selector [^Selector sel]
  (.wakeup sel))
