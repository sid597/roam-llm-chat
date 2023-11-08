(ns ui.core
  (:require [reagent.core :as r]
            [reagent.dom :as rd]))

(defn menu []
  (.addCommand js/document.roamAlphaAPI.ui.blockContextMenu
    (fn [e]
      (js/console.log "Hello from roam cljs plugin boilerplate!")
      (js/console.log e))))


(defn init []
  (js/console.log "Hello from roam cljs plugin boilerplate!")
  (rd/render [menu] (.getElementById js/document "app")))