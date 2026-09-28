(ns top.kzre.krro.d3.core.geometry.aabb
  "AABB 数组布局——每图元 6 float：minX minY minZ maxX maxY maxZ。

   为构建器提供统一的 AABB 数组访问：
   - kd-build
   - binned-bvh
   - sbvh

   与 Java 值类的关系：
   - Java AABB / IAABB   单个 AABB 对象——API 边界
   - AabbArray           AABB 的 SoA 数组——构建器内部

   布局由 deflayout 管理——逻辑代码不硬编码下标。"
  (:require
    [top.kzre.deflayout.core :refer [deflayout]])
  (:import
    (top.kzre.krro.d3.core.geometry AABB IAABB)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 声明——IDE 友好
;; ═══════════════════════════════════════════════

(declare aabb-min-x aabb-min-y aabb-min-z)
(declare aabb-max-x aabb-max-y aabb-max-z)
(declare set-aabb-min-x! set-aabb-min-y! set-aabb-min-z!)
(declare set-aabb-max-x! set-aabb-max-y! set-aabb-max-z!)

(declare obj-aabb-min-x obj-aabb-min-y obj-aabb-min-z)
(declare obj-aabb-max-x obj-aabb-max-y obj-aabb-max-z)
(declare obj-set-aabb-min-x! obj-set-aabb-min-y! obj-set-aabb-min-z!)
(declare obj-set-aabb-max-x! obj-set-aabb-max-y! obj-set-aabb-max-z!)

;; ═══════════════════════════════════════════════
;; 布局
;; ═══════════════════════════════════════════════
;;
;; 组名 :data —— deftype 的数组字段
;; 字段名 :aabb —— 组内字段——含 6 个子分量
;;
;; 访问器（数组版本）：
;;   (aabb-min-x arr i)   → (aget ^"[F" arr (+ (* i 6) 0))
;;   (aabb-max-z arr i)   → (aget ^"[F" arr (+ (* i 6) 5))

(deflayout AabbArray
           {:data [:float [:aabb [:min-x :min-y :min-z :max-x :max-y :max-z]]]}
           {:unchecked-math? true})

;; ═══════════════════════════════════════════════
;; 数组分配
;; ═══════════════════════════════════════════════

(defn allocate-aabb-array
  "分配容纳 n 个 AABB 的 float 数组。
   每 AABB 6 float——布局见 deflayout。"
  ^floats [^long n]
  (float-array (* n 6)))

;; ═══════════════════════════════════════════════
;; 单 AABB 读写——桥接 Java IAABB 与数组
;; ═══════════════════════════════════════════════

(defn write-from!
  "从 IAABB 写入数组的 idx 槽。返回 arr——链式调用友好。"
  ^floats [^floats arr ^long idx ^IAABB aabb]
  (set-aabb-min-x! arr idx (.getMinX aabb))
  (set-aabb-min-y! arr idx (.getMinY aabb))
  (set-aabb-min-z! arr idx (.getMinZ aabb))
  (set-aabb-max-x! arr idx (.getMaxX aabb))
  (set-aabb-max-y! arr idx (.getMaxY aabb))
  (set-aabb-max-z! arr idx (.getMaxZ aabb))
  arr)

;; ═══════════════════════════════════════════════
;; 子范围操作——构建器复用
;; ═══════════════════════════════════════════════

(defn range-aabb
  "计算 prims 子范围的联合 AABB。

   两 arity：
   - (range-aabb arr prims out)   写入 out——复用——零分配
   - (range-aabb arr prims)       内建 AABB——便捷

   内部用 double 累加——Clojure loop 的 primitive 循环变量
   只有 long / double——避免 float 每轮来回提升。
   出口窄化为 float 写进 AABB。"

  (^AABB [^floats arr prims ^AABB out]
   (loop [i   0
          mnx Double/POSITIVE_INFINITY
          mny Double/POSITIVE_INFINITY
          mnz Double/POSITIVE_INFINITY
          mxx Double/NEGATIVE_INFINITY
          mxy Double/NEGATIVE_INFINITY
          mxz Double/NEGATIVE_INFINITY]
     (if (< i (count prims))
       (let [p   (long (nth prims i))
             px0 (double (aabb-min-x arr p))
             py0 (double (aabb-min-y arr p))
             pz0 (double (aabb-min-z arr p))
             px1 (double (aabb-max-x arr p))
             py1 (double (aabb-max-y arr p))
             pz1 (double (aabb-max-z arr p))]
         (recur (unchecked-inc i)
                (if (< px0 mnx) px0 mnx)
                (if (< py0 mny) py0 mny)
                (if (< pz0 mnz) pz0 mnz)
                (if (> px1 mxx) px1 mxx)
                (if (> py1 mxy) py1 mxy)
                (if (> pz1 mxz) pz1 mxz)))
       (do (.set out (float mnx) (float mny) (float mnz)
                 (float mxx) (float mxy) (float mxz))
           out))))

  (^AABB [^floats arr prims]
   (range-aabb arr prims (AABB.))))


(defn range-aabb-range
  "对 prims[start, end) 子范围计算联合 AABB。
   返回新建 AABB——调用方负责生命周期。

   4 参数——`IFn$OOLL` 接口——start / end 保持 primitive long。"
  ^AABB [^floats arr ^longs prims ^long start ^long end]
  (loop [i   start
         mnx Double/POSITIVE_INFINITY
         mny Double/POSITIVE_INFINITY
         mnz Double/POSITIVE_INFINITY
         mxx Double/NEGATIVE_INFINITY
         mxy Double/NEGATIVE_INFINITY
         mxz Double/NEGATIVE_INFINITY]
    (if (< i end)
      (let [p   (aget prims i)
            px0 (double (aabb-min-x arr p))
            py0 (double (aabb-min-y arr p))
            pz0 (double (aabb-min-z arr p))
            px1 (double (aabb-max-x arr p))
            py1 (double (aabb-max-y arr p))
            pz1 (double (aabb-max-z arr p))]
        (recur (unchecked-inc i)
               (if (< px0 mnx) px0 mnx)
               (if (< py0 mny) py0 mny)
               (if (< pz0 mnz) pz0 mnz)
               (if (> px1 mxx) px1 mxx)
               (if (> py1 mxy) py1 mxy)
               (if (> pz1 mxz) pz1 mxz)))
      (AABB. (float mnx) (float mny) (float mnz)
             (float mxx) (float mxy) (float mxz)))))


(defn center-on-axis
  "图元 prim-idx 在 axis-idx 上的中心坐标——double。

   axis-idx：0 = X，1 = Y，2 = Z。"
  ^double [^floats arr ^long prim-idx ^long axis-idx]
  (let [lo (case axis-idx
             0 (double (aabb-min-x arr prim-idx))
             1 (double (aabb-min-y arr prim-idx))
             2 (double (aabb-min-z arr prim-idx)))
        hi (case axis-idx
             0 (double (aabb-max-x arr prim-idx))
             1 (double (aabb-max-y arr prim-idx))
             2 (double (aabb-max-z arr prim-idx)))]
    (* 0.5 (+ lo hi))))

(defn longest-axis
  "从 AABB 的三个跨度选出最长轴——0 = X，1 = Y，2 = Z。
   接收 IAABB——避免裸 6 参数——突破 primitive 签名 4 参数限制。"
  ^long [^IAABB aabb]
  (let [ex (- (.getMaxX aabb) (.getMinX aabb))
        ey (- (.getMaxY aabb) (.getMinY aabb))
        ez (- (.getMaxZ aabb) (.getMinZ aabb))]
    (cond
      (and (>= ex ey) (>= ex ez)) 0
      (>= ey ez)                  1
      :else                       2)))

(set! *unchecked-math* nil)