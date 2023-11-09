(ns ui.core
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [reagent.dom :as rd]))



(defn log
  [& args]  (apply js/console.log args))

;; Code for roam render

#_(ns ui.core
    (:require [reagent.core :as r]
              [applied-science.js-interop :as j]
              [roam.datascript.reactive :as dr]
              [roam.datascript :as d]
              [clojure.pprint :as pp :refer [pprint]]
              [blueprintjs.core :as bp :refer [Button InputGroup Card]]
      [roam.ui.main-window :as cmp

              [reagent.dom :as rd]]))


#_(defn get-child-with-str [block-uid s]
    (ffirst (d/q '[:find (pull ?c [:block/string :block/uid {:block/children ...}])
                   :in $ ?uid ?s
                   :where
                   [?e :block/uid ?uid]
                   [?e :block/children ?c]
                   [?c :block/string ?s]]
              block-uid
              s)))

#_(defn chat-ui [{:keys [block-uid]}]
    (println "chat ui input" block-uid)
    (let [settings (get-child-with-str block-uid "Settings")
          context  (get-child-with-str block-uid "Context")
          messages (:block/children (get-child-with-str block-uid "Messages"))
          c-uid    (:block/uid context)
          chat-loaded (r/atom nil)
          ;; Define the function for handling the mounting of the component
          handle-did-mount (fn []
                             (println "component did mount")
                             (let [chat-loader-el (.querySelector js/document ".chat-loader")
                                   new-input-el         (.createElement js/document "chat-block-div")
                                   hist       (.querySelector js/document ".chat-history")
                                   new-hist-el  (.createElement js/document "chat-history-div")]
                               (println "CHAT LOADED")
                               (when chat-loader-el
                                 (.appendChild chat-loader-el new-input-el (.-firstChild chat-loader-el))
                                (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                      (clj->js {:uid "NwZQptIUd"
                                                :zoom-path true
                                                :el new-input-el}))
                                  (.then (fn [res]
                                           (log "PROMISE RESULT" res)
                                           (reset! chat-loaded res)))))
                               (when hist
                                 (println "CHAT HISTORY" (count messages))
                                 (doall
                                   (for [child messages]
                                     (let [uid (:block/uid child)
                                           msg-block-div (.createElement js/document "chat-history-div")]
                                       (println "child" child)
                                       (do
                                         (if (.hasChildNodes hist)
                                           (.insertBefore hist msg-block-div (.-firstChild hist))
                                           (.appendChild hist msg-block-div (.-firstChild hist)))
                                         (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                                               (clj->js {:uid uid
                                                         :zoom-path true
                                                         :el msg-block-div}))
                                           (.then (fn [res]
                                                    (log "PROMISE RESULT" res)
                                                    (reset! chat-loaded res)))))))))))]

      ;; Define the Reagent component function
      (r/create-class
        {:component-did-mount handle-did-mount
         :reagent-render      (fn [{:keys [block-uid]}]
                               (println "settings")
                               (pprint settings)
                               (println "muid" c-uid)
                               (pprint messages)
                               [:div.chat-container
                                {:style {:display "flex"
                                         :flex-direction "column"
                                         :height "500px" ;; Adjust the height as needed
                                         :border-radius "8px"
                                         :overflow "hidden"}}

                                [:> Card {:interactive true
                                          :elevation 3
                                          :style {:flex "1"
                                                  :margin "0"
                                                  :display "flex"
                                                  :flex-direction "column"
                                                  :border "2px solid rgba(0, 0, 0, 0.2)"
                                                  :border-radius "8px"}}

                                 [:div.chat-history
                                  {:style {:flex "1"
                                           :overflow-y "auto"
                                           :margin "10px"
                                           :background "aliceblue"}}]
                                 ;; Content of chat history goes here

                                 [:div.chat-input-container
                                  {:style {:display "flex"
                                           :align-items "center"
                                           :border "1px"
                                           :padding "10px"}
                                   :component-did-mount handle-did-mount}
                                  [:div.chat-loader
                                   {:fill true
                                    :large true
                                    :placeholder "Type a message..."
                                    :style {:flex "1" ;; Ensures input takes available space
                                            :margin-right "10px"}}

                                   @chat-loaded]
                                  [:> Button {:icon "arrow-right"
                                              :intent "primary"
                                              :large true
                                              :style {:margin-left "10px"}}]]]])})))



(defn add-new-option-to-context-menu []
  (let [block-context-menus (j/get-in js/window [:roamAlphaAPI :ui :blockContextMenu])]
    (log "block context menu" block-context-menus)
    (.addCommand block-context-menus
      (clj->js {:label "Chat LLM: Hello from ClojureScript"
                :display-conditional (fn [e]
                                       #_(log "display conditional" e)
                                       true)
                :callback (fn [e]
                            (let [dom-id (str "block-input-" (j/get e :window-id) "-" (j/get e :block-uid))
                                  parent-el (.getElementById js/document dom-id)
                                  block-uid (j/get-in e [:block-uid])
                                  new-container (.createElement js/document "div")]
                               (log "chat llm callback" dom-id)
                               (log "parent el" parent-el)
                               (log "new container" new-container)
                               (.appendChild parent-el new-container)
                               (log "block uid" block-uid)
                               ;; Update block string
                               (j/call-in js/window [:roamAlphaAPI :data  :block :update]
                                 (clj->js {:block
                                           {:uid block-uid
                                            :string "{{roam/render: ((C_s8CL875)) \"C_s8CL875\"}}"}}))))}))))


;; Returns
#_{:block-uid "8CskYJbhx"
   :page-uid "11-08-2023"
   :window-id "m0n15kMpYIaPLcMEchKcuWKdLAK2-body-outline-11-08-2023"
   :read-only? false
   :block-string ""
   :heading nil}



(defn init []
  (js/console.log "Hello from roam cljs plugin boilerplate!")
  (add-new-option-to-context-menu))