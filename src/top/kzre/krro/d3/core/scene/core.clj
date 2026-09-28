(ns top.kzre.krro.d3.core.scene.core
  (:require [clojure.spec.alpha :as s]))

;; ── 基础类型 ──────────────────────────────
(s/def ::id keyword?)

;; 4×4 变换矩阵：长度为 16 的浮点数向量
(s/def ::transform (s/coll-of float? :kind vector? :count 16))

;; ── 组件系统（multispec） ────────────────
(s/def ::type keyword?)

(defmulti component-type ::type)
(defmethod component-type :default [_] (s/keys))   ;; 默认允许任意键

(s/def ::component
  (s/multi-spec component-type ::type))
(s/def ::components (s/coll-of ::component :kind vector?))

;; ── 网格组件 ─────────────────────────────
(s/def ::mesh-id ::id)                          ; 引用的网格 ID
(s/def ::mesh-component
  (s/keys :req-un [::type ::mesh-id]))          ; 注意：需要包含 :type 字段

;; 注册网格组件的分派
(defmethod component-type :mesh [_] ::mesh-component)

;; ── 场景物体节点 ──────────────────────────
(s/def ::parent-id (s/nilable ::id))
(s/def ::scene-object
  (s/keys :req-un [::id
                   ::parent-id
                   ::transform]
          :opt-un [::components]))


;; ── 场景容器 ───────────────────────────────
(s/def ::objects (s/coll-of ::scene-object :kind vector? :into []))
(s/def ::scene (s/keys :req-un [::objects]))
