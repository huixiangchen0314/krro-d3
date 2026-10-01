(ns top.kzre.krro.d3.core.geometry.bmesh.nav.analyze
  "BMesh 邻接分析。

   绕顶点 v 的扇分组——判断顶点单扇性（流形）。

   扇 = 绕 v 通过面相邻的边组成的连通分量。

   ── 输出 ──
     两个 IntList —— 用户传入 —— 复用 —— 稳态零分配：
       out-edges    所有边按扇连续排列
       out-offsets  扇边界 —— 长度 = 扇数 + 1

     扇 i 的边 = out-edges[out-offsets[i] .. out-offsets[i+1] - 1]。

   ── workspace ──
     local   IntIntMap     —— 边索引 → local idx
     uf      UnionFind     —— 并查集
     ws      FanWorkspace  —— deg(v) 级临时数组

   全部用户传入 —— 稳态零分配。

   O(deg(v) · α(deg(v)))。n = deg(v)。

   ── 只读 ──
     不修改 BMesh。"
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step   :as step]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.rotate :as rotate]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.walk   :as walk])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)
    (top.kzre.krro.d3.core.geometry.bmesh.nav FanWorkspace)
    (top.kzre.krro.d3.core.util IntList IntIntMap UnionFind)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 扇分组
;; ═══════════════════════════════════════════════

(defn vert-fan-groups-into
  "绕顶点 v 的出边——按扇分组——写到用户 buffer。

   参数：
     editor       BMeshEditor
     v            顶点
     local        IntIntMap    —— 边索引 → local idx —— 用户复用
     uf           UnionFind    —— 并查集 —— 用户复用
     ws           FanWorkspace —— deg(v) 级临时数组 —— 用户复用
     out-edges    IntList —— 所有边按扇连续排列
     out-offsets  IntList —— 扇边界 —— 长度 = 扇数 + 1

   扇 i 的边 = out-edges[out-offsets[i] .. out-offsets[i+1] - 1]。

   返回扇数。

   稳态零分配。

   O(deg(v) · α(deg(v)))。"
  [^BMeshEditor editor v
         ^IntIntMap local ^UnionFind uf ^FanWorkspace ws
         ^IntList out-edges ^IntList out-offsets]
  (.clear out-edges)
  (.clear out-offsets)
  (.reset local)
  (let [v (long v)
        ^ints edges (walk/edge-around-vertex editor v)
        n           (alength edges)]
    (if (== n 0)
      0
      (do
        (.reset uf n)
        (.ensureCapacity ws n)
        (.resetRootToFan ws n)

        (let [^ints rtf (.rootToFan ws)]

          ;; 1. local 映射
          (dotimes [i n]
            (.put local (aget edges i) i))

          ;; 2. 并查集合并——每个面贡献一条邻接
          (dotimes [i n]
            (let [e (aget edges i)
                  l (long (rotate/loop-at-edge-vert editor e v))]
              (when (not= l -1)
                (let [e-prev (int (step/loop-edge editor
                                                  (step/loop-prev editor l)))
                      j      (.get local e-prev)]
                  (.union uf i j)))))

          ;; 3. root → 连续 fan id
          (let [fans (loop [i 0 fans 0]
                       (if (>= i n)
                         fans
                         (let [r (.find uf i)]
                           (if (>= (aget rtf r) 0)
                             (recur (inc i) fans)
                             (do
                               (aset rtf r fans)
                               (recur (inc i) (inc fans)))))))

                ^ints sizes   (.sizes ws)
                ^ints offsets (.offsets ws)
                ^ints result  (.result ws)
                ^ints cursor  (.cursor ws)]

            ;; 4. 每扇大小
            (dotimes [f fans]
              (aset sizes f 0))
            (dotimes [i n]
              (let [f (aget rtf (.find uf i))]
                (aset sizes f (inc (aget sizes f)))))

            ;; 5. offsets 前缀和
            (aset offsets 0 0)
            (dotimes [f fans]
              (aset offsets (inc f)
                    (+ (aget offsets f) (aget sizes f))))
            (dotimes [f (inc fans)]
              (.add out-offsets (aget offsets f)))

            ;; 6. 按扇填充 result
            (dotimes [f fans]
              (aset cursor f (aget offsets f)))
            (dotimes [i n]
              (let [f   (aget rtf (.find uf i))
                    pos (aget cursor f)]
                (aset result pos (aget edges i))
                (aset cursor f (inc pos))))
            (dotimes [i n]
              (.add out-edges (aget result i)))

            (long fans)))))))

;; ═══════════════════════════════════════════════
;; 谓词
;; ═══════════════════════════════════════════════

(defn manifold-vert?
  "顶点 v 单扇——流形。

   内部分配四个 buffer —— 便利封装。
   高频调用 —— 用 vert-fan-groups-into 复用 buffer。"
  [^BMeshEditor editor ^long v]
  (let [local (IntIntMap. 8)
        uf    (UnionFind. 8)
        ws    (FanWorkspace. 8)
        e     (IntList.)
        o     (IntList.)]
    (== 1 (vert-fan-groups-into editor v local uf ws e o))))

(set! *unchecked-math* nil)