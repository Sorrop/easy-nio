(ns easy-nio.file-test
  (:require [clojure.test :refer [deftest is]]
            [easy-nio.file :as f]
            [easy-nio.buffer :as buf]
            [easy-nio.protocols :as proto])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;;; ============================================================
;;; Helpers
;;; ============================================================

(defn- temp-file []
  (Files/createTempFile "easy-nio-test" ".tmp" (into-array FileAttribute [])))

(defn- with-temp-file [f]
  (let [p (temp-file)]
    (try
      (f p)
      (finally
        (Files/deleteIfExists p)))))

;;; ============================================================
;;; Construction
;;; ============================================================

(deftest test-path
  (let [p (f/path "/tmp" "foo" "bar")]
    (is (= "/tmp/foo/bar" (str p)))))

(deftest test-file-channel-open-close
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read)]
        (try
          (is (proto/open? fc))
          (finally
            (proto/close! fc)))))))

;;; ============================================================
;;; Read / Write
;;; ============================================================

(deftest test-write-and-read
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create)]
        (try
          (let [written (f/write! fc (buf/from-string "hello"))]
            (is (= 5 written)))
          (finally
            (proto/close! fc))))
      (let [fc  (f/file-channel p f/opt-read)
            rb  (buf/allocate 16)]
        (try
          (let [n (f/read! fc rb)]
            (is (= 5 n))
            (buf/flip! rb)
            (is (= "hello" (buf/->str rb))))
          (finally
            (proto/close! fc)))))))

(deftest test-read-at-write-at
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create)]
        (try
          (f/write-at! fc (buf/from-string "helloworld") 0)
          (finally
            (proto/close! fc))))
      (let [fc (f/file-channel p f/opt-read)
            rb (buf/allocate 5)]
        (try
          ;; read "world" starting at position 5
          (let [n (f/read-at! fc rb 5)]
            (is (= 5 n))
            (buf/flip! rb)
            (is (= "world" (buf/->str rb))))
          ;; position should be unaffected by read-at!
          (is (= 0 (f/position fc)))
          (finally
            (proto/close! fc)))))))

(deftest test-read-returns-eof
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read)
            rb (buf/allocate 16)]
        (try
          (is (= -1 (f/read! fc rb)))
          (finally
            (proto/close! fc)))))))

;;; ============================================================
;;; Position / size / truncate
;;; ============================================================

(deftest test-size
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "hello"))
          (is (= 5 (f/size fc)))
          (finally
            (proto/close! fc)))))))

(deftest test-position-and-seek
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "helloworld"))
          (finally
            (proto/close! fc))))
      (let [fc (f/file-channel p f/opt-read)
            rb (buf/allocate 5)]
        (try
          (is (= 0 (f/position fc)))
          (f/set-position! fc 5)
          (is (= 5 (f/position fc)))
          (f/read! fc rb)
          (buf/flip! rb)
          (is (= "world" (buf/->str rb)))
          (finally
            (proto/close! fc)))))))

(deftest test-truncate
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create f/opt-read)]
        (try
          (f/write! fc (buf/from-string "helloworld"))
          (f/truncate! fc 5)
          (is (= 5 (f/size fc)))
          (finally
            (proto/close! fc)))))))

;;; ============================================================
;;; Zero-copy transfer
;;; ============================================================

(deftest test-transfer-to
  (with-temp-file
    (fn [src-path]
      (with-temp-file
        (fn [dst-path]
          (let [src (f/file-channel src-path f/opt-write f/opt-create)]
            (try
              (f/write! src (buf/from-string "hello"))
              (finally
                (proto/close! src))))
          (let [src (f/file-channel src-path f/opt-read)
                dst (f/file-channel dst-path f/opt-write f/opt-create)]
            (try
              (let [transferred (f/transfer-to! src 0 5 dst)]
                (is (= 5 transferred)))
              (finally
                (proto/close! src)
                (proto/close! dst))))
          (let [fc (f/file-channel dst-path f/opt-read)
                rb (buf/allocate 8)]
            (try
              (f/read! fc rb)
              (buf/flip! rb)
              (is (= "hello" (buf/->str rb)))
              (finally
                (proto/close! fc)))))))))

;;; ============================================================
;;; Memory-mapped files
;;; ============================================================

(deftest test-mmap-read-write
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          ;; file must have content before mapping
          (f/write! fc (buf/from-string "hello"))
          (let [mb (f/mmap-read-write fc 0 5)]
            (is (= 5 (buf/remaining mb)))
            (is (= "hello" (buf/->str mb))))
          (finally
            (proto/close! fc)))))))

(deftest test-mmap-read-only
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "world"))
          (finally
            (proto/close! fc))))
      (let [fc (f/file-channel p f/opt-read)]
        (try
          (let [mb (f/mmap-read fc 0 5)]
            (is (= "world" (buf/->str mb)))
            (is (buf/read-only? mb)))
          (finally
            (proto/close! fc)))))))

(deftest test-mmap-private
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "hello"))
          (let [mb (f/mmap-private fc 0 5)]
            ;; write to cow mapping
            (buf/put-byte! mb (byte \H))
            (buf/rewind! mb)
            ;; in-memory copy reflects the write
            (is (= (byte \H) (buf/get-byte! mb))))
          (finally
            (proto/close! fc))))
      ;; original file must be unaffected
      (let [fc (f/file-channel p f/opt-read)
            rb (buf/allocate 5)]
        (try
          (f/read! fc rb)
          (buf/flip! rb)
          (is (= "hello" (buf/->str rb)))
          (finally
            (proto/close! fc)))))))

;;; ============================================================
;;; File locking
;;; ============================================================

(deftest test-lock-and-release
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          (let [lock (f/lock! fc)]
            (is (f/lock-valid? lock))
            (is (not (f/shared-lock? lock)))
            (f/release-lock! lock)
            (is (not (f/lock-valid? lock))))
          (finally
            (proto/close! fc)))))))

(deftest test-try-lock
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          (let [lock (f/try-lock! fc)]
            (is (some? lock))
            (is (f/lock-valid? lock))
            (f/release-lock! lock))
          (finally
            (proto/close! fc)))))))

(deftest test-lock-region
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "helloworld"))
          (let [lock (f/lock-region! fc 0 5 false)]
            (is (f/lock-valid? lock))
            (is (not (f/shared-lock? lock)))
            (f/release-lock! lock))
          (finally
            (proto/close! fc)))))))

(deftest test-try-lock-region
  (with-temp-file
    (fn [p]
      (let [fc (f/file-channel p f/opt-read f/opt-write f/opt-create)]
        (try
          (f/write! fc (buf/from-string "helloworld"))
          (let [lock (f/try-lock-region! fc 0 5 false)]
            (is (some? lock))
            (is (f/lock-valid? lock))
            (f/release-lock! lock))
          (finally
            (proto/close! fc)))))))
