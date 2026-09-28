(ns top.kzre.krro.d3.core.geometry.kd
  "K-Dimensional tree——空间平面切分，子空间不重叠。

   定位：空间分割 + 范围缩小。不做精确图元测试——
   只输出候选图元 id——精确测试由调用方在叶节点内完成。

   与 BVH 的区别：
   - KD 用分割平面——子空间不重叠——每节点无 AABB
   - BVH 用包围盒——子空间可重叠——每节点有 AABB
   - KD 遍历先近后远——支持早停——返回候选
   - BVH 遍历测 AABB——返回最近命中"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]])
  (:import
    (top.kzre.krro.d3.core.geometry Axis SpatialOverlay Ray Sphere)
    (top.kzre.krro.d3.core.util IntList)
    (top.kzre.krro.util.math KMath)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 声明——IDE 友好
;; ═══════════════════════════════════════════════

(declare split-pos set-split-pos!)
(declare axis-id set-axis-id!)
(declare children-left children-right)
(declare set-children-left! set-children-right!)

(declare obj-split-pos obj-set-split-pos!)
(declare obj-axis-id obj-set-axis-id!)
(declare obj-children-left obj-children-right)
(declare obj-set-children-left! obj-set-children-right!)

;; ═══════════════════════════════════════════════
;; 元数据——primitive long——无装箱
;; ═══════════════════════════════════════════════

(deftype KDMeta [^long primitive-count])

;; ═══════════════════════════════════════════════
;; 布局
;; ═══════════════════════════════════════════════

;; 每节点：
;;   split-pos  float  分割平面位置
;;   axis-id    byte   分割轴 0=X / 1=Y / 2=Z（见 Axis）
;;   left       int    左子索引；-1 表示叶
;;   right      int    右子索引；叶时为图元 id

(deflayout KD
           {:split [:float [:split [:pos]]]
            :axis  [:byte  [:axis  [:id]]]
            :child [:int   [:children [:left :right]]]}
           {:unchecked-math? true
            :ext?            true})

;; ═══════════════════════════════════════════════
;; 构造
;; ═══════════════════════════════════════════════

(defn make-kd
  "构造 KD。

   两 arity：
   - (make-kd split axis child)                  空 KD——primitive-count = 0
   - (make-kd split axis child primitive-count)  指定图元数"
  (^KD [^floats split ^bytes axis ^ints child]
   (KD. split axis child (KDMeta. 0)))
  (^KD [^floats split ^bytes axis ^ints child ^long primitive-count]
   (KD. split axis child (KDMeta. primitive-count))))

;; ═══════════════════════════════════════════════
;; 元数据访问
;; ═══════════════════════════════════════════════

(defn primitive-count
  "KD 覆盖的图元数量。0 表示空 KD。"
  ^long [^KD kd]
  (.-primitive-count ^KDMeta (.ext kd)))

;; ═══════════════════════════════════════════════
;; 节点写入——宏——调用点展开
;; ═══════════════════════════════════════════════

(defmacro write-leaf!
  "写入叶节点——left = -1，right = 图元 id。"
  [child node-idx prim-id]
  `(do (set-children-left!  ~child ~node-idx -1)
       (set-children-right! ~child ~node-idx ~prim-id)))

(defmacro write-internal!
  "写入内部节点——轴 + 分割位置 + 左右子。"
  [split axis child node-idx axis-id split-pos left-idx right-idx]
  `(do (set-split-pos! ~split ~node-idx ~split-pos)
       (set-axis-id!   ~axis  ~node-idx ~axis-id)
       (set-children-left!  ~child ~node-idx ~left-idx)
       (set-children-right! ~child ~node-idx ~right-idx)))

;; ═══════════════════════════════════════════════
;; 查询——概念操作
;; ═══════════════════════════════════════════════
;;
;; kwargs：
;;   :acc  IntList——结果累加器——复用——零分配
;;         省略时内部创建
;;
;; 复用约定：调用方传入 :acc 时——返回的 SpatialOverlay 共享
;; acc.rawArray()——下次调用 clear 后——前一结果失效。调用方
;; 必须在下次调用前消费完结果。

(def ^:private ^long STACK-CAP 256)

(defn cross-query
  "射线穿过查询——返回射线穿过的所有候选图元 id。

   kwargs：
   - :acc  IntList——复用累加器——省略时内部创建

   复用约定见命名空间文档。"
  ^SpatialOverlay [^KD kd ^Ray ray & {:keys [acc]}]
  (let [^IntList acc (or acc (IntList.))]
    (.clear acc)
    (let [n (primitive-count kd)]
      (if (zero? n)
        SpatialOverlay/EMPTY
        (let [^floats split (.split kd)
              ^bytes  axis  (.axis kd)
              ^ints   child (.child kd)
              ox (.ox ray) oy (.oy ray) oz (.oz ray)
              dx (.dx ray) dy (.dy ray) dz (.dz ray)
              ^ints   node-stk (int-array STACK-CAP)
              ^floats tmin-stk (float-array STACK-CAP)
              ^floats tmax-stk (float-array STACK-CAP)]
          (aset node-stk 0 0)
          (aset tmin-stk 0 0.0)
          (aset tmax-stk 0 Float/MAX_VALUE)
          (loop [stack-ptr (int 1)]
            (if (zero? stack-ptr)
              (SpatialOverlay. (.rawArray acc) (.size acc))
              (let [stack-ptr (int (dec stack-ptr))
                    node-idx  (int (aget node-stk stack-ptr))
                    tmin      (float (aget tmin-stk stack-ptr))
                    tmax      (float (aget tmax-stk stack-ptr))
                    left      (children-left  child node-idx)
                    right     (children-right child node-idx)]
                (if (== left -1)
                  (do (.add acc right)
                      (recur stack-ptr))
                  (let [ax-id (int (aget axis node-idx))
                        sp    (float (aget split node-idx))
                        o     (Axis/select ax-id ox oy oz)
                        d     (Axis/select ax-id dx dy dz)
                        t-spl (float
                                (if (== d 0.0)
                                  (if (< o sp) Float/MAX_VALUE Float/NEGATIVE_INFINITY)
                                  (/ (- sp o) d)))
                        near (if (>= d 0.0) left right)
                        far  (if (>= d 0.0) right left)]
                    (cond
                      (< t-spl tmin)
                      (do (aset node-stk stack-ptr far)
                          (aset tmin-stk stack-ptr tmin)
                          (aset tmax-stk stack-ptr tmax)
                          (recur (unchecked-inc stack-ptr)))

                      (> t-spl tmax)
                      (do (aset node-stk stack-ptr near)
                          (aset tmin-stk stack-ptr tmin)
                          (aset tmax-stk stack-ptr tmax)
                          (recur (unchecked-inc stack-ptr)))

                      :else
                      (do
                        (aset node-stk stack-ptr far)
                        (aset tmin-stk stack-ptr t-spl)
                        (aset tmax-stk stack-ptr tmax)
                        (aset node-stk (inc stack-ptr) near)
                        (aset tmin-stk (inc stack-ptr) tmin)
                        (aset tmax-stk (inc stack-ptr) t-spl)
                        (recur (+ stack-ptr 2))))))))))))))

(defn sphere-query
  "球体覆盖查询——返回球体覆盖的所有候选图元 id。

   kwargs：
   - :acc  IntList——复用累加器——省略时内部创建

   复用约定见命名空间文档。"
  ^SpatialOverlay [^KD kd ^Sphere sphere & {:keys [acc]}]
  (let [^IntList acc (or acc (IntList.))]
    (.clear acc)
    (let [n (primitive-count kd)]
      (if (zero? n)
        SpatialOverlay/EMPTY
        (let [^floats split (.split kd)
              ^bytes  axis  (.axis kd)
              ^ints   child (.child kd)
              cx (.cx sphere) cy (.cy sphere) cz (.cz sphere)
              radius (.radius sphere)
              ^ints stack (int-array STACK-CAP)]
          (aset stack 0 0)
          (loop [stack-ptr (int 1)]
            (if (zero? stack-ptr)
              (SpatialOverlay. (.rawArray acc) (.size acc))
              (let [stack-ptr (int (dec stack-ptr))
                    node-idx  (int (aget stack stack-ptr))
                    left      (children-left  child node-idx)
                    right     (children-right child node-idx)]
                (if (== left -1)
                  (do (.add acc right)
                      (recur stack-ptr))
                  (let [ax-id (int (aget axis node-idx))
                        sp    (float (aget split node-idx))
                        c     (Axis/select ax-id cx cy cz)
                        dist  (- c sp)
                        near  (if (< c sp) left right)
                        far   (if (< c sp) right left)]
                    (if (<= (KMath/abs dist) radius)
                      (do (aset stack stack-ptr near)
                          (aset stack (inc stack-ptr) far)
                          (recur (+ stack-ptr 2)))
                      (do (aset stack stack-ptr near)
                          (recur (unchecked-int stack-ptr))))))))))))))

(set! *unchecked-math* nil)