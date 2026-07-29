(ns top.kzre.krro.d3.core.scene.core
  (:require [clojure.spec.alpha :as s]))

(s/def ::id keyword?)

(s/def ::material (s/keys :req-un [::id]))

;; 外键id
(s/def ::material-id ::id)

(s/def ::mesh any?)

;; 变换矩阵
(s/def ::transform any?)
(s/def ::object (s/keys :req-un [::id ::transform]))


(s/def ::scene (s/keys :req-un [::object]))