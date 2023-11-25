(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [ui.render-comp :as rc]
            [reagent.dom :as rd]))



(defn log
  [& args]  (apply js/console.log args))


(defn add-new-option-to-context-menu []
    (j/call-in js/window [:roamAlphaAPI :ui :blockContextMenu :addCommand]
      ;; Returns
      #_{:block-uid "8CskYJbhx"
         :page-uid "11-08-2023"
         :window-id "m0n15kMpYIaPLcMEchKcuWKdLAK2-body-outline-11-08-2023"
         :read-only? false
         :block-string ""
         :heading nil}
     (clj->js {:label "Chat LLM: Hello from ClojureScript"
               :display-conditional (fn [e]
                                      true)
               :callback (fn [e]
                           (let [block-uid (j/get e :block-uid)
                                 dom-id (str "block-input-" (j/get e :window-id) "-" block-uid)]
                             (println "cui" (str "{{roam/render: ((C_s8CL875)) \"C_s8CL875\" " dom-id " " "}}"))
                             (rc/main {:block-uid block-uid} "filler"  dom-id)
                             #_(j/call-in js/window [:roamAlphaAPI :data  :block :update]
                                 (clj->js {:block
                                           {:uid block-uid
                                            :string (str "{{roam/render: ((C_s8CL875)) \"C_s8CL875\" " dom-id " " "}}")}}))))})))


(defn init [config-json]
  (let [config (js->clj config-json :keywordize-keys true)]
    (log "config" config))
  (js/console.log "Hello from roam cljs plugin boilerplate!")
  (add-new-option-to-context-menu))



