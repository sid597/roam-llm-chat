(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [reagent.dom :as rd]))

(defn log
  [& args]
  (apply js/console.log args))


(defn add-new-option-to-context-menu []
  (let [block-context-menus (j/get-in js/window [:roamAlphaAPI :ui :blockContextMenu])]
    (log "block context menu" block-context-menus)
    (.addCommand block-context-menus
      (clj->js {:label "Chat LLM: Hello from ClojureScript"
                :display-conditional (fn [e]
                                       #_(log "display conditional" e)
                                       true)
                :callback (fn [e]
                            (log "chat llm callback" e))}))))


(defn init []
  (js/console.log "Hello from roam cljs plugin boilerplate!")
  (add-new-option-to-context-menu))