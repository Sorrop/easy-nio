(ns easy-nio.file
  "Wrapper around java.nio.channels.FileChannel."
  (:require [easy-nio.protocols :as proto])
  (:import [java.nio ByteBuffer MappedByteBuffer]
           [java.nio.channels
            FileChannel
            FileLock
            WritableByteChannel
            ReadableByteChannel
            FileChannel$MapMode]
           [java.nio.channels.spi AbstractInterruptibleChannel]
           [java.nio.file Path Paths OpenOption StandardOpenOption]))

(set! *warn-on-reflection* true)

;;; ============================================================
;;; Open option constants
;;; ============================================================

(def opt-read         StandardOpenOption/READ)
(def opt-write        StandardOpenOption/WRITE)
(def opt-append       StandardOpenOption/APPEND)
(def opt-create       StandardOpenOption/CREATE)
(def opt-create-new   StandardOpenOption/CREATE_NEW)
(def opt-truncate     StandardOpenOption/TRUNCATE_EXISTING)
(def opt-delete-on-close StandardOpenOption/DELETE_ON_CLOSE)
(def opt-sync         StandardOpenOption/SYNC)
(def opt-dsync        StandardOpenOption/DSYNC)

;;; ============================================================
;;; Protocol implementation
;;; ============================================================

(extend-type FileChannel
  proto/NIOChannel
  (open? [ch] (.isOpen ch))
  (close! [ch] (.close ch))

  proto/StreamReadable
  (read! [ch buf] (.read ch ^ByteBuffer buf))

  proto/StreamWritable
  (write! [ch buf] (.write ch ^ByteBuffer buf)))

;;; ============================================================
;;; Construction
;;; ============================================================

(defn path
  "Constructs a Path from `first` and optional `more` path segments."
  ^Path [^String first & more]
  (Paths/get first (into-array String more)))

(defn file-channel
  "Opens a FileChannel at `p` (a Path) with the given `opts`
   (StandardOpenOption constants). 
   
   Examples:
     (file-channel p opt-read)
     (file-channel p opt-write opt-create opt-truncate)"
  ^FileChannel [^Path p & opts]
  (FileChannel/open p (into-array OpenOption opts)))

;;; ============================================================
;;; Position
;;; ============================================================

(defn size
  "Returns the current size of the file in bytes."
  [^FileChannel ch]
  (.size ch))

(defn position
  "Returns the current file position."
  [^FileChannel ch]
  (.position ch))

(defn set-position!
  "Sets the file position to `pos`. Returns `ch`."
  ^FileChannel [^FileChannel ch ^long pos]
  (.position ch pos))

(defn truncate!
  "Truncates the file to `size` bytes. If the current position
   exceeds `size` it is set to `size`. Returns `ch`."
  ^FileChannel [^FileChannel ch ^long size]
  (.truncate ch size))

(defn force!
  "Forces any updates to the file to be written to storage.
   If `metadata?` is true, file metadata (timestamps etc.) is
   also flushed. Returns `ch`."
  [^FileChannel ch metadata?]
  (.force ch (boolean metadata?))
  ch)

;;; ============================================================
;;; Read / Write
;;; ============================================================

(defn read!
  "Reads bytes from `ch` into `buf` at the channel's current position.
   Advances the position. Returns bytes read, or -1 at end-of-file."
  [^FileChannel ch ^ByteBuffer buf]
  (.read ch buf))

(defn read-at!
  "Reads bytes from `ch` into `buf` starting at absolute file position
   `pos`. Does not affect the channel's current position."
  [^FileChannel ch ^ByteBuffer buf ^long pos]
  (.read ch buf pos))

(defn write!
  "Writes bytes from `buf` to `ch` at the channel's current position.
   Advances the position. Returns bytes written."
  [^FileChannel ch ^ByteBuffer buf]
  (.write ch buf))

(defn write-at!
  "Writes bytes from `buf` to `ch` at absolute file position `pos`.
   Does not affect the channel's current position."
  [^FileChannel ch ^ByteBuffer buf ^long pos]
  (.write ch buf pos))


;;; ============================================================
;;; Zero-copy transfer
;;; ============================================================

(defn transfer-to!
  "Transfers bytes from `ch` to `dst` (a WritableByteChannel).
   Reads `count` bytes starting at `pos` in `ch`.
   May transfer fewer bytes than requested. 
   Returns bytes transferred."
  [^FileChannel ch ^long pos ^long count ^WritableByteChannel dst]
  (.transferTo ch pos count dst))

(defn transfer-from!
  "Transfers bytes from `src` (a ReadableByteChannel) into `ch`.
   Writes up to `count` bytes starting at `pos` in `ch`.
   May transfer fewer bytes than requested. 
   Returns bytes transferred."
  [^FileChannel ch ^ReadableByteChannel src ^long pos ^long count]
  (.transferFrom ch src pos count))

;;; ============================================================
;;; Memory-mapped files
;;; ============================================================

(def ^:private map-mode-read-only  FileChannel$MapMode/READ_ONLY)
(def ^:private map-mode-read-write FileChannel$MapMode/READ_WRITE)
(def ^:private map-mode-private    FileChannel$MapMode/PRIVATE)

(defn mmap
  "Maps a region of `ch` into memory. `mode` is one of:
     map-mode-read-only  — shared read-only mapping
     map-mode-read-write — shared read-write mapping
     map-mode-private    — private (copy on write) mapping
   `pos` is the starting file position, `size` is the region size
   in bytes. Returns a MappedByteBuffer. All easy-nio.buffer
   functions apply to it directly."
  ^java.nio.MappedByteBuffer
  [^FileChannel ch mode ^long pos ^long size]
  (.map ch mode pos size))

(defn mmap-read
  "Maps `size` bytes of `ch` starting at `pos` as a read-only mapping."
  ^java.nio.MappedByteBuffer
  [^FileChannel ch ^long pos ^long size]
  (.map ch map-mode-read-only pos size))

(defn mmap-read-write
  "Maps `size` bytes of `ch` starting at `pos` as a read-write mapping."
  ^java.nio.MappedByteBuffer
  [^FileChannel ch ^long pos ^long size]
  (.map ch map-mode-read-write pos size))

(defn mmap-private
  "Maps `size` bytes of `ch` starting at `pos` as a copy-on-write
   mapping. Writes affect only the in-memory copy, not the file."
  ^java.nio.MappedByteBuffer
  [^FileChannel ch ^long pos ^long size]
  (.map ch map-mode-private pos size))

(defn loaded?
  "Returns true if the mapped region is likely to be resident in
   physical memory. This is only a hint and not guarantee."
  [^java.nio.MappedByteBuffer buf]
  (.isLoaded buf))

(defn load!
  "Loads the mapped region into physical memory where possible.
   Returns `buf`."
  ^java.nio.MappedByteBuffer
  [^java.nio.MappedByteBuffer buf]
  (.load buf))

(defn force-map!
  "Flushes changes in a read-write mapped region to storage.
   No-op for read-only or copy-on-write mappings. Returns `buf`."
  ^java.nio.MappedByteBuffer
  [^java.nio.MappedByteBuffer buf]
  (.force buf))

;;; ============================================================
;;; File locking
;;; ============================================================

(defn lock!
  "Acquires an exclusive lock on the entire file. Blocks until
   the lock is available. Returns a FileLock.
   Throws OverlappingFileLockException if the JVM already holds
   a lock that overlaps this region."
  ^FileLock [^FileChannel ch]
  (.lock ch))

(defn lock-region!
  "Acquires a lock on a region of the file. Blocks until available.
   `pos`    — starting byte position
   `size`   — number of bytes to lock
   `shared?` — true for a shared lock, false for exclusive."
  ^FileLock [^FileChannel ch ^long pos ^long size shared?]
  (.lock ch pos size (boolean shared?)))

(defn try-lock!
  "Attempts to acquire an exclusive lock on the entire file without
   blocking. Returns a FileLock if successful, nil if the lock is
   unavailable.
   Throws OverlappingFileLockException if the JVM already holds
   an overlapping lock."
  [^FileChannel ch]
  (.tryLock ch))

(defn try-lock-region!
  "Attempts to acquire a lock on a region of the file without blocking.
   `pos`     — starting byte position
   `size`    — number of bytes to lock
   `shared?` — true for a shared lock, false for exclusive.
   Returns a FileLock if successful, nil if unavailable."
  [^FileChannel ch ^long pos ^long size shared?]
  (.tryLock ch pos size (boolean shared?)))

(defn release-lock!
  "Releases `lock`. Returns nil."
  [^FileLock lock]
  (.release lock))

(defn lock-valid?
  "Returns true if `lock` is valid.
   A lock object remains valid until it is released 
   or the associated file channel is closed, 
   whichever comes first. "
  [^FileLock lock]
  (.isValid lock))

(defn shared-lock?
  "Returns true if `lock` is a shared."
  [^FileLock lock]
  (.isShared lock))
