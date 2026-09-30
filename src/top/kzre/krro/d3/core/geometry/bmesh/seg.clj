(ns top.kzre.krro.d3.core.geometry.bmesh.seg
  "段级数据访问——在 CopyOnWrite 段上应用 deflayout 宏。

   职责：把段的 base-idx（字节偏移）自动加到本地索引上——
   调用方只提供段对象 + 段内索引 + deflayout 宏——
   展开后是纯数组访问。

   ── 宏 ──
     readf    读 float 段——快照
     writef   写 float 段——getForWrite
     readi    读 int 段——快照
     writei   写 int 段——getForWrite

   ── 展开形式 ──
     (writef seg f local-idx x)
     → (f (.getFloatsForWrite seg)
          (unchecked-add (long (quot (.offset seg) Float/BYTES))
                         local-idx)
          x)

   ── 语义 ──
     段 = CopyOnWriteFloats / CopyOnWriteInts
     offset = 段在底层 storage 数组中的字节偏移
     local-idx = 段内元素索引
     最终索引 = offset/字节宽度 + local-idx

   ── 命名 ──
     动词在前——匹配 clojure.core 的 read-string / read-line
     f / i 后缀——float / int——类似 C 的 printf / scanf
     短——调用点 (seg/writef ...) 流畅"
  (:import
    [java.util List]
    (top.kzre.krro.d3.core.geometry.bmesh BMesh)
    (top.kzre.krro.d3.core.util.cow
      CopyOnWriteFloats
      CopyOnWriteInts
      CopyOnWriteObject
      ListResource)))

(set! *unchecked-math* true)

(defmacro seg-readonly
  "段访问宏"
  [cow seg-id]
  `(let [^ListResource lr# (.getSnapshot ^CopyOnWriteObject ~cow)
         ^List segs# (.getList lr#)]
     (.get segs# (int ~seg-id))))

(defmacro seg-for-write
  "段访问宏, 可写"
  [cow seg-id]
  `(let [^ListResource lr# (.getForWrite ^CopyOnWriteObject ~cow)
         ^List segs# (.getList lr#)]
     (.get segs# (int ~seg-id))))

(defmacro mesh-seg-readonly
  "读段——从 BMesh + 属性访问器 + seg-id。

   (mesh-seg-readonly mesh vertPositions seg-id)"
  [mesh accessor seg-id]
  `(let [seg# (. ^BMesh ~mesh ~accessor)]
     (seg-readonly seg# ~seg-id)))

(defmacro mesh-seg-for-write
  [mesh accessor seg-id]
  `(let [seg# (. ^BMesh ~mesh ~accessor)]
     (seg-for-write seg# ~seg-id)))


;; ═══════════════════════════════════════════════
;; float
;; ═══════════════════════════════════════════════

(defmacro readf
  "读 float 段——自动应用 offset——快照路径。

   (readf seg f local-idx & args)
   → (f (.getFloatsSnapshot seg)
        (+ (quot (.offset seg) 4) local-idx)
        args...)"
  [seg f local-idx & args]
  `(~f (.getFloatsSnapshot ^CopyOnWriteFloats ~seg)
     (unchecked-add
       (long (quot (.offset ~seg) Float/BYTES))
       (long ~local-idx))
     ~@args))

(defmacro writef
  "写 float 段——自动应用 offset——getForWrite 路径。

   (writef seg f local-idx & args)
   → (f (.getFloatsForWrite seg)
        (+ (quot (.offset seg) 4) local-idx)
        args...)"
  [seg f local-idx & args]
  `(~f (.getFloatsForWrite ^CopyOnWriteFloats ~seg)
     (unchecked-add
       (long (quot (.offset ~seg) Float/BYTES))
       (long ~local-idx))
     ~@args))

;; ═══════════════════════════════════════════════
;; int
;; ═══════════════════════════════════════════════

(defmacro readi
  "读 int 段——自动应用 offset——快照路径。

   (readi seg f local-idx & args)
   → (f (.getIntsSnapshot seg)
        (+ (quot (.offset seg) 4) local-idx)
        args...)"
  [seg f local-idx & args]
  `(~f (.getIntsSnapshot ^CopyOnWriteInts ~seg)
     (unchecked-add
       (long (quot (.offset ~seg) Integer/BYTES))
       (long ~local-idx))
     ~@args))

(defmacro writei
  "写 int 段——自动应用 offset——getForWrite 路径。

   (writei seg f local-idx & args)
   → (f (.getIntsForWrite seg)
        (+ (quot (.offset seg) 4) local-idx)
        args...)"
  [seg f local-idx & args]
  `(~f (.getIntsForWrite ^CopyOnWriteInts ~seg)
     (unchecked-add
       (long (quot (.offset ~seg) Integer/BYTES))
       (long ~local-idx))
     ~@args))

;; ═══ 顶点 ═══

(defmacro vert-position-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh vertPositions ~idx))
(defmacro vert-position-for-write [mesh idx] `(mesh-seg-for-write ~mesh vertPositions ~idx))

(defmacro vert-out-edge-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh vertOutEdges ~idx))
(defmacro vert-out-edge-for-write [mesh idx] `(mesh-seg-for-write ~mesh vertOutEdges ~idx))

;; ═══ 边 ═══

(defmacro edge-endpoint-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh edgeEndpoints ~idx))
(defmacro edge-endpoint-for-write [mesh idx] `(mesh-seg-for-write ~mesh edgeEndpoints ~idx))

(defmacro edge-loop-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh edgeLoops ~idx))
(defmacro edge-loop-for-write [mesh idx] `(mesh-seg-for-write ~mesh edgeLoops ~idx))

;; ═══ 环 ═══

(defmacro loop-uv-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh loopUvs ~idx))
(defmacro loop-uv-for-write [mesh idx] `(mesh-seg-for-write ~mesh loopUvs ~idx))

(defmacro loop-ownership-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh loopOwnership ~idx))
(defmacro loop-ownership-for-write [mesh idx] `(mesh-seg-for-write ~mesh loopOwnership ~idx))

(defmacro loop-ring-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh loopRing ~idx))
(defmacro loop-ring-for-write [mesh idx] `(mesh-seg-for-write ~mesh loopRing ~idx))

(defmacro loop-radial-ring-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh loopRadialRing ~idx))
(defmacro loop-radial-ring-for-write [mesh idx] `(mesh-seg-for-write ~mesh loopRadialRing ~idx))

;; ═══ 面 ═══

(defmacro face-normal-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh faceNormals ~idx))
(defmacro face-normal-for-write [mesh idx] `(mesh-seg-for-write ~mesh faceNormals ~idx))

(defmacro face-submesh-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh faceSubmeshIds ~idx))
(defmacro face-submesh-for-write [mesh idx] `(mesh-seg-for-write ~mesh faceSubmeshIds ~idx))

(defmacro face-topology-readonly  [mesh idx] `(mesh-seg-readonly  ~mesh faceTopology ~idx))
(defmacro face-topology-for-write [mesh idx] `(mesh-seg-for-write ~mesh faceTopology ~idx))

(set! *unchecked-math* nil)