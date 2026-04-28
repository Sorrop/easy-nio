(ns easy-nio.buffer-test
  (:require [clojure.test :refer [deftest is testing]]
            [easy-nio.buffer :as buf]))

;;; ============================================================
;;; Allocation
;;; ============================================================

(deftest test-allocate
  (testing "heap allocation"
    (let [b (buf/allocate 64)]
      (is (= 64 (buf/capacity b)))
      (is (= 0  (buf/position b)))
      (is (= 64 (buf/limit b)))
      (is (not (buf/direct? b)))))

  (testing "direct allocation"
    (let [b (buf/allocate 64 true)]
      (is (= 64 (buf/capacity b)))
      (is (buf/direct? b))))

  (testing "wrap byte-array"
    (let [ba (byte-array [1 2 3 4])
          b  (buf/wrap ba)]
      (is (= 4 (buf/capacity b)))
      (is (= 4 (buf/remaining b)))))

  (testing "wrap with offset and length"
    (let [ba (byte-array [0 1 2 3 4 5])
          b  (buf/wrap ba 2 3)]           ; bytes at indices 2,3,4
      (is (= 3 (buf/remaining b))))))

;;; ============================================================
;;; State transitions
;;; ============================================================

(deftest test-flip-clear-rewind
  (let [b (buf/allocate 8)]
    (testing "put advances position"
      (buf/put-int! b 42)
      (is (= 4 (buf/position b))))

    (testing "flip! sets limit to position and resets position"
      (buf/flip! b)
      (is (= 4 (buf/limit b)))
      (is (= 0 (buf/position b))))

    (testing "rewind! resets position without changing limit"
      (buf/get-int! b)                    ; advance position to 4
      (buf/rewind! b)
      (is (= 0 (buf/position b)))
      (is (= 4 (buf/limit b))))

    (testing "clear! resets both position and limit"
      (buf/clear! b)
      (is (= 0  (buf/position b)))
      (is (= 8  (buf/limit b))))))

(deftest test-compact
  (let [b (buf/allocate 8)]
    (buf/put-bytes! b (byte-array [10 20 30 40]))
    (buf/flip! b)
    (buf/get-byte! b)                     ; consume one byte
    (buf/compact! b)                      ; remaining [20 30 40] moved to front
    (is (= 3 (buf/position b)))
    (is (= 8 (buf/limit b)))))

(deftest test-mark-reset
  (let [b (buf/allocate 8)]
    (buf/put-bytes! b (byte-array [1 2 3 4]))
    (buf/flip! b)
    (buf/get-byte! b)                     ; position = 1
    (buf/mark! b)
    (buf/get-byte! b)                     ; position = 2
    (buf/get-byte! b)                     ; position = 3
    (buf/reset-to-mark! b)                ; back to mark
    (is (= 1 (buf/position b)))))

;;; ============================================================
;;; Set position / limit
;;; ============================================================

(deftest test-set-position-limit
  (let [b (buf/allocate 16)]
    (buf/set-limit! b 8)
    (is (= 8 (buf/limit b)))
    (buf/set-position! b 4)
    (is (= 4 (buf/position b)))
    (is (= 4 (buf/remaining b)))))

;;; ============================================================
;;; Primitive put / get round-trips
;;; ============================================================

(deftest test-byte-roundtrip
  (let [b (buf/allocate 1)]
    (buf/put-byte! b 0x7F)
    (buf/flip! b)
    (is (= (byte 0x7F) (buf/get-byte! b)))))

(deftest test-short-roundtrip
  (let [b (buf/allocate 2)]
    (buf/put-short! b 1234)
    (buf/flip! b)
    (is (= (short 1234) (buf/get-short! b)))))

(deftest test-int-roundtrip
  (let [b (buf/allocate 4)]
    (buf/put-int! b 100000)
    (buf/flip! b)
    (is (= (int 100000) (buf/get-int! b)))))

(deftest test-long-roundtrip
  (let [b (buf/allocate 8)]
    (buf/put-long! b Long/MAX_VALUE)
    (buf/flip! b)
    (is (= Long/MAX_VALUE (buf/get-long! b)))))

(deftest test-float-roundtrip
  (let [b (buf/allocate 4)]
    (buf/put-float! b 3.14)
    (buf/flip! b)
    (is (< (Math/abs (- 3.14 (buf/get-float! b))) 0.0001))))

(deftest test-double-roundtrip
  (let [b (buf/allocate 8)]
    (buf/put-double! b Math/PI)
    (buf/flip! b)
    (is (= Math/PI (buf/get-double! b)))))

(deftest test-bytes-roundtrip
  (let [ba (byte-array [1 2 3 4 5])
        b  (buf/allocate 5)]
    (buf/put-bytes! b ba)
    (buf/flip! b)
    (is (= (seq ba) (seq (buf/get-bytes! b 5))))))

(deftest test-put-buffer
  (let [src (buf/from-bytes (byte-array [9 8 7]))
        dst (buf/allocate 8)]
    (buf/put-buffer! dst src)
    (buf/flip! dst)
    (is (= [9 8 7] (vec (buf/get-bytes! dst 3))))))

;;; ============================================================
;;; Byte order
;;; ============================================================

(deftest test-big-endian-byte-order
  (let [b (buf/allocate 4)]
    (buf/set-byte-order! b buf/big-endian)
    (is (= buf/big-endian (buf/byte-order b)))
    (buf/put-int! b 1)
    (buf/flip! b)
    ;; big-endian: 0x00 0x00 0x00 0x01
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x01 (Byte/toUnsignedInt (buf/get-byte! b))))))

(deftest test-little-endian-byte-order
  (let [b (buf/allocate 4)]
    (buf/set-byte-order! b buf/little-endian)
    (is (= buf/little-endian (buf/byte-order b)))
    (buf/put-int! b 1)
    (buf/flip! b)
    ;; little-endian: 0x01 0x00 0x00 0x00
    (is (= 0x01 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))
    (is (= 0x00 (Byte/toUnsignedInt (buf/get-byte! b))))))

;;; ============================================================
;;; Conversion helpers
;;; ============================================================

(deftest test-->bytes
  (let [b (buf/from-bytes (byte-array [7 8 9]))]
    (let [result (buf/->bytes b)]
      (is (= [7 8 9] (vec result)))
      ;; position must not have advanced
      (is (= 0 (buf/position b))))))

(deftest test-->str
  (let [b (buf/from-string "hello")]
    (is (= "hello" (buf/->str b)))
    ;; idempotent
    (is (= "hello" (buf/->str b)))))

(deftest test-from-string
  (let [b (buf/from-string "abc")]
    (is (= 3 (buf/remaining b)))
    (is (buf/direct? b))
    (is (= "abc" (buf/->str b)))))

(deftest test-from-bytes
  (let [ba (byte-array [42 43 44])
        b  (buf/from-bytes ba)]
    (is (= 3 (buf/remaining b)))
    (is (buf/direct? b))
    (is (= [42 43 44] (vec (buf/->bytes b))))))

;;; ============================================================
;;; Slice / duplicate / read-only
;;; ============================================================

(deftest test-slice
  (let [b (buf/allocate 8)]
    (buf/put-bytes! b (byte-array [0 1 2 3 4 5 6 7]))
    (buf/flip! b)
    (buf/get-bytes! b 4)                  ; advance to position 4
    (let [s (buf/slice b)]               ; slice covers bytes 4-7
      (is (= 4 (buf/remaining s)))
      (is (= [4 5 6 7] (vec (buf/->bytes s)))))))

(deftest test-duplicate
  (let [b   (buf/from-bytes (byte-array [1 2 3]))
        dup (buf/duplicate b)]
    ;; independent positions
    (buf/get-byte! b)
    (is (= 0 (buf/position dup)))
    ;; shared backing data
    (is (= [1 2 3] (vec (buf/->bytes dup))))))

(deftest test-as-read-only
  (let [b  (buf/from-bytes (byte-array [1 2]))
        ro (buf/as-read-only b)]
    (is (buf/read-only? ro))
    (is (thrown? java.nio.ReadOnlyBufferException
                 (buf/put-byte! ro 0)))))

;;; ============================================================
;;; has-remaining?
;;; ============================================================

(deftest test-has-remaining
  (let [b (buf/allocate 2)]
    (buf/put-byte! b 1)
    (buf/flip! b)
    (is (buf/has-remaining? b))
    (buf/get-byte! b)
    (is (not (buf/has-remaining? b)))))
