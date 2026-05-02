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

;;; ============================================================
;;; Selection
;;; ============================================================

(defn select!
  "Selects a set of keys whose corresponding channels are 
   ready for I/O operations. Blocks until at least 
   one channel is ready for an operation it is registered for."
  [^Selector sel]
  (.select sel))

(defn select-timeout!
  "Like select!, but blocks for at most `timeout-ms` milliseconds.
   Returns the number of keys  whose ready-operation sets were updated."
  [^Selector sel ^long timeout-ms]
  (.select sel timeout-ms))

(defn select-now!
  "Non-blocking select. Returns immediately with the number of keys
   whose ready-op sets were updated since the last select call.
   Returns 0 if no channels are ready."
  [^Selector sel]
  (.selectNow sel))


;;; ============================================================
;;; Registration
;;; ============================================================

(defn register!
  "Registers channel `ch` with selector `sel` for 
   the given operations `ops` (a bitwise OR of
   op-read, op-write, op-accept, op-connect).
   Optionally accepts an `attachment` — any object — stored on the key
   and retrievable via (attachment key). Useful for associating
   per-channel state (e.g. a buffer, a handler, a connection id).
   Returns the resulting SelectionKey."
  (^SelectionKey [^AbstractSelectableChannel ch ^Selector sel ^long ops]
   (.register ch sel (int ops)))
  (^SelectionKey [^AbstractSelectableChannel ch ^Selector sel ^long ops attachment]
   (.register ch sel (int ops) attachment)))

(defn deregister!
  "Cancels `key`, removing its channel from the selector.
   The key becomes invalid immediately. The cancellation 
   takes effect on the next select! call."
  [^SelectionKey key]
  (.cancel key))

;;; ============================================================
;;; Key inspection
;;; ============================================================

(defn valid?
  "Returns true if `key` is still registered with its selector."
  [^SelectionKey key]
  (.isValid key))

(defn readable?
  "Returns true if `key`'s channel is ready for reading."
  [^SelectionKey key]
  (.isReadable key))

(defn writable?
  "Returns true if `key`'s channel is ready for writing."
  [^SelectionKey key]
  (.isWritable key))

(defn acceptable?
  "Returns true if `key`'s channel is ready to accept 
   a new socket connection."
  [^SelectionKey key]
  (.isAcceptable key))

(defn connectable?
  "Returns true if `key`'s channel is ready to complete a connection."
  [^SelectionKey key]
  (.isConnectable key))

(defn channel
  "Returns the channel associated with `key`."
  [^SelectionKey key]
  (.channel key))

(defn attachment
  "Returns the attachment associated with `key`, or nil if none."
  [^SelectionKey key]
  (.attachment key))

(defn set-interests!
  "Updates the interest ops of `key` to `ops`.
   Useful for switching a channel between read and write interest
   mid-lifecycle, e.g. after accumulating a full response to send.
   Returns `key`."
  ^SelectionKey [^SelectionKey key ^long ops]
  (.interestOps key (int ops))
  key)

(defn get-interests
  "Retrieves this key's interest set. It is guaranteed that 
   the returned set will only contain operation bits 
   that are valid for this key's channel. "
  [^SelectionKey key]
  (.interestOps key))

;; ============================================================
;;; Selected keys
;;; ============================================================

(defn selected-keys
  "Returns the mutable Set of SelectionKeys that are ready."
  [^Selector sel]
  (.selectedKeys sel))

(defn all-keys
  "Returns the set of all keys registered with `sel`, including those
   not yet ready."
  [^Selector sel]
  (.keys sel))

(defn clear-keys!
  "Removes all keys from the selected-key set after processing.
   Call this after all keys in `selected` have been handled."
  [^java.util.Set selected]
  (.clear selected))
