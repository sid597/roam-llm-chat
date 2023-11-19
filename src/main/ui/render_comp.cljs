(ns ui.render-comp
  (:require [reagent.core :as r]
            [applied-science.js-interop :as j]
            [clojure.pprint :as pp :refer [pprint]]
            ["@blueprintjs/core" :as bp :refer [Button InputGroup Card]]
            [reagent.dom :as rd]))


(defn q
  ([query]
   (let [serialised-query (pr-str query)
         roam-api         (.-data (.-roamAlphaAPI js/window))
         q-fn             (.-q roam-api)]
     (-> (.apply q-fn roam-api (array serialised-query))
       (js->clj :keywordize-keys true))))
  ([query & args]
   (let [serialised-query (pr-str query)
         roam-api         (.-data (.-roamAlphaAPI js/window))
         q-fn             (.-q roam-api)]
     (-> (.apply q-fn roam-api (apply array (concat [serialised-query] args)))
       (js->clj :keywordize-keys true)))))


#_(ns ui.core
    (:require [reagent.core :as r]
              [applied-science.js-interop :as j]
              [roam.datascript.reactive :as dr]
              [roam.datascript :as d]
              [clojure.pprint :as pp :refer [pprint]]
              [blueprintjs.core :as bp :refer [Button InputGroup Card]]
              [roam.ui.main-window :as cmp]
              [reagent.dom :as rd]))

(defn log
 [& args]  (apply js/console.log args))


(defn get-child-with-str [block-uid s]
  (ffirst (q '[:find (pull ?c [:block/string :block/uid {:block/children ...}])
               :in $ ?uid ?s
               :where
               [?e :block/uid ?uid]
               [?e :block/children ?c]
               [?c :block/string ?s]]
            block-uid
            s)))

(defn chat-input []
  (let [input-ref (r/atom nil)
        chat-loaded (r/atom nil)]
    (r/create-class
      {:component-did-mount
       (fn []
         (when-let [input-el @input-ref]
           (-> (j/call-in js/window [:roamAlphaAPI :ui :components :renderBlock]
                 (clj->js {:uid "NwZQptIUd"
                           :zoom-path true
                           :el input-el}))
             (.then (fn [res]
                      (log "PROMISE RESULT" res)
                      (reset! chat-loaded res))))))
       :reagent-render
       (fn []
         [:div {:ref (fn [el] (reset! input-ref el))}])})))


(defn chat-ui [block-uid]
  (let [
        settings (get-child-with-str block-uid "Settings")
        context  (get-child-with-str block-uid "Context")
        messages (:block/children (get-child-with-str block-uid "Messages"))
        c-uid    (:block/uid context)
        chat-loaded (r/atom nil)
        ;; Define the function for handling the mounting of the component
        handle-did-mount (fn []
                           (println "component did mount")
                           (let [chat-loader-el (.querySelector js/document ".chat-loader")
                                 new-input-el   (.createElement js/document "div")
                                 hist           (.querySelector js/document ".chat-history")]
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
                                   ^{:key child}
                                   (let [uid (:block/uid child)
                                         msg-block-div (.createElement js/document "div")]
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

    (r/create-class
      {:component-did-mount handle-did-mount
       :reagent-render (fn [{:keys [block-uid]}]
                         (println "settings")
                         (pprint settings)
                         (println "muid" c-uid)
                         (pprint messages)
                         [:div.chat-container
                          {:style {:display "flex"
                                   :flex-direction "column"
                                   :height "500px"
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

                           [:div.chat-input-container
                            {:style {:display "flex"
                                     :align-items "center"
                                     :border "1px"
                                     :padding "10px"}}
                            [chat-input]
                            [:> Button {:icon "arrow-right"
                                        :intent "primary"
                                        :large true
                                        :style {:margin-left "10px"}}]]]])})))




(defn main [{:keys [:block-uid]} & args]
  (println "main args"  args)
  (let [parent-el (.getElementById js/document (str (second args)))]
    (println "parent el" (first args) parent-el block-uid)
    (.addEventListener parent-el "mousedown" (fn [e] (.stopPropagation e)))
    (rd/render [chat-ui block-uid] parent-el)))




