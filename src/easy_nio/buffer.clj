(ns easy-nio.buffer
  "Wrapper around java.nio.ByteBuffer.

  ByteBuffers are stateful and mutable. All functions that modify buffer
  state return the buffer itself to support threading via -> or as->.

  Terminology follows the java.nio convention:
    - capacity : total bytes the buffer can hold
    - limit    : the first byte that should not be read or written
    - position : the current read/write cursor
    - remaining: (- limit position)"
  (:import [java.nio ByteBuffer ByteOrder]))

(set! *warn-on-reflection* true)

;;; ============================================================
;;; Allocation
;;; ============================================================

(defn allocate
  "Allocates a heap ByteBuffer of `capacity` bytes if direct? is false.
   Otherwise, allocates a direct (off-heap) ByteBuffer of `capacity` bytes.
   The former should be used for general purpose buffers while the latter 
   should be prefverred for I/O operations with NIO channels."
  (^ByteBuffer [^long capacity]
   (allocate capacity false))
  (^ByteBuffer [^long capacity direct?]
   (if direct?
     (ByteBuffer/allocateDirect capacity)
     (ByteBuffer/allocate capacity))))

(defn wrap
  "Wraps an existing byte-array into a ByteBuffer.
   The buffer shares the backing array, mutations to one are visible
   in the other. Optionally accepts `offset` and `length` to wrap a
   sub-region."
  (^ByteBuffer [^bytes ba]
   (ByteBuffer/wrap ba))
  (^ByteBuffer [^bytes ba ^long offset ^long length]
   (ByteBuffer/wrap ba offset length)))

;;; ============================================================
;;; State inspection
;;; ============================================================

(defn capacity
  "Returns the total byte capacity of `buf`."
  [^ByteBuffer buf]
  (.capacity buf))

(defn limit
  "Returns the current limit of `buf`."
  [^ByteBuffer buf]
  (.limit buf))

(defn position
  "Returns the current position of `buf`."
  [^ByteBuffer buf]
  (.position buf))

(defn remaining
  "Returns the number of bytes between position and limit."
  [^ByteBuffer buf]
  (.remaining buf))

(defn has-remaining?
  "Returns true if there is at least one byte between position and limit."
  [^ByteBuffer buf]
  (.hasRemaining buf))

(defn direct?
  "Returns true if `buf` is a direct (off-heap) buffer."
  [^ByteBuffer buf]
  (.isDirect buf))

(defn read-only?
  "Returns true if `buf` is read-only."
  [^ByteBuffer buf]
  (.isReadOnly buf))

;;; ============================================================
;;; Position / limit manipulation
;;; ============================================================

(defn flip!
  "Switches `buf` from write mode to read mode.
   Sets limit to current position, then resets position to 0.
   Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.flip buf))

(defn clear!
  "Resets `buf` for writing from scratch.
   Sets position to 0 and limit to capacity. Does not zero out data.
   Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.clear buf))

(defn rewind!
  "Resets position to 0 without changing the limit.
   Useful for re-reading a buffer that has already been flipped.
   Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.rewind buf))

(defn compact!
  "Compacts `buf`. Copies unread bytes to the start, then positions
   after the last copied byte ready for more writes.
   Use instead of clear! when you want to preserve unread data.
   Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.compact buf))

(defn set-limit!
  "Sets the limit of `buf` to `n`. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long n]
  (.limit buf (int n)))

(defn set-position!
  "Sets the position of `buf` to `n`. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long n]
  (.position buf (int n)))

(defn mark!
  "Marks the current position for a later reset!. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.mark buf))

(defn reset-to-mark!
  "Resets position to the previously marked position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf]
  (.reset buf))

;;; ============================================================
;;; Byte order
;;; ============================================================

(def big-endian    ByteOrder/BIG_ENDIAN)
(def little-endian ByteOrder/LITTLE_ENDIAN)

(defn byte-order
  "Returns the current ByteOrder of `buf`."
  [^ByteBuffer buf]
  (.order buf))

(defn set-byte-order!
  "Sets the byte order of `buf` to `order` (use `big-endian` or
  `little-endian`). Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^ByteOrder order]
  (.order buf order))

;;; ============================================================
;;; Writing (put)
;;; ============================================================

(defn put-byte!
  "Writes a single byte `b` at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long b]
  (.put buf (byte b)))
 
(defn put-bytes!
  "Writes all bytes from byte-array `ba` into `buf` at the current
   position. Optionally accepts `offset` and `length` to write a
   sub-range of `ba`. Returns `buf`."
  (^ByteBuffer [^ByteBuffer buf ^bytes ba]
   (.put buf ba))
  (^ByteBuffer [^ByteBuffer buf ^bytes ba ^long offset ^long length]
   (.put buf ba (int offset) (int length))))
 
(defn put-buffer!
  "Writes all remaining bytes from `src` ByteBuffer into `buf`.
  Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^ByteBuffer src]
  (.put buf src))
 
(defn put-short!
  "Writes a 2-byte short at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long n]
  (.putShort buf (short n)))
 
(defn put-int!
  "Writes a 4-byte int at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long n]
  (.putInt buf (int n)))
 
(defn put-long!
  "Writes an 8-byte long at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^long n]
  (.putLong buf n))
 
(defn put-float!
  "Writes a 4-byte float at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^double n]
  (.putFloat buf (float n)))
 
(defn put-double!
  "Writes an 8-byte double at the current position. Returns `buf`."
  ^ByteBuffer [^ByteBuffer buf ^double n]
  (.putDouble buf n))

;;; ============================================================
;;; Reading (get)
;;; ============================================================

(defn get-byte!
  "Reads and returns a single byte at the current position,
   advancing position by 1."
  [^ByteBuffer buf]
  (.get buf))

(defn get-bytes!
  "Reads `n` bytes from `buf` into a new byte-array and returns it.
   Advances position by `n`."
  ^bytes [^ByteBuffer buf ^long n]
  (let [ba (byte-array n)]
    (.get buf ba)
    ba))

(defn get-short!
  "Reads and returns a short (2 bytes) at the current position."
  [^ByteBuffer buf]
  (.getShort buf))

(defn get-int!
  "Reads and returns an int (4 bytes) at the current position."
  [^ByteBuffer buf]
  (.getInt buf))

(defn get-long!
  "Reads and returns a long (8 bytes) at the current position."
  [^ByteBuffer buf]
  (.getLong buf))

(defn get-float!
  "Reads and returns a float (4 bytes) at the current position."
  [^ByteBuffer buf]
  (.getFloat buf))

(defn get-double!
  "Reads and returns a double (8 bytes) at the current position."
  [^ByteBuffer buf]
  (.getDouble buf))

;;; ============================================================
;;; Conversion
;;; ============================================================

(defn ->bytes
  "Returns a byte-array of the bytes between position and limit
   without advancing the position. Safe to call multiple times."
  ^bytes [^ByteBuffer buf]
  (let [n   (.remaining buf)
        ba  (byte-array n)
        dup (.duplicate buf)]
    (.get dup ba)
    ba))

(defn ->str
  "Decodes the bytes between position and limit as a UTF-8 string.
   Does not advance position."
  [^ByteBuffer buf]
  (String. (->bytes buf) "UTF-8"))

(defn slice
  "Returns a new ByteBuffer sharing the region from the current
   position to the limit of `buf`. Changes to the slice's content
   are reflected in the original."
  ^ByteBuffer [^ByteBuffer buf]
  (.slice buf))

(defn duplicate
  "Returns a new ByteBuffer sharing the same backing data but with
   independent position, limit, and mark. Useful for reading the same
   buffer concurrently without rewinding."
  ^ByteBuffer [^ByteBuffer buf]
  (.duplicate buf))

(defn as-read-only
  "Returns a read-only view of `buf`. Writes to the returned buffer
   throw ReadOnlyBufferException."
  ^ByteBuffer [^ByteBuffer buf]
  (.asReadOnlyBuffer buf))

;;; ============================================================
;;; Convenience constructors
;;; ============================================================

(defn from-string
  "Allocates a direct ByteBuffer containing the UTF-8 bytes of `s`,
   ready for reading (already flipped)."
  ^ByteBuffer [^String s]
  (let [ba  (.getBytes s "UTF-8")
        buf (allocate (count ba) true)]
    (put-bytes! buf ba)
    (flip! buf)))

(defn from-bytes
  "Allocates a direct ByteBuffer containing `ba`, ready for reading."
  ^ByteBuffer [^bytes ba]
  (doto (allocate (count ba) true)
    (put-bytes! ba)
    (flip!)))
