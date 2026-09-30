(ns top.kzre.krro.d3.core.geometry.bmesh.nav.query
  "导航——查询。

   基于 step / rotate —— 判断 / 查找 / 度量。
   全部只读——无副作用。

   ── 命名约定 ──
     带 -in-fan 后缀 —— 绕顶点遍历——只在当前扇内可达。
     无后缀          —— 不涉绕顶点遍历——无扇假设。

   ── 谓词 ──
     isolated-vert?
     isolated-edge?
     isolated-face?
     boundary-edge?           径向链 == 1
     manifold-edge?           径向链 == 2
     boundary-vert-in-fan?    当前扇有边界边

   ── 度量 ──
     vert-degree-in-fan       当前扇出边数
     face-degree
     edge-radial-count

   ── 查找 ──
     find-edge-in-fan         当前扇查找 v0-v1 边

   ── 零分配 ──
     谓词 / 度量 —— 计数 / 早停。
     查找 —— 遍历 + 比较。

  ── 扇内语义 ──
  绕顶点遍历依赖旋转——旋转只在当前扇内可达。
  多扇顶点（两个独立顶点拓扑共用同一索引）——
  vert-degree / vert-loop-count / boundary-vert? ——
  只反映当前扇。

  已知数据流形时——准确。
  非流形——上层若有完整上下文（如导入时全扫一遍）——
  自行判断——不依赖这些函数。
   "
  (:require
    [top.kzre.krro.d3.core.geometry.bmesh.nav.step   :as step]
    [top.kzre.krro.d3.core.geometry.bmesh.nav.rotate :as rotate])
  (:import
    (top.kzre.krro.d3.core.geometry.bmesh BMeshEditor)))

(set! *unchecked-math* true)

;; ═══════════════════════════════════════════════
;; 内部——绕顶点旋转边
;; ═══════════════════════════════════════════════

(defn- next-edge-around-vert ^long
  [^BMeshEditor editor ^long v ^long e]
  (let [l (long (rotate/loop-at-edge-vert editor e v))]
    (if (== l -1)
      -1
      (let [nl (long (rotate/next-loop-around-vert editor l))]
        (if (== nl -1) -1 (step/loop-edge editor nl))))))

;; ═══════════════════════════════════════════════
;; 谓词——无扇假设
;; ═══════════════════════════════════════════════

(defn isolated-vert?
  "顶点 v 无出边。"
  [^BMeshEditor editor ^long v]
  (== -1 (long (step/vert-out-edge editor v))))

(defn isolated-edge?
  "边 e 无径向环。"
  [^BMeshEditor editor ^long e]
  (== -1 (long (step/edge-loop editor e))))

(defn isolated-face?
  "面 f 无环入口。"
  [^BMeshEditor editor ^long f]
  (== -1 (long (step/face-loop editor f))))

(defn boundary-edge?
  "边 e 是边界边——径向链长度 1。"
  [^BMeshEditor editor ^long e]
  (== 1 (edge-radial-count editor e)))

(defn manifold-edge?
  "边 e 是流形边——径向链长度 2。"
  [^BMeshEditor editor ^long e]
  (== 2 (edge-radial-count editor e)))

;; ═══════════════════════════════════════════════
;; 度量——无扇假设
;; ═══════════════════════════════════════════════

(defn face-degree
  "面 f 的环长。"
  ^long [^BMeshEditor editor ^long f]
  (step/face-len editor f))

(defn edge-radial-count
  "边 e 的径向链长度——0 / 1 / 2 / N。"
  ^long [^BMeshEditor editor ^long e]
  (let [start (long (step/edge-loop editor e))]
    (if (== start -1)
      0
      (loop [cur start
             n   1]
        (let [nxt (long (step/loop-radial-next editor cur))]
          (if (or (== nxt -1) (== nxt start))
            n
            (recur nxt (inc n))))))))

;; ═══════════════════════════════════════════════
;; 度量——扇内
;; ═══════════════════════════════════════════════

(defn vert-degree-in-fan
  "顶点 v 的当前扇出边数。

   多扇顶点——只计当前扇——非全度数。"
  ^long [^BMeshEditor editor ^long v]
  (let [start (long (step/vert-out-edge editor v))]
    (if (== start -1)
      0
      (loop [e start
             n 1]
        (let [nxt (long (next-edge-around-vert editor v e))]
          (if (or (== nxt -1) (== nxt start))
            n
            (recur nxt (inc n))))))))

;; ═══════════════════════════════════════════════
;; 谓词——扇内
;; ═══════════════════════════════════════════════

(defn boundary-vert-in-fan?
  "顶点 v 的当前扇有边界边。

   多扇顶点——只检查当前扇——其他扇有边界边——可能漏判。"
  [^BMeshEditor editor ^long v]
  (let [start (long (step/vert-out-edge editor v))]
    (if (== start -1)
      false
      (loop [e start]
        (if (boundary-edge? editor e)
          true
          (let [nxt (long (next-edge-around-vert editor v e))]
            (if (or (== nxt -1) (== nxt start))
              false
              (recur nxt))))))))

;; ═══════════════════════════════════════════════
;; 查找——扇内
;; ═══════════════════════════════════════════════

(defn find-edge-in-fan
  "在 v0 的当前扇内查找连接 v0 v1 的边——无返回 -1。

   多扇顶点——只覆盖当前扇。"
  ^long [^BMeshEditor editor ^long v0 ^long v1]
  (let [start (long (step/vert-out-edge editor v0))]
    (if (== start -1)
      -1
      (loop [e start]
        (if (== (long (step/edge-other-vert editor e v0)) v1)
          e
          (let [nxt (long (next-edge-around-vert editor v0 e))]
            (if (or (== nxt -1) (== nxt start))
              -1
              (recur nxt))))))))

(set! *unchecked-math* nil)