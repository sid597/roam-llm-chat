(ns ui.utils
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Tooltip HTMLSelect Button ButtonGroup Card Slider Divider Menu MenuItem Popover MenuDivider]]
            [cljs-http.client :as http]
            [cljs.core.async :as async :refer [<! >! go chan put! take! timeout]]
            [ui.components :as comp :refer [send-message-component]]
            [cljs.core.async.interop :as asy :refer [<p!]]
            [ui.extract-data :as ed :refer [data-for-pages q]]
            [reagent.dom :as rd]))


(defn sm [a b]
  (+ a b))


(sm 1 3)

(defn extract-struct [struct start]
  (let [stack (atom [])
        res   (atom [])
        next (atom nil)]
    (swap! stack conj (j/get struct :string start))
    stack))


#_(extract-struct
    {:uid "zyd"
     :string "a"
     :children [{:uid "ss"
                 :string "ab"
                 :children [{:uid "zyd"
                             :string "abc"
                             :children []}]}
                {:uid "ss"
                 :string "abd"
                 :children []}]
     :callback ()}
    "a")
